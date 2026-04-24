package projet.app.engine.validation;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import projet.app.engine.ast.AggregationNode;
import projet.app.engine.ast.BinaryOperationNode;
import projet.app.engine.ast.FieldNode;
import projet.app.engine.ast.FilterConditionNode;
import projet.app.engine.ast.FilterGroupNode;
import projet.app.engine.ast.FormulaDefinition;
import projet.app.engine.ast.FormulaNode;
import projet.app.engine.ast.OrderByNode;
import projet.app.engine.ast.ValueNode;
import projet.app.engine.enums.AggregationFunction;
import projet.app.engine.enums.ArithmeticOperator;
import projet.app.engine.enums.FilterOperator;
import projet.app.engine.enums.FormulaValueType;
import projet.app.engine.parser.FormulaParser;
import projet.app.engine.registry.FieldDefinition;
import projet.app.engine.registry.FieldRegistry;
import projet.app.exception.FormulaValidationException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
public class FormulaValidationService {

    private static final int MAX_NESTING_DEPTH = 5;

    private final FormulaParser formulaParser;
    private final FieldRegistry fieldRegistry;

    public FormulaValidationService(FormulaParser formulaParser, FieldRegistry fieldRegistry) {
        this.formulaParser = formulaParser;
        this.fieldRegistry = fieldRegistry;
    }

    public FormulaDefinition validateAndParse(JsonNode formulaJson) {
        FormulaDefinition formulaDefinition = formulaParser.parse(formulaJson);

        List<String> errors = new ArrayList<>();
        validateExpression(formulaDefinition.expression(), errors, "expression", 1);

        if (formulaDefinition.whereFilter() != null) {
            validateFilterGroup(formulaDefinition.whereFilter(), errors, "where", 1);
        }

        for (int i = 0; i < formulaDefinition.groupByFields().size(); i++) {
            String groupByField = formulaDefinition.groupByFields().get(i);
            if (!fieldRegistry.exists(groupByField)) {
                errors.add("groupBy[" + i + "]: unknown field " + groupByField);
            }
        }

        validateOrderByAndPaging(formulaDefinition, errors);

        if (!errors.isEmpty()) {
            throw new FormulaValidationException(errors);
        }

        return formulaDefinition;
    }

    private FormulaValueType validateExpression(FormulaNode node, List<String> errors, String path, int depth) {
        if (depth > MAX_NESTING_DEPTH) {
            errors.add(path + ": expression nesting depth exceeds max of " + MAX_NESTING_DEPTH);
            return FormulaValueType.UNKNOWN;
        }

        if (node instanceof FieldNode fieldNode) {
            if (!fieldRegistry.exists(fieldNode.field())) {
                errors.add(path + ": unknown field " + fieldNode.field());
                return FormulaValueType.UNKNOWN;
            }
            return fieldRegistry.getFormulaValueType(fieldNode.field());
        }

        if (node instanceof ValueNode valueNode) {
            return inferValueType(valueNode.value());
        }

        if (node instanceof AggregationNode aggregationNode) {
            AggregationFunction function = aggregationNode.function();
            FormulaValueType inputType = FormulaValueType.UNKNOWN;

            if (aggregationNode.field() != null && !aggregationNode.field().isBlank()) {
                if (!fieldRegistry.exists(aggregationNode.field())) {
                    errors.add(path + ": unknown aggregation field " + aggregationNode.field());
                } else {
                    inputType = fieldRegistry.getFormulaValueType(aggregationNode.field());
                }
            }

            if (aggregationNode.expression() != null) {
                inputType = validateExpression(aggregationNode.expression(), errors, path + ".expression", depth + 1);
            }

            if (aggregationNode.field() == null && aggregationNode.expression() == null && function != AggregationFunction.COUNT) {
                errors.add(path + ": aggregation requires field or expression");
            }

            if (function.requiresNumericField() && !inputType.isNumeric()) {
                errors.add(path + ": aggregation " + function + " requires numeric field/expression");
            }

            if (aggregationNode.filters() != null) {
                validateFilterGroup(aggregationNode.filters(), errors, path + ".filters", depth + 1);
            }

            return FormulaValueType.NUMERIC;
        }

        if (node instanceof BinaryOperationNode binaryNode) {
            FormulaValueType leftType = validateExpression(binaryNode.left(), errors, path + ".left", depth + 1);
            FormulaValueType rightType = validateExpression(binaryNode.right(), errors, path + ".right", depth + 1);

            if (!leftType.isNumeric() || !rightType.isNumeric()) {
                errors.add(path + ": arithmetic operation " + binaryNode.operator() + " requires numeric operands");
            }

            if (binaryNode.operator() == ArithmeticOperator.DIVIDE) {
                checkDivisionByZero(binaryNode.right(), errors, path + ".right");
            }
            return FormulaValueType.NUMERIC;
        }

        errors.add(path + ": unsupported expression node " + node.getClass().getSimpleName());
        return FormulaValueType.UNKNOWN;
    }

