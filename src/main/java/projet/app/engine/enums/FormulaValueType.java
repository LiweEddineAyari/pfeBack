package projet.app.engine.enums;

public enum FormulaValueType {
    NUMERIC,
    STRING,
    BOOLEAN,
    DATE,
    DATETIME,
    UNKNOWN;

    public boolean isNumeric() {
        return this == NUMERIC;
    }
}