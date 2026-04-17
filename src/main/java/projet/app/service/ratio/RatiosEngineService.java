package projet.app.service.ratio;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import projet.app.dto.RatioDimensionValueDTO;
import projet.app.dto.RatioExecutionResponseDTO;
import projet.app.dto.RatioSimulationRequestDTO;
import projet.app.dto.RatioSimulationResponseDTO;
import projet.app.dto.RatiosConfigRequestDTO;
import projet.app.dto.RatiosConfigResponseDTO;
import projet.app.entity.mapping.RatiosConfig;
import projet.app.exception.RatiosConfigNotFoundException;
import projet.app.ratio.formula.ExpressionNode;
import projet.app.repository.mapping.RatiosConfigRepository;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class RatiosEngineService {

    private final RatiosConfigRepository ratiosConfigRepository;
    private final RatioFormulaMapper ratioFormulaMapper;
    private final FormulaValidatorService formulaValidatorService;
    private final FormulaEvaluationService formulaEvaluationService;
    private final FormulaSqlBuilderService formulaSqlBuilderService;

    public RatiosEngineService(
            RatiosConfigRepository ratiosConfigRepository,
            RatioFormulaMapper ratioFormulaMapper,
            FormulaValidatorService formulaValidatorService,
            FormulaEvaluationService formulaEvaluationService,
            FormulaSqlBuilderService formulaSqlBuilderService
    ) {
        this.ratiosConfigRepository = ratiosConfigRepository;
        this.ratioFormulaMapper = ratioFormulaMapper;
        this.formulaValidatorService = formulaValidatorService;
        this.formulaEvaluationService = formulaEvaluationService;
        this.formulaSqlBuilderService = formulaSqlBuilderService;
    }

    @Transactional
    public RatiosConfigResponseDTO create(RatiosConfigRequestDTO request) {
        String ratioCode = request.getCode().trim();
        if (ratiosConfigRepository.existsByCode(ratioCode)) {
            throw new IllegalArgumentException("Ratios config already exists for code: " + ratioCode);
        }

        ExpressionNode expressionNode = parseAndValidate(request.getFormula());
        JsonNode normalizedFormula = ratioFormulaMapper.toJsonNode(expressionNode);

        RatiosConfig entity = RatiosConfig.builder()
                .code(ratioCode)
                .label(request.getLabel().trim())
                .famille(request.getFamille().trim())
                .categorie(request.getCategorie().trim())
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

        ExpressionNode expressionNode = parseAndValidate(request.getFormula());
        JsonNode normalizedFormula = ratioFormulaMapper.toJsonNode(expressionNode);

        existing.setLabel(request.getLabel().trim());
        existing.setFamille(request.getFamille().trim());
        existing.setCategorie(request.getCategorie().trim());
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

    @Transactional(readOnly = true)
    public RatioSimulationResponseDTO simulate(RatioSimulationRequestDTO request) {
        ExpressionNode expressionNode = parseAndValidate(request.getFormula());
        ParameterResult result = formulaEvaluationService.evaluate(expressionNode);
        String sqlExpression = formulaSqlBuilderService.build(expressionNode);
        Set<String> referencedParameters = formulaValidatorService.collectReferencedParameterCodes(expressionNode);

        return RatioSimulationResponseDTO.builder()
                .mode(result.mode().name())
                .value(result.isScalar() ? result.getValue() : null)
                .rows(result.isMultiRow() ? toDimensionRows(result.getRows()) : null)
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
                .mode(evaluation.result().mode().name())
                .value(evaluation.result().isScalar() ? evaluation.result().getValue() : null)
                .rows(evaluation.result().isMultiRow() ? toDimensionRows(evaluation.result().getRows()) : null)
                .sqlExpression(sqlExpression)
                .referencedParameters(referencedParameters)
                .resolvedParameters(extractScalarParameters(evaluation.resolvedParameters()))
                .resolvedParameterRows(extractMultiRowParameters(evaluation.resolvedParameters()))
                .build();
    }

    private List<RatioDimensionValueDTO> toDimensionRows(List<RowValue> rows) {
        return rows.stream()
                .map(row -> RatioDimensionValueDTO.builder()
                        .dimensionKey(row.dimensionKey())
                        .value(row.value())
                        .build())
                .toList();
    }

    private Map<String, Double> extractScalarParameters(Map<String, ParameterResult> resolvedParameters) {
        Map<String, Double> scalarParameters = new LinkedHashMap<>();
        for (Map.Entry<String, ParameterResult> entry : resolvedParameters.entrySet()) {
            if (entry.getValue() != null && entry.getValue().isScalar()) {
                scalarParameters.put(entry.getKey(), entry.getValue().getValue());
            }
        }
        return scalarParameters;
    }

    private Map<String, List<RatioDimensionValueDTO>> extractMultiRowParameters(
            Map<String, ParameterResult> resolvedParameters
    ) {
        Map<String, List<RatioDimensionValueDTO>> rowParameters = new LinkedHashMap<>();
        for (Map.Entry<String, ParameterResult> entry : resolvedParameters.entrySet()) {
            if (entry.getValue() != null && entry.getValue().isMultiRow()) {
                rowParameters.put(entry.getKey(), toDimensionRows(entry.getValue().getRows()));
            }
        }
        return rowParameters;
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
                .famille(entity.getFamille())
                .categorie(entity.getCategorie())
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
}