    private void validateFilterGroup(FilterGroupNode group, List<String> errors, String path, int depth) {
        if (depth > MAX_NESTING_DEPTH) {
            errors.add(path + ": filter nesting depth exceeds max of " + MAX_NESTING_DEPTH);
            return;
        }

        if (group.isEmpty()) {
            errors.add(path + ": filter group must not be empty");
            return;
        }

        for (int i = 0; i < group.conditions().size(); i++) {
            validateFilterCondition(group.conditions().get(i), errors, path + ".conditions[" + i + "]");
        }

        for (int i = 0; i < group.groups().size(); i++) {
            validateFilterGroup(group.groups().get(i), errors, path + ".groups[" + i + "]", depth + 1);
        }
    }

    private void validateFilterCondition(FilterConditionNode condition, List<String> errors, String path) {
        if (!fieldRegistry.exists(condition.field())) {
            errors.add(path + ": unknown filter field " + condition.field());
            return;
        }

        FormulaValueType fieldType = fieldRegistry.getFormulaValueType(condition.field());
        FilterOperator operator = condition.operator();
        Object value = condition.value();

        validateOperatorCompatibility(fieldType, operator, errors, path);

        if (!operator.requiresValue()) {
            return;
        }

        if (value == null) {
            errors.add(path + ": operator " + operator + " requires a non-null value");
            return;
        }

        if (operator.expectsCollectionValue()) {
            if (!(value instanceof List<?> values) || values.isEmpty()) {
                errors.add(path + ": operator " + operator + " requires a non-empty array value");
                return;
            }
            for (Object item : values) {
                validateValueTypeCompatibility(fieldType, item, errors, path);
            }
            return;
        }

        if (operator.expectsRangeValue()) {
            if (!(value instanceof List<?> values) || values.size() != 2) {
                errors.add(path + ": BETWEEN requires exactly two values");
                return;
            }
            validateValueTypeCompatibility(fieldType, values.get(0), errors, path + "[0]");
            validateValueTypeCompatibility(fieldType, values.get(1), errors, path + "[1]");
            return;
        }

        validateValueTypeCompatibility(fieldType, value, errors, path);
    }

    private void validateValueTypeCompatibility(FormulaValueType fieldType, Object value, List<String> errors, String path) {
        if (value == null || fieldType == FormulaValueType.UNKNOWN) {
            return;
        }

        switch (fieldType) {
            case NUMERIC -> {
                if (!(value instanceof Number || value instanceof BigDecimal)) {
                    errors.add(path + ": numeric field expects numeric value, got " + value.getClass().getSimpleName());
                }
            }
            case BOOLEAN -> {
                if (!(value instanceof Boolean)) {
                    errors.add(path + ": boolean field expects boolean value");
                }
            }
            case STRING -> {
                if (!(value instanceof String)) {
                    errors.add(path + ": string field expects string value");
                }
            }
            case DATE, DATETIME -> {
                if (!(value instanceof String)) {
                    errors.add(path + ": date/datetime field expects ISO string value");
                }
            }
            default -> {
                // No strict check for UNKNOWN.
            }
        }
    }

