package projet.app.engine.ast;

import projet.app.engine.enums.FilterLogic;

import java.util.List;

public record FilterGroupNode(
        FilterLogic logic,
        List<FilterConditionNode> conditions,
        List<FilterGroupNode> groups
) {

    public FilterGroupNode {
        logic = logic == null ? FilterLogic.AND : logic;
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
        groups = groups == null ? List.of() : List.copyOf(groups);
    }

    public boolean isEmpty() {
        return conditions.isEmpty() && groups.isEmpty();
    }
}
