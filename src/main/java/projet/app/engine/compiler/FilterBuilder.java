package projet.app.engine.compiler;

import org.springframework.stereotype.Service;
import projet.app.engine.ast.FilterConditionNode;
import projet.app.engine.ast.FilterGroupNode;
import projet.app.engine.enums.FilterOperator;
import projet.app.engine.registry.FieldDefinition;
import projet.app.engine.registry.FieldRegistry;
import projet.app.exception.FormulaValidationException;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class FilterBuilder {

    private final FieldRegistry fieldRegistry;

    public FilterBuilder(FieldRegistry fieldRegistry) {
        this.fieldRegistry = fieldRegistry;
    }

    public FilterBuildResult build(FilterGroupNode filterGroup, SqlCompilationContext context) {
        if (filterGroup == null || filterGroup.isEmpty()) {
            return new FilterBuildResult("", List.of());
        }

        List<Object> localParameters = new ArrayList<>();
        String sql = buildGroup(filterGroup, context, localParameters);
        return new FilterBuildResult(sql, localParameters);
    }

    private String buildGroup(FilterGroupNode group, SqlCompilationContext context, List<Object> localParameters) {
        List<String> fragments = new ArrayList<>();

        for (FilterConditionNode condition : group.conditions()) {
            fragments.add(buildCondition(condition, context, localParameters));
        }

        for (FilterGroupNode nested : group.groups()) {
            fragments.add("(" + buildGroup(nested, context, localParameters) + ")");
        }

        if (fragments.isEmpty()) {
            throw new FormulaValidationException(List.of("Filter group cannot be empty"));
        }

        return String.join(" " + group.logic().name() + " ", fragments);
    }

    private String buildCondition(
            FilterConditionNode condition,
            SqlCompilationContext context,
            List<Object> localParameters
    ) {
        FieldDefinition field = fieldRegistry.resolve(condition.field());
        context.addReferencedField(field.fieldName());

        String column = fieldRegistry.toSqlExpression(field);
        FilterOperator operator = condition.operator();

        if (!operator.requiresValue()) {
            return column + " " + operator.getSqlToken();
        }

        Object value = condition.value();

        if (operator.expectsCollectionValue()) {
            if (!(value instanceof List<?> values) || values.isEmpty()) {
                throw new FormulaValidationException(List.of(
                        "Operator " + operator + " requires a non-empty array value for field " + condition.field()
                ));
            }

            String placeholders = values.stream().map(v -> {
                context.addParameter(v);
                localParameters.add(v);
                return "?";
            }).collect(Collectors.joining(", "));

            return column + " " + operator.getSqlToken() + " (" + placeholders + ")";
        }

        if (operator.expectsRangeValue()) {
            if (!(value instanceof List<?> values) || values.size() != 2) {
                throw new FormulaValidationException(List.of("BETWEEN requires exactly two values"));
            }
            context.addParameter(values.get(0));
            context.addParameter(values.get(1));
            localParameters.add(values.get(0));
            localParameters.add(values.get(1));
            return column + " BETWEEN ? AND ?";
        }

        if (operator.isPatternOperator()) {
            if (!(value instanceof String patternValue)) {
                throw new FormulaValidationException(List.of(
                        "Operator " + operator + " requires a string value for field " + condition.field()
                ));
            }

            String normalizedPattern = switch (operator) {
                case STARTS_WITH -> patternValue + "%";
                case ENDS_WITH -> "%" + patternValue;
                case CONTAINS -> "%" + patternValue + "%";
                default -> patternValue;
            };

            context.addParameter(normalizedPattern);
            localParameters.add(normalizedPattern);
            return column + " LIKE ?";
        }

        context.addParameter(value);
        localParameters.add(value);
        return column + " " + operator.getSqlToken() + " ?";
    }
}
