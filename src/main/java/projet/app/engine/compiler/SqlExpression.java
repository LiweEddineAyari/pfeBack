package projet.app.engine.compiler;

import projet.app.engine.enums.FormulaValueType;

public record SqlExpression(
        String sql,
        FormulaValueType valueType,
        boolean aggregated
) {
}
