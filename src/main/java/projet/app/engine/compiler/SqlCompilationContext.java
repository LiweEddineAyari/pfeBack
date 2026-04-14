package projet.app.engine.compiler;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class SqlCompilationContext {

    private final List<Object> parameters = new ArrayList<>();
    private final Set<String> referencedFields = new LinkedHashSet<>();
    private final Set<JoinKey> joins = new LinkedHashSet<>();

    public void addParameter(Object value) {
        parameters.add(value);
    }

    public void addReferencedField(String field) {
        referencedFields.add(field);
    }

    public void requireJoin(JoinKey joinKey) {
        joins.add(joinKey);
    }

    public void requireJoins(List<JoinKey> keys) {
        joins.addAll(keys);
    }

    public List<Object> getParameters() {
        return List.copyOf(parameters);
    }

    public Set<String> getReferencedFields() {
        return Set.copyOf(referencedFields);
    }

    public List<JoinKey> getJoins() {
        return List.copyOf(joins);
    }
}
