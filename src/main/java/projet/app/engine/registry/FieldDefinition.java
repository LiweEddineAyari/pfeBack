package projet.app.engine.registry;

public record FieldDefinition(
        String fieldName,
        String column,
        String tableAlias,
        String joinKey,
        FieldDataType dataType
) {

    public String qualifiedColumn() {
        return tableAlias + "." + column;
    }
}