    private void validateOperatorCompatibility(
            FormulaValueType fieldType,
            FilterOperator operator,
            List<String> errors,
            String path
    ) {
        if ((operator == FilterOperator.LIKE
                || operator == FilterOperator.STARTS_WITH
                || operator == FilterOperator.ENDS_WITH
                || operator == FilterOperator.CONTAINS)
                && fieldType != FormulaValueType.STRING) {
            errors.add(path + ": operator " + operator + " is only allowed on string fields");
            return;
        }

        if ((operator == FilterOperator.GT
                || operator == FilterOperator.GTE
                || operator == FilterOperator.LT
                || operator == FilterOperator.LTE
                || operator == FilterOperator.BETWEEN)
                && !(fieldType == FormulaValueType.NUMERIC
                || fieldType == FormulaValueType.DATE
                || fieldType == FormulaValueType.DATETIME)) {
            errors.add(path + ": operator " + operator + " is only allowed on numeric/date fields");
        }

        if (operator == FilterOperator.IN && fieldType == FormulaValueType.BOOLEAN) {
            errors.add(path + ": IN operator is not supported for boolean fields");
        }
    }

    private void checkDivisionByZero(FormulaNode denominatorNode, List<String> errors, String path) {
        if (denominatorNode instanceof ValueNode valueNode) {
            Object value = valueNode.value();
            if (value instanceof Number number && BigDecimal.ZERO.compareTo(new BigDecimal(number.toString())) == 0) {
                errors.add(path + ": division by zero is not allowed");
            }
        }
    }

    private FormulaValueType inferValueType(Object value) {
        if (value == null) {
            return FormulaValueType.UNKNOWN;
        }
        if (value instanceof Number || value instanceof BigDecimal) {
            return FormulaValueType.NUMERIC;
        }
        if (value instanceof Boolean) {
            return FormulaValueType.BOOLEAN;
        }
        return FormulaValueType.STRING;
    }

    private void validateOrderByAndPaging(FormulaDefinition definition, List<String> errors) {
        List<OrderByNode> orderBy = definition.orderBy();
        List<String> groupByFields = definition.groupByFields();

        for (int i = 0; i < orderBy.size(); i++) {
            OrderByNode orderByNode = orderBy.get(i);
            String orderField = orderByNode.field();

            if (isValueAlias(orderField)) {
                continue;
            }

            if (!fieldRegistry.exists(orderField)) {
                errors.add("orderBy[" + i + "]: unknown field " + orderField);
                continue;
            }

            FieldDefinition orderFieldDefinition = fieldRegistry.resolve(orderField);

            if (!groupByFields.isEmpty() && !containsEquivalentGroupByField(groupByFields, orderFieldDefinition.fieldName())) {
                errors.add("orderBy[" + i + "]: field " + orderField + " must also be present in groupBy when grouping is used");
            }
        }

        Integer limit = definition.limit();
        Integer top = definition.top();

        if (limit != null && top != null) {
            errors.add("limit and top cannot both be defined");
        }

        if (limit != null && limit <= 0) {
            errors.add("limit must be a positive integer");
        }

        if (top != null && top <= 0) {
            errors.add("top must be a positive integer");
        }

        if ((limit != null || top != null) && orderBy.isEmpty()) {
            errors.add("orderBy is required when limit or top is provided");
        }
    }

    private boolean containsEquivalentGroupByField(List<String> groupByFields, String targetCanonicalField) {
        for (String groupByField : groupByFields) {
            if (!fieldRegistry.exists(groupByField)) {
                continue;
            }

            FieldDefinition groupByDefinition = fieldRegistry.resolve(groupByField);
            if (groupByDefinition.fieldName().equalsIgnoreCase(targetCanonicalField)) {
                return true;
            }
        }
        return false;
    }

    private boolean isValueAlias(String field) {
        return field != null && "value".equalsIgnoreCase(field.trim());
    }
}
