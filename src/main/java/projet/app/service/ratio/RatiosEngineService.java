package projet.app.service.ratio;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import projet.app.dto.BulkDeleteResponseDTO;
import projet.app.dto.RatioExecutionResponseDTO;
import projet.app.dto.RatioSimulationRequestDTO;
import projet.app.dto.RatioSimulationResponseDTO;
import projet.app.dto.RatiosConfigRequestDTO;
import projet.app.dto.RatiosConfigResponseDTO;
import projet.app.entity.mapping.RatiosConfig;
import projet.app.exception.RatiosConfigNotFoundException;
import projet.app.ratio.formula.ExpressionNode;
import projet.app.repository.mapping.CategorieRatiosRepository;
import projet.app.repository.mapping.FamilleRatiosRepository;
import projet.app.repository.mapping.RatiosConfigRepository;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class RatiosEngineService {

    private final RatiosConfigRepository ratiosConfigRepository;
    private final RatioFormulaMapper ratioFormulaMapper;
    private final FormulaValidatorService formulaValidatorService;
    private final FormulaEvaluationService formulaEvaluationService;
    private final FormulaSqlBuilderService formulaSqlBuilderService;
    private final FamilleRatiosRepository familleRatiosRepository;
    private final CategorieRatiosRepository categorieRatiosRepository;

    public RatiosEngineService(
            RatiosConfigRepository ratiosConfigRepository,
            RatioFormulaMapper ratioFormulaMapper,
            FormulaValidatorService formulaValidatorService,
            FormulaEvaluationService formulaEvaluationService,
            FormulaSqlBuilderService formulaSqlBuilderService,
            FamilleRatiosRepository familleRatiosRepository,
            CategorieRatiosRepository categorieRatiosRepository
    ) {
        this.ratiosConfigRepository = ratiosConfigRepository;
        this.ratioFormulaMapper = ratioFormulaMapper;
        this.formulaValidatorService = formulaValidatorService;
        this.formulaEvaluationService = formulaEvaluationService;
        this.formulaSqlBuilderService = formulaSqlBuilderService;
        this.familleRatiosRepository = familleRatiosRepository;
        this.categorieRatiosRepository = categorieRatiosRepository;
    }

    @Transactional
    public RatiosConfigResponseDTO create(RatiosConfigRequestDTO request) {
        String ratioCode = request.getCode().trim();
        if (ratiosConfigRepository.existsByCode(ratioCode)) {
            throw new IllegalArgumentException("Ratios config already exists for code: " + ratioCode);
        }

        validateForeignKeys(request.getFamilleId(), request.getCategorieId());

        ExpressionNode expressionNode = parseAndValidate(request.getFormula());
        JsonNode normalizedFormula = ratioFormulaMapper.toJsonNode(expressionNode);

        RatiosConfig entity = RatiosConfig.builder()
                .code(ratioCode)
                .label(request.getLabel().trim())
            .familleId(request.getFamilleId())
            .categorieId(request.getCategorieId())
                .formula(normalizedFormula)
                .seuilTolerance(request.getSeuilTolerance())
                .seuilAlerte(request.getSeuilAlerte())
                .seuilAppetence(request.getSeuilAppetence())
                .description(request.getDescription())
                .version(1)
                .isActive(request.getIsActive() == null ? Boolean.TRUE : request.getIsActive())
                .build();

        RatiosConfig saved = ratiosConfigRepository.save(entity);
        return toResponse(saved);
    }

    @Transactional
    public RatiosConfigResponseDTO update(String code, RatiosConfigRequestDTO request) {
        RatiosConfig existing = ratiosConfigRepository.findByCode(code)
                .orElseThrow(() -> new RatiosConfigNotFoundException(code));

        if (request.getCode() != null
                && !request.getCode().isBlank()
                && !code.equalsIgnoreCase(request.getCode().trim())) {
            throw new IllegalArgumentException("Request code does not match path code");
        }

        validateForeignKeys(request.getFamilleId(), request.getCategorieId());

        ExpressionNode expressionNode = parseAndValidate(request.getFormula());
        JsonNode normalizedFormula = ratioFormulaMapper.toJsonNode(expressionNode);

        existing.setLabel(request.getLabel().trim());
        existing.setFamilleId(request.getFamilleId());
        existing.setCategorieId(request.getCategorieId());
        existing.setFormula(normalizedFormula);
        existing.setSeuilTolerance(request.getSeuilTolerance());
        existing.setSeuilAlerte(request.getSeuilAlerte());
        existing.setSeuilAppetence(request.getSeuilAppetence());
        existing.setDescription(request.getDescription());
        existing.setVersion((existing.getVersion() == null ? 0 : existing.getVersion()) + 1);
        if (request.getIsActive() != null) {
            existing.setIsActive(request.getIsActive());
        }

        RatiosConfig updated = ratiosConfigRepository.save(existing);
        return toResponse(updated);
    }

    @Transactional(readOnly = true)
    public List<RatiosConfigResponseDTO> list() {
        return ratiosConfigRepository.findAll().stream()
                .sorted(Comparator.comparing(RatiosConfig::getCode, String.CASE_INSENSITIVE_ORDER))
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public RatiosConfigResponseDTO getByCode(String code) {
        RatiosConfig entity = ratiosConfigRepository.findByCode(code)
                .orElseThrow(() -> new RatiosConfigNotFoundException(code));
        return toResponse(entity);
    }

    @Transactional
    public void deleteByCode(String code) {
        RatiosConfig entity = ratiosConfigRepository.findByCode(code)
                .orElseThrow(() -> new RatiosConfigNotFoundException(code));
        ratiosConfigRepository.delete(entity);
    }

        @Transactional
        public BulkDeleteResponseDTO deleteManyByCodes(List<String> codes) {
        List<String> normalizedCodes = normalizeCodeList(codes);
        List<RatiosConfig> existingConfigs = ratiosConfigRepository.findAllByCodeIn(normalizedCodes);

        Set<String> existingCodes = existingConfigs.stream()
            .map(RatiosConfig::getCode)
            .collect(Collectors.toSet());

        List<String> deletedCodes = normalizedCodes.stream()
            .filter(existingCodes::contains)
            .toList();

        List<String> missingCodes = normalizedCodes.stream()
            .filter(code -> !existingCodes.contains(code))
            .toList();

        if (!existingConfigs.isEmpty()) {
            ratiosConfigRepository.deleteAllInBatch(existingConfigs);
        }

        return BulkDeleteResponseDTO.builder()
            .requestedCount(normalizedCodes.size())
            .deletedCount(deletedCodes.size())
            .deletedCodes(deletedCodes)
            .missingCodes(missingCodes)
            .build();
        }

    @Transactional(readOnly = true)
    public RatioSimulationResponseDTO simulate(RatioSimulationRequestDTO request) {
        ExpressionNode expressionNode = parseAndValidate(request.getFormula());
        double value = formulaEvaluationService.evaluate(expressionNode);
        String sqlExpression = formulaSqlBuilderService.build(expressionNode);
        Set<String> referencedParameters = formulaValidatorService.collectReferencedParameterCodes(expressionNode);

        return RatioSimulationResponseDTO.builder()
                .value(value)
                .sqlExpression(sqlExpression)
                .referencedParameters(referencedParameters)
                .build();
    }

    @Transactional(readOnly = true)
    public RatioExecutionResponseDTO executeByCodeAtDate(String code, LocalDate referenceDate) {
        RatiosConfig ratio = ratiosConfigRepository.findByCodeAndIsActiveTrue(code)
                .orElseThrow(() -> new RatiosConfigNotFoundException(code));

        ExpressionNode expressionNode = parseAndValidate(ratio.getFormula());
        RatioFormulaExecutionResult evaluation = formulaEvaluationService.evaluateAtDate(expressionNode, referenceDate);
        String sqlExpression = formulaSqlBuilderService.build(expressionNode, referenceDate);
        Set<String> referencedParameters = formulaValidatorService.collectReferencedParameterCodes(expressionNode);

        return RatioExecutionResponseDTO.builder()
                .code(ratio.getCode())
                .referenceDate(referenceDate)
                .value(evaluation.value())
                .sqlExpression(sqlExpression)
                .referencedParameters(referencedParameters)
                .resolvedParameters(evaluation.resolvedParameters())
                .build();
    }

    private ExpressionNode parseAndValidate(JsonNode formulaNode) {
        ExpressionNode expressionNode = ratioFormulaMapper.toExpressionNode(formulaNode);
        formulaValidatorService.validate(expressionNode);
        return expressionNode;
    }

    private RatiosConfigResponseDTO toResponse(RatiosConfig entity) {
        return RatiosConfigResponseDTO.builder()
                .id(entity.getId())
                .code(entity.getCode())
                .label(entity.getLabel())
                .familleId(entity.getFamilleId())
                .categorieId(entity.getCategorieId())
                .formula(entity.getFormula())
                .seuilTolerance(entity.getSeuilTolerance())
                .seuilAlerte(entity.getSeuilAlerte())
                .seuilAppetence(entity.getSeuilAppetence())
                .description(entity.getDescription())
                .version(entity.getVersion())
                .isActive(entity.getIsActive())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private void validateForeignKeys(Long familleId, Long categorieId) {
        if (familleId == null || !familleRatiosRepository.existsById(familleId)) {
            throw new IllegalArgumentException("familleId does not exist: " + familleId);
        }

        if (categorieId == null || !categorieRatiosRepository.existsById(categorieId)) {
            throw new IllegalArgumentException("categorieId does not exist: " + categorieId);
        }
    }

    private List<String> normalizeCodeList(List<String> codes) {
        if (codes == null || codes.isEmpty()) {
            throw new IllegalArgumentException("codes list must not be empty");
        }

        List<String> normalizedCodes = codes.stream()
                .filter(code -> code != null && !code.isBlank())
                .map(String::trim)
                .distinct()
                .toList();

        if (normalizedCodes.isEmpty()) {
            throw new IllegalArgumentException("codes list must contain at least one non-empty code");
        }

        return normalizedCodes;
    }
}
