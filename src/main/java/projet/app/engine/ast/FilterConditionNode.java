package projet.app.engine.ast;

import projet.app.engine.enums.FilterOperator;

public record FilterConditionNode(
        String field,
        FilterOperator operator,
        Object value
) {

    public FilterConditionNode {
        if (field == null || field.isBlank()) {
            throw new IllegalArgumentException("Filter condition field is required");
        }
        if (operator == null) {
            throw new IllegalArgumentException("Filter condition operator is required");
        }
    }
}
