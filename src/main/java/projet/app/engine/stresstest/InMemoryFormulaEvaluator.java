package projet.app.engine.stresstest;

import org.springframework.stereotype.Component;
import projet.app.engine.ast.AggregationNode;
import projet.app.engine.ast.BinaryOperationNode;
import projet.app.engine.ast.FieldNode;
import projet.app.engine.ast.FilterGroupNode;
import projet.app.engine.ast.FormulaDefinition;
import projet.app.engine.ast.FormulaNode;
import projet.app.engine.ast.ValueNode;
import projet.app.engine.enums.AggregationFunction;
import projet.app.engine.enums.ArithmeticOperator;
import projet.app.engine.registry.FieldDefinition;
import projet.app.engine.registry.FieldRegistry;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Executes a parsed {@link FormulaDefinition} over an in-memory row dataset and returns
 * a scalar double (matching how parameters are consumed by ratios).
 *
 * <p>Semantics mirror the SQL compilation path:
 * <ul>
 *     <li>The top-level {@code whereFilter} pre-filters the row set.</li>
 *     <li>Aggregations (SUM/AVG/MIN/MAX/COUNT) apply their inner filter and optional
 *     expression.</li>
 *     <li>Binary arithmetic reduces to a scalar.</li>
 * </ul>
 *
 * <p>{@code groupBy}/{@code orderBy}/{@code limit}/{@code top} are ignored: stress-test
 * comparison always operates on scalar parameters.</p>
 */
@Component
public class InMemoryFormulaEvaluator {

    private static final double EPSILON = 1e-12d;

    private final FieldRegistry fieldRegistry;
    private final InMemoryFilterMatcher filterMatcher;

    public InMemoryFormulaEvaluator(FieldRegistry fieldRegistry, InMemoryFilterMatcher filterMatcher) {
        this.fieldRegistry = fieldRegistry;
        this.filterMatcher = filterMatcher;
    }

    public double evaluate(FormulaDefinition definition, List<InMemoryRow> rows) {
        List<InMemoryRow> scope = applyTopLevelFilter(definition.whereFilter(), rows);
        return evaluateNode(definition.expression(), scope);
    }

    private List<InMemoryRow> applyTopLevelFilter(FilterGroupNode filter, List<InMemoryRow> rows) {
        if (filter == null || filter.isEmpty()) {
            return rows;
        }
        List<InMemoryRow> kept = new ArrayList<>(rows.size());
        for (InMemoryRow row : rows) {
            if (filterMatcher.matches(filter, row)) {
                kept.add(row);
            }
        }
        return kept;
    }

    private double evaluateNode(FormulaNode node, List<InMemoryRow> rows) {
        if (node instanceof ValueNode valueNode) {
            return toDouble(valueNode.value());
        }
        if (node instanceof AggregationNode aggregationNode) {
            return evaluateAggregation(aggregationNode, rows);
        }
        if (node instanceof BinaryOperationNode binaryNode) {
            double left = evaluateNode(binaryNode.left(), rows);
            double right = evaluateNode(binaryNode.right(), rows);
            return applyOperator(binaryNode.operator(), left, right);
        }
        if (node instanceof FieldNode) {
            throw new IllegalStateException(
                    "Field expression outside of an aggregation is not supported in stress-test evaluation"
            );
        }
        throw new IllegalStateException("Unsupported formula node: " + node.getClass().getSimpleName());
    }

    private double evaluateAggregation(AggregationNode node, List<InMemoryRow> rows) {
        List<InMemoryRow> scope = rows;
        if (node.filters() != null && !node.filters().isEmpty()) {
            scope = new ArrayList<>(rows.size());
            for (InMemoryRow row : rows) {
                if (filterMatcher.matches(node.filters(), row)) {
                    scope.add(row);
                }
            }
        }

        AggregationFunction function = node.function();

        if (function == AggregationFunction.COUNT) {
            return evaluateCount(node, scope);
        }

        List<Double> numericValues = collectNumericValues(node, scope);
        if (numericValues.isEmpty()) {
            return function == AggregationFunction.SUM ? 0d : 0d;
        }

        return switch (function) {
            case SUM -> numericValues.stream().mapToDouble(Double::doubleValue).sum();
            case AVG -> numericValues.stream().mapToDouble(Double::doubleValue).average().orElse(0d);
            case MIN -> numericValues.stream().mapToDouble(Double::doubleValue).min().orElse(0d);
            case MAX -> numericValues.stream().mapToDouble(Double::doubleValue).max().orElse(0d);
            case COUNT -> throw new IllegalStateException("COUNT handled above");
        };
    }

