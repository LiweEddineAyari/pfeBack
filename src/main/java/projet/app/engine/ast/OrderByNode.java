package projet.app.engine.ast;

import projet.app.engine.enums.SortDirection;

public record OrderByNode(
        String field,
        SortDirection direction
) {

    public OrderByNode {
        if (field == null || field.isBlank()) {
            throw new IllegalArgumentException("Order by field is required");
        }
        direction = direction == null ? SortDirection.ASC : direction;
    }
}
