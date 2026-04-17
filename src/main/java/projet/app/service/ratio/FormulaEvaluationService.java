package projet.app.service.ratio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import projet.app.dto.FormulaExecutionResponseDTO;
import projet.app.ratio.formula.AggregateNode;
import projet.app.ratio.formula.BinaryNode;
import projet.app.ratio.formula.ConstantNode;
import projet.app.ratio.formula.ExpressionNode;
import projet.app.ratio.formula.FilterConditionNode;
import projet.app.ratio.formula.FilterNode;
import projet.app.ratio.formula.ParamNode;
import projet.app.service.mapping.FormulaService;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class FormulaEvaluationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(FormulaEvaluationService.class);

    private static final double EPSILON = 1e-12d;
    private static final Set<String> DIMENSION_KEY_HINTS = Set.of(
            "dimension",
            "dimensionkey",
            "clientid",
            "client_id",
            "id_client",
            "f_id_client"
    );

    private final FormulaService formulaService;

    public FormulaEvaluationService(FormulaService formulaService) {
        this.formulaService = formulaService;
    }

    public ParameterResult evaluate(ExpressionNode node) {
        return evaluate(node, null);
    }

    public ParameterResult evaluate(ExpressionNode node, LocalDate referenceDate) {
        return evaluate(node, new EvaluationContext(), referenceDate);
    }

    public RatioFormulaExecutionResult evaluateAtDate(ExpressionNode node, LocalDate referenceDate) {
        EvaluationContext context = new EvaluationContext();
        ParameterResult result = evaluate(node, context, referenceDate);

        if (LOGGER.isDebugEnabled()) {
            int rowsProcessed = result.isMultiRow() ? result.getRows().size() : 1;
            LOGGER.debug(
                    "Ratio evaluation complete at {}: mode={}, rowsProcessed={}, scalarParameters={}, scalarCacheMisses={}, parameterExecutions={}",
                    referenceDate,
                    result.mode(),
                    rowsProcessed,
                    context.scalarCache.size(),
                    context.scalarCacheMisses,
                    context.parameterEvaluationCount
            );
        }

        return new RatioFormulaExecutionResult(result, new LinkedHashMap<>(context.parameterCache));
    }

    private ParameterResult evaluate(
            ExpressionNode node,
            EvaluationContext context,
            LocalDate referenceDate
    ) {
        if (node == null || node.getType() == null || node.getType().isBlank()) {
            throw new IllegalArgumentException("Invalid formula node: missing type");
        }

        String type = node.getType().trim().toUpperCase(Locale.ROOT);

        if ("PARAM".equals(type)) {
            return evaluateParamNode(node, context, referenceDate);
        }

        if ("CONSTANT".equals(type)) {
            return ParameterResult.scalar(evaluateConstantNode(node));
        }

        if ("AGGREGATE".equals(type)) {
            return evaluateAggregateNode(node, context, referenceDate);
        }

        if ("FILTER".equals(type)) {
            return evaluateFilterNode(node, context, referenceDate);
        }

        if (!(node instanceof BinaryNode binaryNode)) {
            throw new IllegalArgumentException("Invalid binary node structure for type: " + type);
        }

        ParameterResult left = evaluate(binaryNode.getLeft(), context, referenceDate);
        ParameterResult right = evaluate(binaryNode.getRight(), context, referenceDate);
        return applyBinaryOperator(type, left, right);
    }

    private ParameterResult evaluateParamNode(
            ExpressionNode node,
            EvaluationContext context,
            LocalDate referenceDate
    ) {
        if (!(node instanceof ParamNode paramNode) || paramNode.getCode() == null || paramNode.getCode().isBlank()) {
            throw new IllegalArgumentException("PARAM node requires a non-empty code");
        }

        String parameterCode = paramNode.getCode().trim();
        if (context.parameterCache.containsKey(parameterCode)) {
            return context.parameterCache.get(parameterCode);
        }

        context.parameterEvaluationCount.merge(parameterCode, 1, Integer::sum);
        FormulaExecutionResponseDTO execution = formulaService.executeByCode(parameterCode, referenceDate);
        ParameterResult result = toParameterResult(parameterCode, execution.getValue());
        context.parameterCache.put(parameterCode, result);

        if (result.isScalar()) {
            if (context.scalarCache.containsKey(parameterCode)) {
                throw new IllegalStateException(
                        "Scalar parameter " + parameterCode + " was evaluated multiple times in a single ratio execution"
                );
            }
            context.scalarCacheMisses++;
            context.scalarCache.put(parameterCode, safeNumber(result.getValue()));
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Scalar parameter {} computed once with value {}", parameterCode, context.scalarCache.get(parameterCode));
            }
        }

        return result;
    }

    private ParameterResult evaluateAggregateNode(
            ExpressionNode node,
            EvaluationContext context,
            LocalDate referenceDate
    ) {
        if (!(node instanceof AggregateNode aggregateNode)) {
            throw new IllegalArgumentException("AGGREGATE node has invalid structure");
        }
        if (aggregateNode.getFunction() == null || aggregateNode.getFunction().isBlank()) {
            throw new IllegalArgumentException("AGGREGATE.function is required");
        }
        if (aggregateNode.getInput() == null) {
            throw new IllegalArgumentException("AGGREGATE.input is required");
        }

        String function = aggregateNode.getFunction().trim().toUpperCase(Locale.ROOT);
    ParameterResult input = evaluate(aggregateNode.getInput(), context, referenceDate);

        if (input.isScalar()) {
            return switch (function) {
                case "COUNT" -> ParameterResult.scalar(1d);
                case "SUM", "AVG", "MIN", "MAX" -> ParameterResult.scalar(input.getValue());
                default -> throw new IllegalArgumentException("Unsupported AGGREGATE.function: " + aggregateNode.getFunction());
            };
        }

        List<RowValue> rows = input.getRows();
        if (rows.isEmpty()) {
            return ParameterResult.scalar(0d);
        }

        return switch (function) {
            case "SUM" -> ParameterResult.scalar(rows.stream().mapToDouble(r -> safeNumber(r.value())).sum());
            case "AVG" -> ParameterResult.scalar(rows.stream().mapToDouble(r -> safeNumber(r.value())).average().orElse(0d));
            case "MAX" -> ParameterResult.scalar(rows.stream().mapToDouble(r -> safeNumber(r.value())).max().orElse(0d));
            case "MIN" -> ParameterResult.scalar(rows.stream().mapToDouble(r -> safeNumber(r.value())).min().orElse(0d));
            case "COUNT" -> ParameterResult.scalar((double) rows.size());
            default -> throw new IllegalArgumentException("Unsupported AGGREGATE.function: " + aggregateNode.getFunction());
        };
    }

    private ParameterResult evaluateFilterNode(
            ExpressionNode node,
            EvaluationContext context,
            LocalDate referenceDate
    ) {
        if (!(node instanceof FilterNode filterNode)) {
            throw new IllegalArgumentException("FILTER node has invalid structure");
        }
        if (filterNode.getInput() == null) {
            throw new IllegalArgumentException("FILTER.input is required");
        }
        if (filterNode.getCondition() == null) {
            throw new IllegalArgumentException("FILTER.condition is required");
        }

        FilterConditionNode condition = filterNode.getCondition();
        if (condition.getOperator() == null || condition.getOperator().isBlank()) {
            throw new IllegalArgumentException("FILTER.condition.operator is required");
        }
        if (condition.getValue() == null) {
            throw new IllegalArgumentException("FILTER.condition.value is required");
        }

        ParameterResult inputResult = evaluate(filterNode.getInput(), context, referenceDate);
        if (!inputResult.isMultiRow()) {
            throw new IllegalArgumentException("FILTER.input must evaluate to a multi-row result");
        }

        ParameterResult conditionResult = condition.getExpression() != null
            ? evaluate(condition.getExpression(), context, referenceDate)
                : inputResult;

        List<RowValue> filteredRows = new ArrayList<>();
        if (conditionResult.isScalar()) {
            if (matchesCondition(safeNumber(conditionResult.getValue()), condition.getOperator(), condition.getValue())) {
                filteredRows.addAll(inputResult.getRows());
            }
            return ParameterResult.multiRow(filteredRows);
        }

        Map<String, Double> conditionByDimension = toDimensionMap(conditionResult.getRows());
        for (RowValue inputRow : inputResult.getRows()) {
            double candidate = conditionByDimension.getOrDefault(inputRow.dimensionKey(), 0d);
            if (matchesCondition(candidate, condition.getOperator(), condition.getValue())) {
                filteredRows.add(inputRow);
            }
        }

        return ParameterResult.multiRow(filteredRows);
    }

        private ParameterResult applyBinaryOperator(String operator, ParameterResult left, ParameterResult right) {
        if (left.isScalar() && right.isScalar()) {
            return ParameterResult.scalar(applyArithmetic(
                    operator,
                    safeNumber(left.getValue()),
                    safeNumber(right.getValue()),
                    "scalar operands"
            ));
        }

        if (left.isMultiRow() && right.isScalar()) {
            double rightValue = safeNumber(right.getValue());
            if ("DIVIDE".equals(operator) && Math.abs(rightValue) < EPSILON) {
                throw new IllegalArgumentException("Division by zero is not allowed for scalar right operand");
            }
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug(
                        "Broadcasting scalar right operand across {} rows for operator {}",
                        left.getRows().size(),
                        operator
                );
            }
            List<RowValue> rows = left.getRows().stream()
                    .map(row -> new RowValue(
                            row.dimensionKey(),
                            applyArithmetic(operator, safeNumber(row.value()), rightValue, "dimension " + row.dimensionKey())
                    ))
                    .toList();
            return ParameterResult.multiRow(rows);
        }

        if (left.isScalar() && right.isMultiRow()) {
            double leftValue = safeNumber(left.getValue());
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug(
                        "Broadcasting scalar left operand across {} rows for operator {}",
                        right.getRows().size(),
                        operator
                );
            }
            List<RowValue> rows = right.getRows().stream()
                    .map(row -> new RowValue(
                            row.dimensionKey(),
                            applyArithmetic(operator, leftValue, safeNumber(row.value()), "dimension " + row.dimensionKey())
                    ))
                    .toList();
            return ParameterResult.multiRow(rows);
        }

        Map<String, Double> leftByDimension = toDimensionMap(left.getRows());
        Map<String, Double> rightByDimension = toDimensionMap(right.getRows());
        Set<String> dimensions = new LinkedHashSet<>();
        dimensions.addAll(leftByDimension.keySet());
        dimensions.addAll(rightByDimension.keySet());

        List<RowValue> rows = dimensions.stream()
                .map(dimension -> new RowValue(
                        dimension,
                        applyArithmetic(
                                operator,
                                leftByDimension.getOrDefault(dimension, 0d),
                                rightByDimension.getOrDefault(dimension, 0d),
                                "dimension " + dimension
                        )
                ))
                .toList();
        return ParameterResult.multiRow(rows);
    }

    private double applyArithmetic(String operator, double left, double right, String context) {
        return switch (operator) {
            case "ADD" -> left + right;
            case "SUBTRACT" -> left - right;
            case "MULTIPLY" -> left * right;
            case "DIVIDE" -> {
                if (Math.abs(right) < EPSILON) {
                    throw new IllegalArgumentException("Division by zero is not allowed for " + context);
                }
                yield left / right;
            }
            default -> throw new IllegalArgumentException("Unsupported formula node type: " + operator);
        };
    }

    private boolean matchesCondition(double leftValue, String operator, double rightValue) {
        String normalized = operator.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case ">", "GT" -> leftValue > rightValue;
            case ">=", "GTE" -> leftValue >= rightValue;
            case "<", "LT" -> leftValue < rightValue;
            case "<=", "LTE" -> leftValue <= rightValue;
            case "=", "==", "EQ" -> Double.compare(leftValue, rightValue) == 0;
            case "!=", "<>", "NE" -> Double.compare(leftValue, rightValue) != 0;
            default -> throw new IllegalArgumentException("Unsupported FILTER.condition.operator: " + operator);
        };
    }

    private ParameterResult toParameterResult(String parameterCode, Object rawValue) {
        if (rawValue == null) {
            return ParameterResult.scalar(0d);
        }

        if (rawValue instanceof Number number) {
            return ParameterResult.scalar(number.doubleValue());
        }

        if (rawValue instanceof String stringValue) {
            String normalized = stringValue.trim();
            if (normalized.isEmpty()) {
                return ParameterResult.scalar(0d);
            }
            try {
                return ParameterResult.scalar(Double.parseDouble(normalized));
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("Parameter " + parameterCode + " returned a non-numeric string value");
            }
        }

        if (rawValue instanceof List<?> rows) {
            return toMultiRowResult(parameterCode, rows);
        }

        throw new IllegalArgumentException("Parameter " + parameterCode + " returned a non-numeric value");
    }

    private ParameterResult toMultiRowResult(String parameterCode, List<?> rawRows) {
        if (rawRows == null || rawRows.isEmpty()) {
            return ParameterResult.multiRow(List.of());
        }

        Map<String, Double> mergedRows = new LinkedHashMap<>();

        for (Object rawRow : rawRows) {
            if (!(rawRow instanceof Map<?, ?> rowMap)) {
                throw new IllegalArgumentException("Parameter " + parameterCode + " returned a non-numeric value");
            }

            String dimensionKey = extractDimensionKey(rowMap);
            if (dimensionKey == null || dimensionKey.isBlank()) {
                continue;
            }

            Object rawRowValue = findByCaseInsensitiveKey(rowMap, "value");
            double rowValue;
            try {
                rowValue = parseNumeric(rawRowValue);
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException(
                        "Parameter " + parameterCode + " returned a non-numeric value for dimension " + dimensionKey
                );
            }

            mergedRows.merge(dimensionKey, rowValue, Double::sum);
        }

        List<RowValue> rows = mergedRows.entrySet().stream()
                .map(entry -> new RowValue(entry.getKey(), entry.getValue()))
                .toList();
        return ParameterResult.multiRow(rows);
    }

    private String extractDimensionKey(Map<?, ?> rowMap) {
        for (String candidate : DIMENSION_KEY_HINTS) {
            Object value = findByCaseInsensitiveKey(rowMap, candidate);
            if (value != null) {
                String normalized = String.valueOf(value).trim();
                if (!normalized.isBlank()) {
                    return normalized;
                }
                return null;
            }
        }

        for (Map.Entry<?, ?> entry : rowMap.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }

            String key = String.valueOf(entry.getKey());
            if ("value".equalsIgnoreCase(key)) {
                continue;
            }

            Object rawDimension = entry.getValue();
            if (rawDimension == null) {
                return null;
            }

            String normalized = String.valueOf(rawDimension).trim();
            return normalized.isBlank() ? null : normalized;
        }

        return null;
    }

    private Object findByCaseInsensitiveKey(Map<?, ?> map, String key) {
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            if (key.equalsIgnoreCase(String.valueOf(entry.getKey()))) {
                return entry.getValue();
            }
        }
        return null;
    }

    private Map<String, Double> toDimensionMap(List<RowValue> rows) {
        Map<String, Double> byDimension = new LinkedHashMap<>();
        for (RowValue row : rows) {
            if (row.dimensionKey() == null || row.dimensionKey().isBlank()) {
                continue;
            }
            byDimension.put(row.dimensionKey(), safeNumber(row.value()));
        }
        return byDimension;
    }

    private double parseNumeric(Object rawValue) {
        if (rawValue == null) {
            return 0d;
        }
        if (rawValue instanceof Number number) {
            return number.doubleValue();
        }
        if (rawValue instanceof String stringValue) {
            String normalized = stringValue.trim();
            if (normalized.isBlank()) {
                return 0d;
            }
            return Double.parseDouble(normalized);
        }
        throw new NumberFormatException("Unsupported numeric value type");
    }

    private double safeNumber(Double value) {
        return value == null ? 0d : value;
    }

    private double evaluateConstantNode(ExpressionNode node) {
        if (!(node instanceof ConstantNode constantNode) || constantNode.getValue() == null) {
            throw new IllegalArgumentException("CONSTANT node requires a numeric value");
        }
        return constantNode.getValue();
    }

    private static final class EvaluationContext {

        private final Map<String, ParameterResult> parameterCache = new LinkedHashMap<>();
        private final Map<String, Double> scalarCache = new LinkedHashMap<>();
        private final Map<String, Integer> parameterEvaluationCount = new LinkedHashMap<>();
        private int scalarCacheMisses = 0;
    }
}