    private double evaluateCount(AggregationNode node, List<InMemoryRow> rows) {
        boolean hasField = node.field() != null && !node.field().isBlank();
        boolean hasExpression = node.expression() != null;

        if (!hasField && !hasExpression) {
            if (node.distinct()) {
                Set<InMemoryRow> distinct = new HashSet<>(rows);
                return distinct.size();
            }
            return rows.size();
        }

        if (hasField) {
            FieldDefinition definition = fieldRegistry.resolve(node.field());
            if (node.distinct()) {
                Set<Object> distinct = new HashSet<>();
                for (InMemoryRow row : rows) {
                    Object value = row.get(definition.fieldName());
                    if (value != null) {
                        distinct.add(value);
                    }
                }
                return distinct.size();
            }
            long count = 0;
            for (InMemoryRow row : rows) {
                if (row.get(definition.fieldName()) != null) {
                    count++;
                }
            }
            return count;
        }

        // COUNT over an inner expression: count non-null numeric results.
        long count = 0;
        Set<Double> distinct = node.distinct() ? new HashSet<>() : null;
        for (InMemoryRow row : rows) {
            Double value = evaluateInnerExpressionForRow(node.expression(), row);
            if (value == null) {
                continue;
            }
            if (distinct != null) {
                distinct.add(value);
            } else {
                count++;
            }
        }
        return distinct != null ? distinct.size() : count;
    }

    private List<Double> collectNumericValues(AggregationNode node, List<InMemoryRow> rows) {
        List<Double> values = new ArrayList<>(rows.size());
        boolean distinct = node.distinct();
        Set<Double> distinctValues = distinct ? new HashSet<>() : null;

        if (node.field() != null && !node.field().isBlank()) {
            FieldDefinition definition = fieldRegistry.resolve(node.field());
            for (InMemoryRow row : rows) {
                Object raw = row.get(definition.fieldName());
                if (raw == null) {
                    continue;
                }
                Double numeric = coerceToDouble(raw);
                if (numeric == null) {
                    continue;
                }
                if (distinctValues != null) {
                    if (distinctValues.add(numeric)) {
                        values.add(numeric);
                    }
                } else {
                    values.add(numeric);
                }
            }
            return values;
        }

        if (node.expression() != null) {
            for (InMemoryRow row : rows) {
                Double numeric = evaluateInnerExpressionForRow(node.expression(), row);
                if (numeric == null) {
                    continue;
                }
                if (distinctValues != null) {
                    if (distinctValues.add(numeric)) {
                        values.add(numeric);
                    }
                } else {
                    values.add(numeric);
                }
            }
        }

        return values;
    }

    /**
     * Evaluates an expression used inside an aggregation for a single row (per-row
     * arithmetic that will be aggregated). Returns {@code null} when the row does not
     * contribute (any operand missing / divide by zero).
     */
    private Double evaluateInnerExpressionForRow(FormulaNode node, InMemoryRow row) {
        if (node instanceof FieldNode fieldNode) {
            FieldDefinition definition = fieldRegistry.resolve(fieldNode.field());
            Object value = row.get(definition.fieldName());
            return coerceToDouble(value);
        }
        if (node instanceof ValueNode valueNode) {
            return coerceToDouble(valueNode.value());
        }
        if (node instanceof BinaryOperationNode binary) {
            Double left = evaluateInnerExpressionForRow(binary.left(), row);
            Double right = evaluateInnerExpressionForRow(binary.right(), row);
            if (left == null || right == null) {
                return null;
            }
            return switch (binary.operator()) {
                case ADD -> left + right;
                case SUBTRACT -> left - right;
                case MULTIPLY -> left * right;
                case DIVIDE -> Math.abs(right) < EPSILON ? null : left / right;
            };
        }
        if (node instanceof AggregationNode) {
            throw new IllegalStateException("Nested aggregations are not supported");
        }
        return null;
    }

    private double applyOperator(ArithmeticOperator operator, double left, double right) {
        return switch (operator) {
            case ADD -> left + right;
            case SUBTRACT -> left - right;
            case MULTIPLY -> left * right;
            case DIVIDE -> {
                if (Math.abs(right) < EPSILON) {
                    yield 0d;
                }
                yield left / right;
            }
        };
    }

    private double toDouble(Object value) {
        Double coerced = coerceToDouble(value);
        return coerced == null ? 0d : coerced;
    }

    private Double coerceToDouble(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal bd) {
            return bd.doubleValue();
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof Boolean bool) {
            return bool ? 1d : 0d;
        }
        if (value instanceof String str) {
            String trimmed = str.trim();
            if (trimmed.isEmpty()) {
                return null;
            }
            try {
                return Double.parseDouble(trimmed);
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        return null;
    }
}
