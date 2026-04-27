package projet.app.service.dashboard;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import projet.app.dto.DashboardCreateRequestDTO;
import projet.app.dto.DashboardGroupedByRatioResponseDTO;
import projet.app.dto.DashboardRowResponseDTO;
import projet.app.dto.DashboardSimulateAllResponseDTO;
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
import projet.app.service.ratio.RatioFormulaMapper;

import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class DashboardService {

    private final DashboardRepository dashboardRepository;
    private final RatiosConfigRepository ratiosConfigRepository;
    private final FamilleRatiosRepository familleRatiosRepository;
    private final CategorieRatiosRepository categorieRatiosRepository;
    private final FormulaEvaluationService formulaEvaluationService;
    private final RatioFormulaMapper ratioFormulaMapper;
    private final JdbcTemplate jdbcTemplate;

    public DashboardService(
            DashboardRepository dashboardRepository,
            RatiosConfigRepository ratiosConfigRepository,
            FamilleRatiosRepository familleRatiosRepository,
            CategorieRatiosRepository categorieRatiosRepository,
            FormulaEvaluationService formulaEvaluationService,
            RatioFormulaMapper ratioFormulaMapper,
            JdbcTemplate jdbcTemplate
    ) {
        this.dashboardRepository = dashboardRepository;
        this.ratiosConfigRepository = ratiosConfigRepository;
        this.familleRatiosRepository = familleRatiosRepository;
        this.categorieRatiosRepository = categorieRatiosRepository;
        this.formulaEvaluationService = formulaEvaluationService;
        this.ratioFormulaMapper = ratioFormulaMapper;
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

    /**
     * All dashboard rows grouped by ratio {@link RatiosConfig#getCode()}, with date → value per ratio.
     * Ratio codes are ordered alphabetically; dates within each ratio are chronological.
     */
    @Transactional(readOnly = true)
    public DashboardGroupedByRatioResponseDTO listGroupedByRatio() {
        List<DashboardEntry> entries = dashboardRepository.findAll(Sort.by(
                Sort.Order.asc("idRatios"),
                Sort.Order.asc("referenceDate"),
                Sort.Order.asc("id")
        ));
        if (entries.isEmpty()) {
            return DashboardGroupedByRatioResponseDTO.builder()
                    .ratios(Map.of())
                    .build();
        }

        Set<Long> ratioIds = entries.stream()
                .map(DashboardEntry::getIdRatios)
                .collect(Collectors.toSet());

        Map<Long, RatiosConfig> ratioById = ratiosConfigRepository.findAllById(ratioIds).stream()
                .collect(Collectors.toMap(RatiosConfig::getId, Function.identity()));

        record RatioDateRow(String ratioCode, LocalDate referenceDate, Double value) {}
        List<RatioDateRow> rows = entries.stream()
                .map(e -> {
                    RatiosConfig ratio = ratioById.get(e.getIdRatios());
                    String code = ratio != null && ratio.getCode() != null && !ratio.getCode().isBlank()
                            ? ratio.getCode()
                            : "id_" + e.getIdRatios();
                    return new RatioDateRow(code, e.getReferenceDate(), e.getRatiosValue());
                })
                .sorted(Comparator.comparing(RatioDateRow::ratioCode).thenComparing(RatioDateRow::referenceDate))
                .toList();

        Map<String, Map<String, Double>> grouped = new LinkedHashMap<>();
        for (RatioDateRow row : rows) {
            grouped.computeIfAbsent(row.ratioCode(), k -> new LinkedHashMap<>())
                    .put(row.referenceDate().toString(), row.value());
        }

        return DashboardGroupedByRatioResponseDTO.builder()
                .ratios(grouped)
                .build();
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

    /**
     * For every active ratio, evaluate its formula against every distinct date
     * available in fact_balance, and upsert the result into the dashboard table.
     * Rows that already exist (same ratio + date) are skipped (idempotent).
     */
    @Transactional
    public DashboardSimulateAllResponseDTO simulateAll() {
        List<LocalDate> dates = getReferenceDatesFromFactBalance();
        List<RatiosConfig> activeRatios = ratiosConfigRepository.findAll().stream()
                .filter(r -> Boolean.TRUE.equals(r.getIsActive()))
                .toList();

        log.info("[simulateAll] dates in fact_balance: {}", dates);
        log.info("[simulateAll] active ratios count: {}", activeRatios.size());

        if (dates.isEmpty()) {
            log.warn("[simulateAll] No dates found in datamart.fact_balance — is the datamart loaded?");
            return DashboardSimulateAllResponseDTO.builder()
                    .datesFound(0)
                    .dates(List.of())
                    .ratiosProcessed(0)
                    .inserted(0)
                    .skipped(0)
                    .errors(List.of(Map.of(
                            "reason", "No dates found in datamart.fact_balance. Run /api/etl/datamart first."
                    )))
                    .build();
        }

        if (activeRatios.isEmpty()) {
            log.warn("[simulateAll] No active ratios found in ratios_config.");
            return DashboardSimulateAllResponseDTO.builder()
                    .datesFound(dates.size())
                    .dates(dates)
                    .ratiosProcessed(0)
                    .inserted(0)
                    .skipped(0)
                    .errors(List.of(Map.of(
                            "reason", "No active ratios found. Create ratios first."
                    )))
                    .build();
        }

        int inserted = 0;
        int skipped = 0;
        List<Map<String, Object>> errors = new ArrayList<>();

        log.info("[simulateAll] Starting: {} ratios × {} dates = {} combinations",
                activeRatios.size(), dates.size(), (long) activeRatios.size() * dates.size());

        for (RatiosConfig ratio : activeRatios) {
            log.info("[simulateAll] Processing ratio: code={} id={}", ratio.getCode(), ratio.getId());

            ExpressionNode node;
            try {
                node = ratioFormulaMapper.toExpressionNode(ratio.getFormula());
            } catch (Exception ex) {
                log.error("[simulateAll] ratio={} — formula parse failed: {}", ratio.getCode(), ex.getMessage());
                errors.add(Map.of(
                        "ratioId", ratio.getId(),
                        "ratioCode", ratio.getCode(),
                        "reason", "formula parse error: " + ex.getMessage()
                ));
                continue;
            }

            for (LocalDate date : dates) {
                if (dashboardRepository.existsByIdRatiosAndReferenceDate(ratio.getId(), date)) {
                    log.debug("[simulateAll] ratio={} date={} — already exists, skipping", ratio.getCode(), date);
                    skipped++;
                    continue;
                }

                try {
                    log.info("[simulateAll] Evaluating ratio={} at date={}...", ratio.getCode(), date);
                    RatioFormulaExecutionResult result = formulaEvaluationService.evaluateAtDate(node, date);
                    double value = result.value();

                    dashboardRepository.save(DashboardEntry.builder()
                            .idRatios(ratio.getId())
                            .ratiosValue(value)
                            .referenceDate(date)
                            .build());

                    inserted++;
                    log.info("[simulateAll] ✓ ratio={} date={} value={}", ratio.getCode(), date, value);

                } catch (Exception ex) {
                    Throwable root = ex;
                    while (root.getCause() != null && root.getCause() != root) root = root.getCause();
                    log.warn("[simulateAll] ✗ ratio={} date={} error: {}", ratio.getCode(), date, root.getMessage());
                    errors.add(Map.of(
                            "ratioId", ratio.getId(),
                            "ratioCode", ratio.getCode(),
                            "date", date.toString(),
                            "reason", root.getMessage() != null ? root.getMessage() : "unknown error"
                    ));
                }
            }
        }

        log.info("[simulateAll] Finished — inserted={} skipped={} errors={}",
                inserted, skipped, errors.size());

        return DashboardSimulateAllResponseDTO.builder()
                .datesFound(dates.size())
                .dates(dates)
                .ratiosProcessed(activeRatios.size())
                .inserted(inserted)
                .skipped(skipped)
                .errors(errors)
                .build();
    }

    @Transactional(readOnly = true)
    public List<LocalDate> getAvailableDates() {
        return getReferenceDatesFromFactBalance();
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