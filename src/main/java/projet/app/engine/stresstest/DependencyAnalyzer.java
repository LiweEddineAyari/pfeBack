package projet.app.engine.stresstest;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import projet.app.engine.ast.FormulaDefinition;
import projet.app.engine.registry.FieldDefinition;
import projet.app.engine.registry.FieldRegistry;
import projet.app.engine.validation.FormulaValidationService;
import projet.app.entity.mapping.ParameterConfig;
import projet.app.entity.mapping.RatiosConfig;
import projet.app.ratio.formula.ExpressionNode;
import projet.app.service.ratio.FormulaValidatorService;
import projet.app.service.ratio.RatioFormulaMapper;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Builds dependency graphs required for stress-test impact analysis:
 *
 * <pre>
 *   field   -> parameters using it
 *   param   -> ratios referencing it
 * </pre>
 *
 * The analyzer is stateless: callers pass in the collections of parameters/ratios they want to
 * analyse (typically the full catalog).
 */
@Component
public class DependencyAnalyzer {

    private final FieldRegistry fieldRegistry;
    private final FormulaValidationService formulaValidationService;
    private final FormulaFieldCollector formulaFieldCollector;
    private final RatioFormulaMapper ratioFormulaMapper;
    private final FormulaValidatorService ratioFormulaValidatorService;

    public DependencyAnalyzer(
            FieldRegistry fieldRegistry,
            FormulaValidationService formulaValidationService,
            FormulaFieldCollector formulaFieldCollector,
            RatioFormulaMapper ratioFormulaMapper,
            FormulaValidatorService ratioFormulaValidatorService
    ) {
        this.fieldRegistry = fieldRegistry;
        this.formulaValidationService = formulaValidationService;
        this.formulaFieldCollector = formulaFieldCollector;
        this.ratioFormulaMapper = ratioFormulaMapper;
        this.ratioFormulaValidatorService = ratioFormulaValidatorService;
    }

    /**
     * Resolves any supported field alias (user input) to the canonical lower-case field name.
     */
    public String canonicalFieldName(String rawField) {
        FieldDefinition definition = fieldRegistry.resolve(rawField);
        return definition.fieldName().toLowerCase(Locale.ROOT);
    }

    /**
     * Builds a {@code field -> [parameter codes]} map from the provided parameter catalog.
     */
    public Map<String, Set<String>> buildFieldToParameterMap(Collection<ParameterConfig> parameters) {
        Map<String, Set<String>> map = new LinkedHashMap<>();
        for (ParameterConfig parameter : parameters) {
            Set<String> fields = fieldsUsedByParameter(parameter);
            for (String field : fields) {
                map.computeIfAbsent(field, key -> new LinkedHashSet<>()).add(parameter.getCode());
            }
        }
        return map;
    }

    /**
     * Builds a {@code parameterCode -> [ratio codes]} map from the provided ratio catalog.
     */
    public Map<String, Set<String>> buildParameterToRatioMap(Collection<RatiosConfig> ratios) {
        Map<String, Set<String>> map = new LinkedHashMap<>();
        for (RatiosConfig ratio : ratios) {
            Set<String> params = parametersUsedByRatio(ratio);
            for (String param : params) {
                map.computeIfAbsent(param, key -> new LinkedHashSet<>()).add(ratio.getCode());
            }
        }
        return map;
    }

    public Set<String> fieldsUsedByParameter(ParameterConfig parameter) {
        if (parameter == null || parameter.getFormulaJson() == null) {
            return Set.of();
        }
        try {
            FormulaDefinition definition = formulaValidationService.validateAndParse(parameter.getFormulaJson());
            return formulaFieldCollector.collect(definition);
        } catch (RuntimeException ex) {
            return Set.of();
        }
    }

    public Set<String> fieldsUsedByFormula(JsonNode formulaJson) {
        if (formulaJson == null) {
            return Set.of();
        }
        FormulaDefinition definition = formulaValidationService.validateAndParse(formulaJson);
        return formulaFieldCollector.collect(definition);
    }

    public Set<String> parametersUsedByRatio(RatiosConfig ratio) {
        if (ratio == null || ratio.getFormula() == null) {
            return Set.of();
        }
        try {
            ExpressionNode expressionNode = ratioFormulaMapper.toExpressionNode(ratio.getFormula());
            return ratioFormulaValidatorService.collectReferencedParameterCodes(expressionNode);
        } catch (RuntimeException ex) {
            return Set.of();
        }
    }

    /**
     * Computes the set of parameters transitively affected when one of the supplied fields is
     * altered. Today parameters only reference fact/dim columns directly, so the map is a simple
     * lookup; the helper keeps the API ready for transitive chains.
     */
    public Set<String> computeAffectedParameters(
            Set<String> affectedFields,
            Map<String, Set<String>> fieldToParameters
    ) {
        Set<String> result = new LinkedHashSet<>();
        for (String field : affectedFields) {
            Set<String> parameters = fieldToParameters.get(field);
            if (parameters != null) {
                result.addAll(parameters);
            }
        }
        return result;
    }

    /**
     * Computes the set of ratios transitively affected when one of the supplied parameters is
     * altered.
     */
    public Set<String> computeAffectedRatios(
            Set<String> affectedParameters,
            Map<String, Set<String>> parameterToRatios
    ) {
        Set<String> result = new LinkedHashSet<>();
        for (String parameter : affectedParameters) {
            Set<String> ratios = parameterToRatios.get(parameter);
            if (ratios != null) {
                result.addAll(ratios);
            }
        }
        return result;
    }

    /**
     * Returns the set of parameters referenced by the given ratio codes (useful to pick which
     * parameters must be evaluated when the caller scopes to a ratio subset).
     */
    public Set<String> parametersForRatios(List<RatiosConfig> ratios, Set<String> ratioCodes) {
        Set<String> result = new LinkedHashSet<>();
        for (RatiosConfig ratio : ratios) {
            if (!ratioCodes.contains(ratio.getCode())) {
                continue;
            }
            result.addAll(parametersUsedByRatio(ratio));
        }
        return result;
    }
}
