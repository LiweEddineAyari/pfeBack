package projet.app.engine.parser;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
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
import projet.app.engine.enums.FilterLogic;
import projet.app.engine.enums.FilterOperator;
import projet.app.engine.enums.FormulaNodeType;
import projet.app.engine.enums.SortDirection;
import projet.app.exception.FormulaValidationException;

import java.util.ArrayList;
import java.util.List;

@Component
public class FormulaParser {

    public FormulaDefinition parse(JsonNode formulaJson) {
        if (formulaJson == null || formulaJson.isNull() || formulaJson.isMissingNode()) {
            throw new FormulaValidationException(List.of("formula_json must not be null"));
        }

        FormulaNode expression;
        FilterGroupNode whereFilter = null;
        List<String> groupBy = List.of();
        List<OrderByNode> orderBy = List.of();
        Integer limit = null;
        Integer top = null;

        if (formulaJson.isObject() && formulaJson.has("expression") && !formulaJson.has("type")) {
            expression = parseExpression(formulaJson.get("expression"), "expression");
            whereFilter = parseOptionalTopFilter(formulaJson);
            groupBy = parseOptionalGroupBy(formulaJson);
            orderBy = parseOptionalOrderBy(formulaJson);
            limit = parseOptionalInteger(formulaJson, "limit");
            top = parseOptionalInteger(formulaJson, "top");
        } else {
            expression = parseExpression(formulaJson, "root");
            if (formulaJson.isObject()) {
                if (formulaJson.has("where")) {
                    whereFilter = parseFilterGroupFlexible(formulaJson.get("where"), "where");
                } else if (formulaJson.has("filter")) {
                    whereFilter = parseFilterGroupFlexible(formulaJson.get("filter"), "filter");
                }
                groupBy = parseOptionalGroupBy(formulaJson);
                orderBy = parseOptionalOrderBy(formulaJson);
                limit = parseOptionalInteger(formulaJson, "limit");
                top = parseOptionalInteger(formulaJson, "top");
            }
        }

        return new FormulaDefinition(expression, whereFilter, groupBy, orderBy, limit, top);
    }

    private FilterGroupNode parseOptionalTopFilter(JsonNode rootNode) {
        if (rootNode.has("where")) {
            return parseFilterGroupFlexible(rootNode.get("where"), "where");
        }
        if (rootNode.has("filter")) {
            return parseFilterGroupFlexible(rootNode.get("filter"), "filter");
        }
        if (rootNode.has("filters")) {
            return parseFilterGroupFlexible(rootNode.get("filters"), "filters");
        }
        return null;
    }

