package projet.app.engine.compiler;

import projet.app.engine.enums.FormulaValueType;

import java.util.List;

public record FieldSpec(
        String canonicalName,
        String sourceTable,
        String sqlExpression,
        FormulaValueType valueType,
        List<JoinKey> joins
) {
}
