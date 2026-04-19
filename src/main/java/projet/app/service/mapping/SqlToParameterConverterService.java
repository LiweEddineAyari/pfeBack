package projet.app.service.mapping;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.DateValue;
import net.sf.jsqlparser.expression.DoubleValue;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.NullValue;
import net.sf.jsqlparser.expression.Parenthesis;
import net.sf.jsqlparser.expression.SignedExpression;
import net.sf.jsqlparser.expression.StringValue;
import net.sf.jsqlparser.expression.TimeValue;
import net.sf.jsqlparser.expression.TimestampValue;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.conditional.OrExpression;
import net.sf.jsqlparser.expression.operators.relational.Between;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.expression.operators.relational.GreaterThan;
import net.sf.jsqlparser.expression.operators.relational.GreaterThanEquals;
import net.sf.jsqlparser.expression.operators.relational.InExpression;
import net.sf.jsqlparser.expression.operators.relational.IsNullExpression;
import net.sf.jsqlparser.expression.operators.relational.LikeExpression;
import net.sf.jsqlparser.expression.operators.relational.MinorThan;
import net.sf.jsqlparser.expression.operators.relational.MinorThanEquals;
import net.sf.jsqlparser.expression.operators.relational.NotEqualsTo;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.Statements;
import net.sf.jsqlparser.statement.select.GroupByElement;
import net.sf.jsqlparser.statement.select.Join;
import net.sf.jsqlparser.statement.select.Limit;
import net.sf.jsqlparser.statement.select.OrderByElement;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SelectItem;
import net.sf.jsqlparser.statement.select.SetOperationList;
import net.sf.jsqlparser.statement.select.Top;
import org.springframework.stereotype.Service;
import projet.app.engine.registry.FieldRegistry;
import projet.app.exception.InvalidSqlException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class SqlToParameterConverterService {

    private static final String INVALID_SQL_MESSAGE = "Unsupported SQL structure";
    private static final String BASE_TABLE = "fact_balance";
    private static final Set<String> ALLOWED_AGGREGATIONS = Set.of("SUM", "AVG", "COUNT", "MIN", "MAX");
    private static final Set<String> ALLOWED_JOIN_TABLES = Set.of("dim_client", "dim_contrat");

    private final FieldRegistry fieldRegistry;
    private final ObjectMapper objectMapper;

    public SqlToParameterConverterService(FieldRegistry fieldRegistry, ObjectMapper objectMapper) {
        this.fieldRegistry = fieldRegistry;
        this.objectMapper = objectMapper;
    }

    public JsonNode convertToFormula(String nativeSql) {
        if (nativeSql == null || nativeSql.isBlank()) {
            throw invalidSql("nativeSql is required");
        }

        String trimmedSql = nativeSql.trim();
        if (!trimmedSql.toUpperCase(Locale.ROOT).startsWith("SELECT")) {
            throw invalidSql("SQL must start with SELECT");
        }

        Statement statement = parseSingleStatement(trimmedSql);
        if (!(statement instanceof Select select)) {
            throw invalidSql("Only SELECT statements are supported");
        }

        Select selectBody = select.getSelectBody();
        if (selectBody instanceof SetOperationList) {
            throw invalidSql("UNION and multiple SELECT statements are not supported");
        }

        if (!(selectBody instanceof PlainSelect plainSelect)) {
            throw invalidSql("Unsupported SQL structure");
        }

        validateStructure(plainSelect);
        validateFromAndJoins(plainSelect);

        ParsedAggregation aggregation = parseSelectAggregation(plainSelect);
        FilterGroupModel where = parseWhere(plainSelect.getWhere());
        List<String> groupBy = parseGroupBy(plainSelect);
        List<OrderByModel> orderBy = parseOrderBy(plainSelect.getOrderByElements(), aggregation);
        Integer limit = parseLimit(plainSelect.getLimit());
        Integer top = parseTop(plainSelect.getTop());

        if (limit != null && top != null) {
            throw invalidSql("Cannot use LIMIT and TOP together");
        }

        ParsedSqlModel parsed = new ParsedSqlModel(aggregation, where, groupBy, orderBy, limit, top);
        return toFormulaJson(parsed);
    }

    private Statement parseSingleStatement(String sql) {
        try {
            Statements statements = CCJSqlParserUtil.parseStatements(sql);
            if (statements == null || statements.size() != 1) {
                throw invalidSql("Exactly one SQL statement is required");
            }
            return statements.get(0);
        } catch (JSQLParserException ex) {
            throw invalidSql("SQL parsing failed: " + ex.getMessage());
        }
    }

    private void validateStructure(PlainSelect plainSelect) {
        if (plainSelect.getHaving() != null) {
            throw invalidSql("HAVING is not supported");
        }

        if (plainSelect.getDistinct() != null) {
            throw invalidSql("SELECT DISTINCT is not supported");
        }

        if (plainSelect.getIntoTables() != null && !plainSelect.getIntoTables().isEmpty()) {
            throw invalidSql("SELECT INTO is not supported");
        }
    }

    private void validateFromAndJoins(PlainSelect plainSelect) {
        if (!(plainSelect.getFromItem() instanceof Table baseTable)) {
            throw invalidSql("FROM must reference a concrete fact table");
        }

        String baseName = normalizeIdentifier(baseTable.getName());
        if (!BASE_TABLE.equals(baseName)) {
            throw invalidSql("FROM table must be fact_balance");
        }

        List<Join> joins = plainSelect.getJoins();
        if (joins == null || joins.isEmpty()) {
            return;
        }

        for (Join join : joins) {
            if (!(join.getRightItem() instanceof Table table)) {
                throw invalidSql("Subqueries in JOIN are not allowed");
            }

            String joinTable = normalizeIdentifier(table.getName());
            if (!isAllowedJoinTable(joinTable)) {
                throw invalidSql("Unsupported JOIN table: " + table.getName());
            }
        }
    }

    private ParsedAggregation parseSelectAggregation(PlainSelect plainSelect) {
        List<SelectItem<?>> selectItems = plainSelect.getSelectItems();
        if (selectItems == null || selectItems.size() != 1) {
            throw invalidSql("SELECT must contain exactly one aggregation expression");
        }

        SelectItem<?> selectItem = selectItems.get(0);
        Expression expression = selectItem.getExpression();
        if (!(expression instanceof Function function)) {
            throw invalidSql("SELECT must contain an aggregation function");
        }

        return parseAggregationFunction(function, "SELECT");
    }

    private ParsedAggregation parseAggregationFunction(Function function, String location) {
        String functionName = normalizeFunctionName(function.getName());
        if (!ALLOWED_AGGREGATIONS.contains(functionName)) {
            throw invalidSql("Unsupported aggregation function in " + location + ": " + function.getName());
        }

        if (function.isAllColumns()) {
            if (!"COUNT".equals(functionName)) {
                throw invalidSql(functionName + "(*) is not supported");
            }
            return new ParsedAggregation(functionName, null, function.isDistinct());
        }

        ExpressionList<?> parameters = function.getParameters();
        if (parameters == null || parameters.size() != 1) {
            throw invalidSql("Aggregation function in " + location + " must have exactly one argument");
        }

        Object argumentItem = parameters.get(0);
        if (!(argumentItem instanceof Expression argument)) {
            throw invalidSql("Aggregation function argument in " + location + " is invalid");
        }

        if (!(argument instanceof Column column)) {
            throw invalidSql("Aggregation function argument in " + location + " must be a field reference");
        }

        String field = resolveFieldName(column);
        return new ParsedAggregation(functionName, field, function.isDistinct());
    }

    private FilterGroupModel parseWhere(Expression whereExpression) {
        if (whereExpression == null) {
            return null;
        }

        if (whereExpression instanceof Parenthesis parenthesis) {
            return parseWhere(parenthesis.getExpression());
        }

        if (whereExpression instanceof AndExpression andExpression) {
            return combineGroups("AND", parseWhere(andExpression.getLeftExpression()), parseWhere(andExpression.getRightExpression()));
        }

        if (whereExpression instanceof OrExpression orExpression) {
            return combineGroups("OR", parseWhere(orExpression.getLeftExpression()), parseWhere(orExpression.getRightExpression()));
        }

        return singleConditionGroup(parseCondition(whereExpression));
    }

    private FilterGroupModel combineGroups(String logic, FilterGroupModel left, FilterGroupModel right) {
        List<ObjectNode> conditions = new ArrayList<>();
        List<FilterGroupModel> groups = new ArrayList<>();
        appendGroup(logic, left, conditions, groups);
        appendGroup(logic, right, conditions, groups);
        return new FilterGroupModel(logic, conditions, groups);
    }

    private void appendGroup(
            String targetLogic,
            FilterGroupModel group,
            List<ObjectNode> conditions,
            List<FilterGroupModel> groups
    ) {
        if (group == null) {
            return;
        }

        if (targetLogic.equalsIgnoreCase(group.logic())) {
            conditions.addAll(group.conditions());
            groups.addAll(group.groups());
            return;
        }

        groups.add(group);
    }

    private FilterGroupModel singleConditionGroup(ObjectNode condition) {
        return new FilterGroupModel("AND", List.of(condition), List.of());
    }

    private ObjectNode parseCondition(Expression expression) {
        if (expression instanceof EqualsTo equalsTo) {
            return conditionWithValue(equalsTo.getLeftExpression(), "=", equalsTo.getRightExpression());
        }
        if (expression instanceof NotEqualsTo notEqualsTo) {
            return conditionWithValue(notEqualsTo.getLeftExpression(), "!=", notEqualsTo.getRightExpression());
        }
        if (expression instanceof GreaterThan greaterThan) {
            return conditionWithValue(greaterThan.getLeftExpression(), ">", greaterThan.getRightExpression());
        }
        if (expression instanceof GreaterThanEquals greaterThanEquals) {
            return conditionWithValue(greaterThanEquals.getLeftExpression(), ">=", greaterThanEquals.getRightExpression());
        }
        if (expression instanceof MinorThan minorThan) {
            return conditionWithValue(minorThan.getLeftExpression(), "<", minorThan.getRightExpression());
        }
        if (expression instanceof MinorThanEquals minorThanEquals) {
            return conditionWithValue(minorThanEquals.getLeftExpression(), "<=", minorThanEquals.getRightExpression());
        }
        if (expression instanceof LikeExpression likeExpression) {
            if (likeExpression.isNot()) {
                throw invalidSql("NOT LIKE is not supported");
            }
            return conditionWithValue(likeExpression.getLeftExpression(), "LIKE", likeExpression.getRightExpression());
        }
        if (expression instanceof IsNullExpression isNullExpression) {
            String field = resolveFieldName(isNullExpression.getLeftExpression());
            ObjectNode condition = objectMapper.createObjectNode();
            condition.put("field", field);
            condition.put("operator", isNullExpression.isNot() ? "IS NOT NULL" : "IS NULL");
            return condition;
        }
        if (expression instanceof Between between) {
            if (between.isNot()) {
                throw invalidSql("NOT BETWEEN is not supported");
            }

            String field = resolveFieldName(between.getLeftExpression());
            ObjectNode condition = objectMapper.createObjectNode();
            condition.put("field", field);
            condition.put("operator", "BETWEEN");

            ArrayNode values = objectMapper.createArrayNode();
            values.add(objectMapper.valueToTree(parseLiteralValue(between.getBetweenExpressionStart(), "BETWEEN start")));
            values.add(objectMapper.valueToTree(parseLiteralValue(between.getBetweenExpressionEnd(), "BETWEEN end")));
            condition.set("value", values);
            return condition;
        }
        if (expression instanceof InExpression inExpression) {
            String field = resolveFieldName(inExpression.getLeftExpression());
            Expression rightExpression = inExpression.getRightExpression();

            if (!(rightExpression instanceof ExpressionList<?> expressionList)) {
                if (rightExpression != null && rightExpression.toString().toUpperCase(Locale.ROOT).contains("SELECT")) {
                    throw invalidSql("Subqueries are not allowed");
                }
                throw invalidSql("IN clause must contain a list of literal values");
            }

            if (expressionList.isEmpty()) {
                throw invalidSql("IN requires at least one value");
            }

            ObjectNode condition = objectMapper.createObjectNode();
            condition.put("field", field);
            condition.put("operator", inExpression.isNot() ? "NOT IN" : "IN");

            ArrayNode values = objectMapper.createArrayNode();
            for (Object item : expressionList) {
                if (!(item instanceof Expression valueExpression)) {
                    throw invalidSql("Unsupported IN value");
                }
                values.add(objectMapper.valueToTree(parseLiteralValue(valueExpression, "IN value")));
            }
            condition.set("value", values);
            return condition;
        }

        String asText = expression.toString().toUpperCase(Locale.ROOT);
        if (asText.contains("SELECT")) {
            throw invalidSql("Subqueries are not allowed");
        }

        throw invalidSql("Unsupported WHERE condition: " + expression);
    }

    private ObjectNode conditionWithValue(Expression left, String operator, Expression right) {
        String field = resolveFieldName(left);
        Object value = parseLiteralValue(right, "condition value");

        ObjectNode condition = objectMapper.createObjectNode();
        condition.put("field", field);
        condition.put("operator", operator);
        condition.set("value", objectMapper.valueToTree(value));
        return condition;
    }

    private List<String> parseGroupBy(PlainSelect plainSelect) {
        GroupByElement groupByElement = plainSelect.getGroupBy();
        ExpressionList<?> groupByExpressions = groupByElement == null ? null : groupByElement.getGroupByExpressionList();
        if (groupByExpressions == null || groupByExpressions.isEmpty()) {
            return List.of();
        }

        List<String> fields = new ArrayList<>();
        for (Object groupByItem : groupByExpressions) {
            if (!(groupByItem instanceof Expression groupByExpression)) {
                throw invalidSql("Unsupported GROUP BY expression");
            }
            fields.add(resolveFieldName(groupByExpression));
        }

        return deduplicatePreservingOrder(fields);
    }

    private List<OrderByModel> parseOrderBy(List<OrderByElement> orderByElements, ParsedAggregation selectAggregation) {
        if (orderByElements == null || orderByElements.isEmpty()) {
            return List.of();
        }

        List<OrderByModel> orderBy = new ArrayList<>();
        for (OrderByElement element : orderByElements) {
            String direction = element.isAscDescPresent() && !element.isAsc() ? "DESC" : "ASC";
            Expression expression = element.getExpression();

            if (expression instanceof Function function) {
                ParsedAggregation orderAggregation = parseAggregationFunction(function, "ORDER BY");
                if (!selectAggregation.equals(orderAggregation)) {
                    throw invalidSql("ORDER BY aggregation must match SELECT aggregation");
                }
                orderBy.add(new OrderByModel("value", direction));
                continue;
            }

            if (expression instanceof Column column
                    && "value".equalsIgnoreCase(safeTrim(column.getColumnName()))) {
                orderBy.add(new OrderByModel("value", direction));
                continue;
            }

            orderBy.add(new OrderByModel(resolveFieldName(expression), direction));
        }

        return orderBy;
    }

    private Integer parseLimit(Limit limit) {
        if (limit == null || limit.getRowCount() == null) {
            return null;
        }

        return parsePositiveInteger(limit.getRowCount(), "LIMIT");
    }

    private Integer parseTop(Top top) {
        if (top == null || top.getExpression() == null) {
            return null;
        }

        return parsePositiveInteger(top.getExpression(), "TOP");
    }

    private Integer parsePositiveInteger(Expression expression, String keyword) {
        if (expression instanceof LongValue longValue) {
            long value = longValue.getValue();
            if (value <= 0 || value > Integer.MAX_VALUE) {
                throw invalidSql(keyword + " value must be a positive integer");
            }
            return (int) value;
        }

        if (expression instanceof SignedExpression signedExpression && signedExpression.getExpression() instanceof LongValue longValue) {
            long signedValue = signedExpression.getSign() == '-' ? -longValue.getValue() : longValue.getValue();
            if (signedValue <= 0 || signedValue > Integer.MAX_VALUE) {
                throw invalidSql(keyword + " value must be a positive integer");
            }
            return (int) signedValue;
        }

        throw invalidSql(keyword + " value must be a positive integer literal");
    }

    private Object parseLiteralValue(Expression expression, String context) {
        if (expression instanceof Parenthesis parenthesis) {
            return parseLiteralValue(parenthesis.getExpression(), context);
        }

        if (expression instanceof StringValue stringValue) {
            return stringValue.getValue();
        }

        if (expression instanceof LongValue longValue) {
            return longValue.getValue();
        }

        if (expression instanceof DoubleValue doubleValue) {
            return BigDecimal.valueOf(doubleValue.getValue());
        }

        if (expression instanceof DateValue dateValue) {
            LocalDate date = dateValue.getValue().toLocalDate();
            return date.toString();
        }

        if (expression instanceof TimeValue timeValue) {
            LocalTime time = timeValue.getValue().toLocalTime();
            return time.toString();
        }

        if (expression instanceof TimestampValue timestampValue) {
            LocalDateTime timestamp = timestampValue.getValue().toLocalDateTime();
            return timestamp.toString();
        }

        if (expression instanceof NullValue) {
            return null;
        }

        if (expression instanceof SignedExpression signedExpression) {
            Object nested = parseLiteralValue(signedExpression.getExpression(), context);
            if (nested instanceof Long longValue) {
                return signedExpression.getSign() == '-' ? -longValue : longValue;
            }
            if (nested instanceof BigDecimal decimalValue) {
                return signedExpression.getSign() == '-' ? decimalValue.negate() : decimalValue;
            }
            throw invalidSql("Unsupported signed literal in " + context);
        }

        if (expression instanceof Column) {
            throw invalidSql("Column references are not allowed as literal values in " + context);
        }

        String asText = expression.toString().toUpperCase(Locale.ROOT);
        if (asText.contains("SELECT")) {
            throw invalidSql("Subqueries are not allowed");
        }

        throw invalidSql("Unsupported literal value in " + context + ": " + expression);
    }

    private String resolveFieldName(Expression expression) {
        if (!(expression instanceof Column column)) {
            throw invalidSql("Expected a field reference but got: " + expression);
        }

        List<String> candidates = new ArrayList<>();
        String fullName = safeTrim(column.getFullyQualifiedName());
        if (!fullName.isBlank()) {
            candidates.add(fullName);
        }

        String columnName = safeTrim(column.getColumnName());
        if (!columnName.isBlank()) {
            candidates.add(columnName);
        }

        String normalizedColumnName = normalizeIdentifier(columnName);
        if (!normalizedColumnName.isBlank()) {
            candidates.add(normalizedColumnName);
        }

        if (column.getTable() != null) {
            Table table = column.getTable();
            String tableName = safeTrim(table.getName());
            if (!tableName.isBlank() && !columnName.isBlank()) {
                candidates.add(tableName + "." + columnName);
            }
            if (table.getAlias() != null) {
                String alias = safeTrim(table.getAlias().getName());
                if (!alias.isBlank() && !columnName.isBlank()) {
                    candidates.add(alias + "." + columnName);
                }
            }
        }

        for (String candidate : candidates) {
            String canonical = resolveKnownField(candidate);
            if (canonical != null) {
                return canonical;
            }
        }

        throw invalidSql("Unknown field: " + column);
    }

    private String resolveKnownField(String candidate) {
        if (candidate == null || candidate.isBlank()) {
            return null;
        }

        String trimmed = candidate.trim();
        if (fieldRegistry.exists(trimmed)) {
            return fieldRegistry.resolve(trimmed).fieldName();
        }

        String sanitized = sanitizeIdentifier(trimmed);
        if (fieldRegistry.exists(sanitized)) {
            return fieldRegistry.resolve(sanitized).fieldName();
        }

        int lastDot = sanitized.lastIndexOf('.');
        if (lastDot > -1) {
            String unqualified = sanitized.substring(lastDot + 1);
            if (fieldRegistry.exists(unqualified)) {
                return fieldRegistry.resolve(unqualified).fieldName();
            }
        }

        return null;
    }

    private JsonNode toFormulaJson(ParsedSqlModel parsed) {
        ObjectNode formula = objectMapper.createObjectNode();

        ObjectNode expression = objectMapper.createObjectNode();
        expression.put("type", "AGGREGATION");
        expression.put("function", parsed.aggregation().function());
        if (parsed.aggregation().field() != null) {
            expression.put("field", parsed.aggregation().field());
        }
        if (parsed.aggregation().distinct()) {
            expression.put("distinct", true);
        }
        formula.set("expression", expression);

        if (parsed.where() != null) {
            formula.set("where", toFilterJson(parsed.where()));
        }

        if (!parsed.groupBy().isEmpty()) {
            ArrayNode groupBy = objectMapper.createArrayNode();
            parsed.groupBy().forEach(groupBy::add);
            formula.set("groupBy", groupBy);
        }

        if (!parsed.orderBy().isEmpty()) {
            ArrayNode orderBy = objectMapper.createArrayNode();
            for (OrderByModel item : parsed.orderBy()) {
                ObjectNode order = objectMapper.createObjectNode();
                order.put("field", item.field());
                order.put("direction", item.direction());
                orderBy.add(order);
            }
            formula.set("orderBy", orderBy);
        }

        if (parsed.limit() != null) {
            formula.put("limit", parsed.limit());
        }

        if (parsed.top() != null) {
            formula.put("top", parsed.top());
        }

        return formula;
    }

    private ObjectNode toFilterJson(FilterGroupModel group) {
        ObjectNode json = objectMapper.createObjectNode();
        json.put("logic", group.logic());

        if (!group.conditions().isEmpty()) {
            ArrayNode conditions = objectMapper.createArrayNode();
            group.conditions().forEach(conditions::add);
            json.set("conditions", conditions);
        }

        if (!group.groups().isEmpty()) {
            ArrayNode groups = objectMapper.createArrayNode();
            for (FilterGroupModel nested : group.groups()) {
                groups.add(toFilterJson(nested));
            }
            json.set("groups", groups);
        }

        return json;
    }

    private List<String> deduplicatePreservingOrder(List<String> values) {
        LinkedHashSet<String> unique = new LinkedHashSet<>(values);
        return new ArrayList<>(unique);
    }

    private String normalizeFunctionName(String name) {
        return name == null ? "" : name.trim().toUpperCase(Locale.ROOT);
    }

    private boolean isAllowedJoinTable(String joinTable) {
        return ALLOWED_JOIN_TABLES.contains(joinTable) || joinTable.startsWith("sub_dim_");
    }

    private String normalizeIdentifier(String rawIdentifier) {
        return sanitizeIdentifier(rawIdentifier).toLowerCase(Locale.ROOT);
    }

    private String sanitizeIdentifier(String rawIdentifier) {
        if (rawIdentifier == null) {
            return "";
        }
        return rawIdentifier
                .trim()
                .replace("\"", "")
                .replace("`", "")
                .replace("[", "")
                .replace("]", "");
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    private InvalidSqlException invalidSql(String detail) {
        return new InvalidSqlException(INVALID_SQL_MESSAGE, List.of(detail));
    }

    private record ParsedAggregation(String function, String field, boolean distinct) {
    }

    private record FilterGroupModel(String logic, List<ObjectNode> conditions, List<FilterGroupModel> groups) {
    }

    private record OrderByModel(String field, String direction) {
    }

    private record ParsedSqlModel(
            ParsedAggregation aggregation,
            FilterGroupModel where,
            List<String> groupBy,
            List<OrderByModel> orderBy,
            Integer limit,
            Integer top
    ) {
    }
}