    private FormulaNode parseExpression(JsonNode node, String path) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            throw new FormulaValidationException(List.of("Missing expression at " + path));
        }
        if (!node.isObject()) {
            throw new FormulaValidationException(List.of("Expression at " + path + " must be a JSON object"));
        }

        FormulaNodeType type = FormulaNodeType.from(readRequiredText(node, "type", path + ".type"));

        return switch (type) {
            case FIELD -> new FieldNode(readRequiredText(node, "field", path + ".field"));
            case VALUE -> new ValueNode(convertJsonValue(readRequiredNode(node, "value", path + ".value"), path + ".value"));
            case AGGREGATION -> parseAggregationNode(node, path);
            case ADD, SUBTRACT, MULTIPLY, DIVIDE -> parseBinaryNode(node, type, path);
        };
    }

    private AggregationNode parseAggregationNode(JsonNode node, String path) {
        AggregationFunction function = AggregationFunction.from(readRequiredText(node, "function", path + ".function"));

        String field = null;
        if (node.has("field") && !node.get("field").isNull()) {
            field = readRequiredText(node, "field", path + ".field");
        }

        FormulaNode expression = null;
        if (node.has("expression") && !node.get("expression").isNull()) {
            expression = parseExpression(node.get("expression"), path + ".expression");
        }

        FilterGroupNode filters = null;
        if (node.has("filters")) {
            filters = parseFilterGroupFlexible(node.get("filters"), path + ".filters");
        }

        boolean distinct = node.has("distinct") && node.get("distinct").isBoolean() && node.get("distinct").asBoolean();

        return new AggregationNode(function, field, expression, filters, distinct);
    }

    private List<String> parseOptionalGroupBy(JsonNode rootNode) {
        if (!rootNode.has("groupBy")) {
            return List.of();
        }

        JsonNode groupByNode = rootNode.get("groupBy");
        if (!groupByNode.isArray()) {
            throw new FormulaValidationException(List.of("groupBy must be an array of field names"));
        }

        List<String> groupByFields = new ArrayList<>();
        for (int i = 0; i < groupByNode.size(); i++) {
            JsonNode item = groupByNode.get(i);
            if (!item.isTextual() || item.asText().isBlank()) {
                throw new FormulaValidationException(List.of("groupBy[" + i + "] must be a non-empty string"));
            }
            groupByFields.add(item.asText().trim());
        }
        return groupByFields;
    }

    private List<OrderByNode> parseOptionalOrderBy(JsonNode rootNode) {
        if (!rootNode.has("orderBy")) {
            return List.of();
        }

        JsonNode orderByNode = rootNode.get("orderBy");
        if (!orderByNode.isArray()) {
            throw new FormulaValidationException(List.of("orderBy must be an array"));
        }

        List<OrderByNode> orderByItems = new ArrayList<>();
        for (int i = 0; i < orderByNode.size(); i++) {
            JsonNode item = orderByNode.get(i);
            String itemPath = "orderBy[" + i + "]";

            if (item.isTextual()) {
                if (item.asText().isBlank()) {
                    throw new FormulaValidationException(List.of(itemPath + " must be a non-empty string"));
                }
                orderByItems.add(new OrderByNode(item.asText().trim(), SortDirection.ASC));
                continue;
            }

            if (!item.isObject()) {
                throw new FormulaValidationException(List.of(itemPath + " must be a string or object"));
            }

            String field = readRequiredText(item, "field", itemPath + ".field");
            SortDirection direction = item.has("direction")
                    ? SortDirection.from(readRequiredText(item, "direction", itemPath + ".direction"))
                    : SortDirection.ASC;

            orderByItems.add(new OrderByNode(field, direction));
        }
        return orderByItems;
    }

    private Integer parseOptionalInteger(JsonNode rootNode, String fieldName) {
        if (!rootNode.has(fieldName)) {
            return null;
        }

        JsonNode valueNode = rootNode.get(fieldName);
        if (!valueNode.isIntegralNumber()) {
            throw new FormulaValidationException(List.of(fieldName + " must be an integer"));
        }

        long value = valueNode.longValue();
        if (value > Integer.MAX_VALUE || value < Integer.MIN_VALUE) {
            throw new FormulaValidationException(List.of(fieldName + " is out of integer range"));
        }

        return (int) value;
    }

    private BinaryOperationNode parseBinaryNode(JsonNode node, FormulaNodeType type, String path) {
        FormulaNode left = parseExpression(readRequiredNode(node, "left", path + ".left"), path + ".left");
        FormulaNode right = parseExpression(readRequiredNode(node, "right", path + ".right"), path + ".right");
        ArithmeticOperator operator = ArithmeticOperator.from(type.name());
        return new BinaryOperationNode(operator, left, right);
    }

    private FilterGroupNode parseFilterGroupFlexible(JsonNode node, String path) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }

        if (node.isArray()) {
            List<FilterConditionNode> conditions = new ArrayList<>();
            List<FilterGroupNode> groups = new ArrayList<>();

            for (int i = 0; i < node.size(); i++) {
                JsonNode item = node.get(i);
                String itemPath = path + "[" + i + "]";
                if (isConditionObject(item)) {
                    conditions.add(parseCondition(item, itemPath));
                } else {
                    groups.add(parseFilterGroupFlexible(item, itemPath));
                }
            }

            return new FilterGroupNode(FilterLogic.AND, conditions, groups);
        }

        if (!node.isObject()) {
            throw new FormulaValidationException(List.of("Filter group at " + path + " must be an object or an array"));
        }

        if (isConditionObject(node)) {
            return new FilterGroupNode(
                    FilterLogic.AND,
                    List.of(parseCondition(node, path)),
                    List.of()
            );
        }

        FilterLogic logic = node.has("logic")
                ? FilterLogic.from(node.get("logic").asText())
                : FilterLogic.AND;

        List<FilterConditionNode> conditions = new ArrayList<>();
        List<FilterGroupNode> groups = new ArrayList<>();

        if (node.has("conditions")) {
            JsonNode conditionsNode = node.get("conditions");
            if (!conditionsNode.isArray()) {
                throw new FormulaValidationException(List.of("conditions at " + path + " must be an array"));
            }
            for (int i = 0; i < conditionsNode.size(); i++) {
                JsonNode conditionNode = conditionsNode.get(i);
                conditions.add(parseCondition(conditionNode, path + ".conditions[" + i + "]"));
            }
        }

        if (node.has("groups")) {
            JsonNode groupsNode = node.get("groups");
            if (!groupsNode.isArray()) {
                throw new FormulaValidationException(List.of("groups at " + path + " must be an array"));
            }
            for (int i = 0; i < groupsNode.size(); i++) {
                groups.add(parseFilterGroupFlexible(groupsNode.get(i), path + ".groups[" + i + "]"));
            }
        }

        if (conditions.isEmpty() && groups.isEmpty()) {
            throw new FormulaValidationException(List.of("Empty filter group at " + path));
        }

        return new FilterGroupNode(logic, conditions, groups);
    }

    private FilterConditionNode parseCondition(JsonNode node, String path) {
        if (node == null || !node.isObject()) {
            throw new FormulaValidationException(List.of("Filter condition at " + path + " must be a JSON object"));
        }

        String field = readRequiredText(node, "field", path + ".field");
        FilterOperator operator = FilterOperator.from(readRequiredText(node, "operator", path + ".operator"));

        Object value = null;
        if (operator.requiresValue()) {
            JsonNode valueNode;
            if (node.has("value")) {
                valueNode = node.get("value");
            } else if (node.has("values")) {
                valueNode = node.get("values");
            } else {
                throw new FormulaValidationException(List.of("Missing value for operator " + operator + " at " + path));
            }
            value = convertJsonValue(valueNode, path + ".value");
        }

        return new FilterConditionNode(field, operator, value);
    }

    private boolean isConditionObject(JsonNode node) {
        return node != null
                && node.isObject()
                && node.has("field")
                && node.has("operator")
                && !node.has("logic")
                && !node.has("conditions")
                && !node.has("groups");
    }

    private JsonNode readRequiredNode(JsonNode parent, String fieldName, String path) {
        JsonNode value = parent.get(fieldName);
        if (value == null || value.isNull() || value.isMissingNode()) {
            throw new FormulaValidationException(List.of("Missing required node at " + path));
        }
        return value;
    }

    private String readRequiredText(JsonNode parent, String fieldName, String path) {
        JsonNode value = readRequiredNode(parent, fieldName, path);
        if (!value.isTextual() || value.asText().isBlank()) {
            throw new FormulaValidationException(List.of("Field " + path + " must be a non-empty string"));
        }
        return value.asText().trim();
    }

    private Object convertJsonValue(JsonNode node, String path) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        if (node.isTextual()) {
            return node.asText();
        }
        if (node.isIntegralNumber()) {
            return node.longValue();
        }
        if (node.isFloatingPointNumber() || node.isBigDecimal()) {
            return node.decimalValue();
        }
        if (node.isBoolean()) {
            return node.booleanValue();
        }
        if (node.isArray()) {
            List<Object> values = new ArrayList<>();
            for (int i = 0; i < node.size(); i++) {
                values.add(convertJsonValue(node.get(i), path + "[" + i + "]"));
            }
            return values;
        }

        if (node.isObject()) {
            throw new FormulaValidationException(List.of("Object values are not supported at " + path));
        }

        throw new FormulaValidationException(List.of("Unsupported value type at " + path));
    }
}
