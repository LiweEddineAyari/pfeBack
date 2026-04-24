package projet.app.engine.enums;

import projet.app.exception.FormulaValidationException;

import java.util.List;
import java.util.Locale;

public enum FilterOperator {
    EQ("=", true, false, false),
    NE("!=", true, false, false),
    GT(">", true, false, false),
    GTE(">=", true, false, false),
    LT("<", true, false, false),
    LTE("<=", true, false, false),
    LIKE("LIKE", true, false, false),
    STARTS_WITH("LIKE", true, false, false),
    ENDS_WITH("LIKE", true, false, false),
    CONTAINS("LIKE", true, false, false),
    IN("IN", true, true, false),
    NOT_IN("NOT IN", true, true, false),
    BETWEEN("BETWEEN", true, false, true),
    IS_NULL("IS NULL", false, false, false),
    IS_NOT_NULL("IS NOT NULL", false, false, false);

    private final String sqlToken;
    private final boolean requiresValue;
    private final boolean collectionValue;
    private final boolean rangeValue;

    FilterOperator(String sqlToken, boolean requiresValue, boolean collectionValue, boolean rangeValue) {
        this.sqlToken = sqlToken;
        this.requiresValue = requiresValue;
        this.collectionValue = collectionValue;
        this.rangeValue = rangeValue;
    }

    public String getSqlToken() {
        return sqlToken;
    }

    public boolean requiresValue() {
        return requiresValue;
    }

    public boolean expectsCollectionValue() {
        return collectionValue;
    }

    public boolean expectsRangeValue() {
        return rangeValue;
    }

    public boolean isPatternOperator() {
        return this == LIKE || this == STARTS_WITH || this == ENDS_WITH || this == CONTAINS;
    }

    public static FilterOperator from(String rawOperator) {
        if (rawOperator == null || rawOperator.isBlank()) {
            throw new FormulaValidationException(List.of("Filter operator is required"));
        }

        String normalized = rawOperator.trim().toUpperCase(Locale.ROOT).replaceAll("\\s+", " ");
        return switch (normalized) {
            case "=" -> EQ;
            case "EQ" -> EQ;
            case "!=", "<>" -> NE;
            case "NE" -> NE;
            case ">" -> GT;
            case "GT" -> GT;
            case ">=" -> GTE;
            case "GTE" -> GTE;
            case "<" -> LT;
            case "LT" -> LT;
            case "<=" -> LTE;
            case "LTE" -> LTE;
            case "LIKE" -> LIKE;
            case "STARTS WITH", "STARTS_WITH", "STARTSWITH", "BEGINS WITH", "BEGINS_WITH", "BEGINSWITH" -> STARTS_WITH;
            case "ENDS WITH", "ENDS_WITH", "ENDSWITH" -> ENDS_WITH;
            case "CONTAINS" -> CONTAINS;
            case "IN" -> IN;
            case "NOT IN" -> NOT_IN;
            case "NOT_IN" -> NOT_IN;
            case "BETWEEN" -> BETWEEN;
            case "IS NULL" -> IS_NULL;
            case "IS_NULL" -> IS_NULL;
            case "IS NOT NULL" -> IS_NOT_NULL;
            case "IS_NOT_NULL" -> IS_NOT_NULL;
            default -> throw new FormulaValidationException(List.of("Unsupported filter operator: " + rawOperator));
        };
    }
}