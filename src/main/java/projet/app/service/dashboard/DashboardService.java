package projet.app.service.dashboard;

import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import projet.app.dto.DashboardCreateRequestDTO;
import projet.app.dto.DashboardRowResponseDTO;
import projet.app.entity.dashboard.DashboardEntry;
import projet.app.entity.mapping.CategorieRatios;
import projet.app.entity.mapping.FamilleRatios;
import projet.app.entity.mapping.RatiosConfig;
import projet.app.ratio.formula.ExpressionNode;
import projet.app.repository.dashboard.DashboardRepository;
import projet.app.repository.mapping.CategorieRatiosRepository;
import projet.app.repository.mapping.FamilleRatiosRepository;
import projet.app.repository.mapping.RatiosConfigRepository;
import projet.app.service.ratio.FormulaEvaluationService;
import projet.app.service.ratio.RatioFormulaExecutionResult;

import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class DashboardService {

    private final DashboardRepository dashboardRepository;
    private final RatiosConfigRepository ratiosConfigRepository;
    private final FamilleRatiosRepository familleRatiosRepository;
    private final CategorieRatiosRepository categorieRatiosRepository;
    private final FormulaEvaluationService formulaEvaluationService;
    private final JdbcTemplate jdbcTemplate;

    public DashboardService(
            DashboardRepository dashboardRepository,
            RatiosConfigRepository ratiosConfigRepository,
            FamilleRatiosRepository familleRatiosRepository,
            CategorieRatiosRepository categorieRatiosRepository,
            FormulaEvaluationService formulaEvaluationService,
            JdbcTemplate jdbcTemplate
    ) {
        this.dashboardRepository = dashboardRepository;
        this.ratiosConfigRepository = ratiosConfigRepository;
        this.familleRatiosRepository = familleRatiosRepository;
        this.categorieRatiosRepository = categorieRatiosRepository;
        this.formulaEvaluationService = formulaEvaluationService;
        this.jdbcTemplate = jdbcTemplate;
    }

        @Transactional
        public DashboardRowResponseDTO create(DashboardCreateRequestDTO request) {
                Long ratioId = request.getIdRatios();
                LocalDate referenceDate = request.getDate();

                if (!ratiosConfigRepository.existsById(ratioId)) {
                        throw new IllegalArgumentException("Ratios config does not exist for id: " + ratioId);
                }

                if (dashboardRepository.existsByIdRatiosAndReferenceDate(ratioId, referenceDate)) {
                        throw new IllegalArgumentException(
                                        "Dashboard row already exists for ratio id " + ratioId + " and date " + referenceDate
                        );
                }

                DashboardEntry saved = dashboardRepository.save(DashboardEntry.builder()
                                .idRatios(ratioId)
                                .ratiosValue(request.getValue())
                                .referenceDate(referenceDate)
                                .build());

                return toResponse(List.of(saved)).get(0);
        }

    @Transactional
    public void initializeForNewRatio(RatiosConfig ratio, ExpressionNode expressionNode) {
        if (ratio == null || ratio.getId() == null) {
            throw new IllegalArgumentException("Cannot initialize dashboard rows: ratio id is missing");
        }

        List<LocalDate> referenceDates = getReferenceDatesFromFactBalance();
        if (referenceDates.isEmpty()) {
            return;
        }

        List<DashboardEntry> entries = new ArrayList<>(referenceDates.size());
        for (LocalDate referenceDate : referenceDates) {
            RatioFormulaExecutionResult evaluation = formulaEvaluationService.evaluateAtDate(expressionNode, referenceDate);
            entries.add(DashboardEntry.builder()
                    .idRatios(ratio.getId())
                    .ratiosValue(evaluation.value())
                    .referenceDate(referenceDate)
                    .build());
        }

        dashboardRepository.saveAll(entries);
    }

    @Transactional(readOnly = true)
    public List<DashboardRowResponseDTO> list(LocalDate referenceDate) {
        List<DashboardEntry> entries = referenceDate == null
                ? dashboardRepository.findAll(Sort.by(
                        Sort.Order.asc("referenceDate"),
                        Sort.Order.asc("idRatios"),
                        Sort.Order.asc("id")
                ))
                : dashboardRepository.findByReferenceDateOrderByIdAsc(referenceDate);

        return toResponse(entries);
    }

    private List<DashboardRowResponseDTO> toResponse(List<DashboardEntry> entries) {
        if (entries.isEmpty()) {
            return List.of();
        }

        Set<Long> ratioIds = entries.stream()
                .map(DashboardEntry::getIdRatios)
                .collect(Collectors.toSet());

        Map<Long, RatiosConfig> ratioById = ratiosConfigRepository.findAllById(ratioIds).stream()
                .collect(Collectors.toMap(RatiosConfig::getId, Function.identity()));

        Set<Long> familleIds = ratioById.values().stream()
                .map(RatiosConfig::getFamilleId)
                .collect(Collectors.toSet());

        Set<Long> categorieIds = ratioById.values().stream()
                .map(RatiosConfig::getCategorieId)
                .collect(Collectors.toSet());

        Map<Long, String> familleCodeById = familleRatiosRepository.findAllById(familleIds).stream()
                .collect(Collectors.toMap(FamilleRatios::getId, FamilleRatios::getName));

        Map<Long, String> categorieCodeById = categorieRatiosRepository.findAllById(categorieIds).stream()
                .collect(Collectors.toMap(CategorieRatios::getId, CategorieRatios::getName));

        return entries.stream()
                .map(entry -> {
                    RatiosConfig ratio = ratioById.get(entry.getIdRatios());
                    Long familleId = ratio == null ? null : ratio.getFamilleId();
                    Long categorieId = ratio == null ? null : ratio.getCategorieId();

                    return DashboardRowResponseDTO.builder()
                            .id(entry.getId())
                            .idRatios(entry.getIdRatios())
                            .code(ratio == null ? null : ratio.getCode())
                            .label(ratio == null ? null : ratio.getLabel())
                            .description(ratio == null ? null : ratio.getDescription())
                            .familleId(familleId)
                            .categorieId(categorieId)
                            .familleCode(familleId == null ? null : familleCodeById.get(familleId))
                            .categorieCode(categorieId == null ? null : categorieCodeById.get(categorieId))
                            .seuilTolerance(ratio == null ? null : ratio.getSeuilTolerance())
                            .seuilAlerte(ratio == null ? null : ratio.getSeuilAlerte())
                            .seuilAppetence(ratio == null ? null : ratio.getSeuilAppetence())
                            .value(entry.getRatiosValue())
                            .date(entry.getReferenceDate())
                            .build();
                })
                .toList();
    }

    private List<LocalDate> getReferenceDatesFromFactBalance() {
        String sql = """
                SELECT DISTINCT d.date_value AS reference_date
                FROM datamart.fact_balance fb
                JOIN datamart.sub_dim_date d ON d.id = fb.id_date
                WHERE d.date_value IS NOT NULL
                ORDER BY d.date_value
                """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            Date value = rs.getDate("reference_date");
            return value == null ? null : value.toLocalDate();
        }).stream().filter(date -> date != null).toList();
    }
}