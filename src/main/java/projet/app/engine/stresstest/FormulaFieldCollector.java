package projet.app.engine.stresstest;

import org.springframework.stereotype.Component;
import projet.app.engine.ast.AggregationNode;
import projet.app.engine.ast.BinaryOperationNode;
import projet.app.engine.ast.FieldNode;
import projet.app.engine.ast.FilterConditionNode;
import projet.app.engine.ast.FilterGroupNode;
import projet.app.engine.ast.FormulaDefinition;
import projet.app.engine.ast.FormulaNode;
import projet.app.engine.registry.FieldDefinition;
import projet.app.engine.registry.FieldRegistry;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Walks a {@link FormulaDefinition} to collect every logical field the formula depends on.
 *
 * <p>Collected names are canonical (see {@link FieldRegistry#resolve(String)}) so the caller
 * can reason about field dependencies regardless of which alias was used in the formula JSON.</p>
 */
@Component
public class FormulaFieldCollector {

    private final FieldRegistry fieldRegistry;

    public FormulaFieldCollector(FieldRegistry fieldRegistry) {
        this.fieldRegistry = fieldRegistry;
    }

    public Set<String> collect(FormulaDefinition definition) {
        Set<String> fields = new LinkedHashSet<>();
        if (definition == null) {
            return fields;
        }
        if (definition.expression() != null) {
            collectFromNode(definition.expression(), fields);
        }
        if (definition.whereFilter() != null) {
            collectFromFilter(definition.whereFilter(), fields);
        }
        if (definition.groupByFields() != null) {
            for (String groupByField : definition.groupByFields()) {
                addField(groupByField, fields);
            }
        }
        return fields;
    }

    private void collectFromNode(FormulaNode node, Set<String> fields) {
        if (node instanceof FieldNode fieldNode) {
            addField(fieldNode.field(), fields);
            return;
        }
        if (node instanceof AggregationNode aggregationNode) {
            if (aggregationNode.field() != null && !aggregationNode.field().isBlank()) {
                addField(aggregationNode.field(), fields);
            }
            if (aggregationNode.expression() != null) {
                collectFromNode(aggregationNode.expression(), fields);
            }
            if (aggregationNode.filters() != null) {
                collectFromFilter(aggregationNode.filters(), fields);
            }
            return;
        }
        if (node instanceof BinaryOperationNode binaryNode) {
            collectFromNode(binaryNode.left(), fields);
            collectFromNode(binaryNode.right(), fields);
        }
        // VALUE nodes contribute no field dependencies.
    }

    private void collectFromFilter(FilterGroupNode group, Set<String> fields) {
        if (group == null || group.isEmpty()) {
            return;
        }
        for (FilterConditionNode condition : group.conditions()) {
            addField(condition.field(), fields);
        }
        for (FilterGroupNode nested : group.groups()) {
            collectFromFilter(nested, fields);
        }
    }

    private void addField(String rawField, Set<String> fields) {
        if (rawField == null || rawField.isBlank()) {
            return;
        }
        try {
            FieldDefinition definition = fieldRegistry.resolve(rawField);
            fields.add(definition.fieldName().toLowerCase(Locale.ROOT));
        } catch (RuntimeException ex) {
            // Unknown fields are caught by upstream validation; ignore here so collection is lenient.
        }
    }
}
