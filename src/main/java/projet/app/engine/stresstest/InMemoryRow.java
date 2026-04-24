package projet.app.engine.stresstest;

import projet.app.engine.registry.FieldDefinition;
import projet.app.engine.registry.FieldRegistry;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Single flattened fact_balance row with all joined dimension attributes materialised in memory.
 *
 * <p>Values are keyed by the canonical {@link FieldDefinition#fieldName()} from
 * {@link FieldRegistry}, normalised to lower-case so that any alias can be used for lookups.</p>
 */
public final class InMemoryRow {

    private final Map<String, Object> values;

    public InMemoryRow() {
        this.values = new HashMap<>();
    }

    public InMemoryRow(Map<String, Object> initialValues) {
        this.values = new HashMap<>();
        if (initialValues != null) {
            initialValues.forEach((key, value) -> this.values.put(normalize(key), value));
        }
    }

    public Object get(String canonicalFieldName) {
        return values.get(normalize(canonicalFieldName));
    }

    public boolean contains(String canonicalFieldName) {
        return values.containsKey(normalize(canonicalFieldName));
    }

    public void set(String canonicalFieldName, Object value) {
        values.put(normalize(canonicalFieldName), value);
    }

    public Map<String, Object> asMap() {
        return values;
    }

    public static String normalize(String fieldName) {
        return fieldName == null ? "" : fieldName.trim().toLowerCase(Locale.ROOT);
    }
}
