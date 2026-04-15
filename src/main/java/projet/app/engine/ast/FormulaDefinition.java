package projet.app.engine.ast;

import java.util.List;

public record FormulaDefinition(
        FormulaNode expression,
        FilterGroupNode whereFilter,
        List<String> groupByFields,
        List<OrderByNode> orderBy,
        Integer limit,
        Integer top
) {

    public FormulaDefinition {
        if (expression == null) {
            throw new IllegalArgumentException("Formula expression is required");
        }
        groupByFields = groupByFields == null ? List.of() : List.copyOf(groupByFields);
        orderBy = orderBy == null ? List.of() : List.copyOf(orderBy);
    }

    public FormulaDefinition(FormulaNode expression, FilterGroupNode whereFilter, List<String> groupByFields) {
        this(expression, whereFilter, groupByFields, List.of(), null, null);
    }
}
