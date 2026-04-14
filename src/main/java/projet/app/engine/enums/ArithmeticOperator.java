package projet.app.engine.enums;

import projet.app.exception.FormulaValidationException;

import java.util.List;
import java.util.Locale;

public enum ArithmeticOperator {
    ADD("+"),
    SUBTRACT("-"),
    MULTIPLY("*"),
    DIVIDE("/");

    private final String sqlSymbol;

    ArithmeticOperator(String sqlSymbol) {
        this.sqlSymbol = sqlSymbol;
    }

    public String getSqlSymbol() {
        return sqlSymbol;
    }

    public static ArithmeticOperator from(String rawOperator) {
        if (rawOperator == null || rawOperator.isBlank()) {
            throw new FormulaValidationException(List.of("Arithmetic operator is required"));
        }

        try {
            return ArithmeticOperator.valueOf(rawOperator.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new FormulaValidationException(List.of("Unsupported arithmetic operator: " + rawOperator));
        }
    }
}
