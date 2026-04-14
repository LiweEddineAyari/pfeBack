package projet.app.engine.ast;

import java.util.List;

public record FormulaDefinition(
        FormulaNode expression,
        FilterGroupNode whereFilter,
        List<String> groupByFields
) {

    public FormulaDefinition {
        if (expression == null) {
            throw new IllegalArgumentException("Formula expression is required");
        }
        groupByFields = groupByFields == null ? List.of() : List.copyOf(groupByFields);
    }
}
