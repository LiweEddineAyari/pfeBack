package projet.app.service.mapping;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import projet.app.dto.FormulaExecutionResponseDTO;
import projet.app.dto.FormulaRequestDTO;
import projet.app.dto.FormulaSqlResponseDTO;
import projet.app.dto.ParameterConfigResponseDTO;
import projet.app.engine.ast.FormulaDefinition;
import projet.app.engine.compiler.CompiledSql;
import projet.app.engine.registry.FieldRegistry;
import projet.app.entity.mapping.ParameterConfig;
import projet.app.exception.ParameterConfigNotFoundException;
import projet.app.repository.mapping.ParameterConfigRepository;

import java.util.List;

@Service
public class FormulaEngineService {

    private final ParameterConfigRepository parameterConfigRepository;
    private final ValidationService validationService;
    private final SqlCompilerService sqlCompilerService;
    private final FieldRegistry fieldRegistry;
    private final JdbcTemplate jdbcTemplate;

    public FormulaEngineService(
            ParameterConfigRepository parameterConfigRepository,
            ValidationService validationService,
            SqlCompilerService sqlCompilerService,
            FieldRegistry fieldRegistry,
            JdbcTemplate jdbcTemplate
    ) {
        this.parameterConfigRepository = parameterConfigRepository;
        this.validationService = validationService;
        this.sqlCompilerService = sqlCompilerService;
        this.fieldRegistry = fieldRegistry;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public ParameterConfigResponseDTO create(FormulaRequestDTO request) {
        if (parameterConfigRepository.existsByCode(request.getCode())) {
            throw new IllegalArgumentException("Parameter config already exists for code: " + request.getCode());
        }

        JsonNode normalizedFormula = normalizeAndValidate(request.getFormula());

        ParameterConfig entity = ParameterConfig.builder()
                .code(request.getCode().trim())
                .label(request.getLabel().trim())
                .formulaJson(normalizedFormula)
                .version(1)
                .isActive(request.getIsActive() == null ? Boolean.TRUE : request.getIsActive())
                .build();

        ParameterConfig saved = parameterConfigRepository.save(entity);
        return toResponse(saved);
    }

    @Transactional
    public ParameterConfigResponseDTO update(String code, FormulaRequestDTO request) {
        ParameterConfig existing = parameterConfigRepository.findByCode(code)
                .orElseThrow(() -> new ParameterConfigNotFoundException(code));

        if (request.getCode() != null && !request.getCode().isBlank() && !code.equalsIgnoreCase(request.getCode().trim())) {
            throw new IllegalArgumentException("Request code does not match path code");
        }

        JsonNode normalizedFormula = normalizeAndValidate(request.getFormula());

        existing.setLabel(request.getLabel().trim());
        existing.setFormulaJson(normalizedFormula);
        existing.setVersion((existing.getVersion() == null ? 0 : existing.getVersion()) + 1);
        if (request.getIsActive() != null) {
            existing.setIsActive(request.getIsActive());
        }

        ParameterConfig updated = parameterConfigRepository.save(existing);
        return toResponse(updated);
    }

    @Transactional(readOnly = true)
    public ParameterConfigResponseDTO getByCode(String code) {
        ParameterConfig config = parameterConfigRepository.findByCode(code)
                .orElseThrow(() -> new ParameterConfigNotFoundException(code));
        return toResponse(config);
    }

    @Transactional(readOnly = true)
    public FormulaSqlResponseDTO compileByCode(String code) {
        ParameterConfig config = parameterConfigRepository.findByCodeAndIsActiveTrue(code)
                .orElseThrow(() -> new ParameterConfigNotFoundException(code));

        JsonNode formulaJson = config.getFormulaJson();
        FormulaDefinition definition = validationService.validateAndParse(formulaJson);
        CompiledSql compiledSql = sqlCompilerService.compile(definition);

        return FormulaSqlResponseDTO.builder()
                .code(config.getCode())
                .version(config.getVersion())
                .sql(compiledSql.sql())
                .parameters(compiledSql.parameters())
                .referencedFields(compiledSql.referencedFields())
            .joins(compiledSql.joins())
            .groupByFields(compiledSql.groupByFields())
                .build();
    }

    @Transactional(readOnly = true)
    public FormulaExecutionResponseDTO executeByCode(String code) {
        ParameterConfig config = parameterConfigRepository.findByCodeAndIsActiveTrue(code)
            .orElseThrow(() -> new ParameterConfigNotFoundException(code));

        FormulaDefinition definition = validationService.validateAndParse(config.getFormulaJson());
        CompiledSql compiledSql = sqlCompilerService.compile(definition);

        Object result;
        if (compiledSql.groupByFields() != null && !compiledSql.groupByFields().isEmpty()) {
            result = jdbcTemplate.queryForList(compiledSql.sql(), compiledSql.parameters().toArray());
        } else {
            result = jdbcTemplate.queryForObject(compiledSql.sql(), Object.class, compiledSql.parameters().toArray());
        }

        return FormulaExecutionResponseDTO.builder()
                .code(code)
            .sql(compiledSql.sql())
            .parameters(compiledSql.parameters())
                .value(result)
                .build();
    }

    @Transactional(readOnly = true)
    public List<String> getSupportedFields() {
        return fieldRegistry.supportedFields().stream()
                .sorted()
                .toList();
    }

    private JsonNode normalizeAndValidate(JsonNode formulaNode) {
        validationService.validateAndParse(formulaNode);
        return formulaNode;
    }

    private ParameterConfigResponseDTO toResponse(ParameterConfig entity) {
        return ParameterConfigResponseDTO.builder()
                .id(entity.getId())
                .code(entity.getCode())
                .label(entity.getLabel())
                .formula(entity.getFormulaJson())
                .version(entity.getVersion())
                .isActive(entity.getIsActive())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
