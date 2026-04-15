package projet.app.engine.compiler;

import org.springframework.stereotype.Service;
import projet.app.engine.ast.AggregationNode;
import projet.app.engine.ast.BinaryOperationNode;
import projet.app.engine.ast.FieldNode;
import projet.app.engine.ast.FilterConditionNode;
import projet.app.engine.ast.FilterGroupNode;
import projet.app.engine.ast.FormulaDefinition;
import projet.app.engine.ast.FormulaNode;
import projet.app.engine.registry.FieldDefinition;
import projet.app.engine.registry.FieldRegistry;
import projet.app.engine.registry.JoinCatalog;
import projet.app.engine.registry.JoinDefinition;
import projet.app.engine.registry.JoinResolution;

import java.util.LinkedHashSet;
import java.util.Set;

@Service
public class JoinResolver {

    private final FieldRegistry fieldRegistry;
    private final JoinCatalog joinCatalog;

    public JoinResolver(FieldRegistry fieldRegistry, JoinCatalog joinCatalog) {
        this.fieldRegistry = fieldRegistry;
        this.joinCatalog = joinCatalog;
    }

    public JoinResolution resolve(FormulaDefinition definition) {
        Set<String> requiredJoinKeys = new LinkedHashSet<>();

        collectFromExpression(definition.expression(), requiredJoinKeys);

        if (definition.whereFilter() != null) {
            collectFromFilter(definition.whereFilter(), requiredJoinKeys);
        }

        for (String groupByField : definition.groupByFields()) {
            FieldDefinition fieldDefinition = fieldRegistry.resolve(groupByField);
            if (fieldDefinition.joinKey() != null) {
                requiredJoinKeys.add(fieldDefinition.joinKey());
            }
        }

        definition.orderBy().forEach(orderByNode -> {
            if ("value".equalsIgnoreCase(orderByNode.field())) {
                return;
            }

            FieldDefinition fieldDefinition = fieldRegistry.resolve(orderByNode.field());
            if (fieldDefinition.joinKey() != null) {
                requiredJoinKeys.add(fieldDefinition.joinKey());
            }
        });

        Set<String> expandedJoinKeys = new LinkedHashSet<>();
        for (String joinKey : requiredJoinKeys) {
            expandJoinKey(joinKey, expandedJoinKeys, new LinkedHashSet<>());
        }

        Set<String> clauses = new LinkedHashSet<>();
        Set<String> aliases = new LinkedHashSet<>();
        for (String joinKey : expandedJoinKeys) {
            JoinDefinition joinDefinition = joinCatalog.getRequired(joinKey);
            clauses.add(joinDefinition.clause());
            aliases.add(joinDefinition.alias());
        }

        return new JoinResolution(clauses.stream().toList(), aliases.stream().toList());
    }

    private void collectFromExpression(FormulaNode node, Set<String> joinKeys) {
        if (node instanceof FieldNode fieldNode) {
            FieldDefinition fieldDefinition = fieldRegistry.resolve(fieldNode.field());
            if (fieldDefinition.joinKey() != null) {
                joinKeys.add(fieldDefinition.joinKey());
            }
            return;
        }

        if (node instanceof AggregationNode aggregationNode) {
            if (aggregationNode.field() != null) {
                FieldDefinition fieldDefinition = fieldRegistry.resolve(aggregationNode.field());
                if (fieldDefinition.joinKey() != null) {
                    joinKeys.add(fieldDefinition.joinKey());
                }
            }

            if (aggregationNode.expression() != null) {
                collectFromExpression(aggregationNode.expression(), joinKeys);
            }

            if (aggregationNode.filters() != null) {
                collectFromFilter(aggregationNode.filters(), joinKeys);
            }
            return;
        }

        if (node instanceof BinaryOperationNode binaryNode) {
            collectFromExpression(binaryNode.left(), joinKeys);
            collectFromExpression(binaryNode.right(), joinKeys);
        }
    }

    private void collectFromFilter(FilterGroupNode group, Set<String> joinKeys) {
        for (FilterConditionNode condition : group.conditions()) {
            FieldDefinition fieldDefinition = fieldRegistry.resolve(condition.field());
            if (fieldDefinition.joinKey() != null) {
                joinKeys.add(fieldDefinition.joinKey());
            }
        }
        for (FilterGroupNode nestedGroup : group.groups()) {
            collectFromFilter(nestedGroup, joinKeys);
        }
    }

    private void expandJoinKey(String joinKey, Set<String> orderedKeys, Set<String> visiting) {
        if (joinKey == null || orderedKeys.contains(joinKey)) {
            return;
        }
        if (!visiting.add(joinKey)) {
            throw new IllegalStateException("Cyclic join dependency detected for key: " + joinKey);
        }

        JoinDefinition definition = joinCatalog.getRequired(joinKey);
        for (String dependency : definition.dependsOn()) {
            expandJoinKey(dependency, orderedKeys, visiting);
        }

        visiting.remove(joinKey);
        orderedKeys.add(joinKey);
    }
}
