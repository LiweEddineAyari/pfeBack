package projet.app.engine.stresstest;

import org.springframework.stereotype.Component;
import projet.app.engine.ast.FilterConditionNode;
import projet.app.engine.ast.FilterGroupNode;
import projet.app.engine.enums.FilterLogic;
import projet.app.engine.enums.FilterOperator;
import projet.app.engine.registry.FieldDefinition;
import projet.app.engine.registry.FieldRegistry;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;

/**
 * Evaluates engine {@link FilterGroupNode} instances against an {@link InMemoryRow}.
 *
 * <p>Mirrors the SQL semantics used by {@code FilterBuilder} while operating entirely in memory.</p>
 */
@Component
public class InMemoryFilterMatcher {

    private final FieldRegistry fieldRegistry;

    public InMemoryFilterMatcher(FieldRegistry fieldRegistry) {
        this.fieldRegistry = fieldRegistry;
    }

    public boolean matches(FilterGroupNode group, InMemoryRow row) {
        if (group == null || group.isEmpty()) {
            return true;
        }

        FilterLogic logic = group.logic() == null ? FilterLogic.AND : group.logic();

        boolean seenAny = false;

        for (FilterConditionNode condition : group.conditions()) {
            boolean result = matchCondition(condition, row);
            if (logic == FilterLogic.AND && !result) {
                return false;
            }
            if (logic == FilterLogic.OR && result) {
                return true;
            }
            seenAny = true;
        }

        for (FilterGroupNode nested : group.groups()) {
            boolean result = matches(nested, row);
            if (logic == FilterLogic.AND && !result) {
                return false;
            }
            if (logic == FilterLogic.OR && result) {
                return true;
            }
            seenAny = true;
        }

        if (!seenAny) {
            return true;
        }
        return logic == FilterLogic.AND;
    }

    private boolean matchCondition(FilterConditionNode condition, InMemoryRow row) {
        FieldDefinition definition = fieldRegistry.resolve(condition.field());
        Object rowValue = row.get(definition.fieldName());
        FilterOperator operator = condition.operator();
        Object literal = condition.value();

        return switch (operator) {
            case EQ -> equalsValue(rowValue, literal);
            case NE -> !equalsValue(rowValue, literal);
            case GT -> compareValues(rowValue, literal) > 0;
            case GTE -> compareValues(rowValue, literal) >= 0;
            case LT -> compareValues(rowValue, literal) < 0;
            case LTE -> compareValues(rowValue, literal) <= 0;
            case IN -> matchIn(rowValue, literal);
            case NOT_IN -> !matchIn(rowValue, literal);
            case BETWEEN -> matchBetween(rowValue, literal);
            case IS_NULL -> rowValue == null;
            case IS_NOT_NULL -> rowValue != null;
            case LIKE -> matchLike(rowValue, literal, LikeMode.LIKE);
            case STARTS_WITH -> matchLike(rowValue, literal, LikeMode.STARTS_WITH);
            case ENDS_WITH -> matchLike(rowValue, literal, LikeMode.ENDS_WITH);
            case CONTAINS -> matchLike(rowValue, literal, LikeMode.CONTAINS);
        };
    }

    private boolean equalsValue(Object rowValue, Object literal) {
        if (rowValue == null || literal == null) {
            return rowValue == null && literal == null;
        }
        if (rowValue instanceof Number || literal instanceof Number
                || rowValue instanceof BigDecimal || literal instanceof BigDecimal) {
            BigDecimal left = toBigDecimal(rowValue);
            BigDecimal right = toBigDecimal(literal);
            if (left == null || right == null) {
                return false;
            }
            return left.compareTo(right) == 0;
        }
        if (rowValue instanceof LocalDate || literal instanceof LocalDate) {
            LocalDate left = toLocalDate(rowValue);
            LocalDate right = toLocalDate(literal);
            if (left == null || right == null) {
                return false;
            }
            return left.isEqual(right);
        }
        return String.valueOf(rowValue).equals(String.valueOf(literal));
    }

    private int compareValues(Object rowValue, Object literal) {
        if (rowValue == null || literal == null) {
            return rowValue == null && literal == null ? 0 : -1;
        }
        if (rowValue instanceof Number || literal instanceof Number
                || rowValue instanceof BigDecimal || literal instanceof BigDecimal) {
            BigDecimal left = toBigDecimal(rowValue);
            BigDecimal right = toBigDecimal(literal);
            if (left == null || right == null) {
                return -1;
            }
            return left.compareTo(right);
        }
        if (rowValue instanceof LocalDate || literal instanceof LocalDate) {
            LocalDate left = toLocalDate(rowValue);
            LocalDate right = toLocalDate(literal);
            if (left == null || right == null) {
                return -1;
            }
            return left.compareTo(right);
        }
        if (rowValue instanceof LocalDateTime || literal instanceof LocalDateTime) {
            LocalDateTime left = toLocalDateTime(rowValue);
            LocalDateTime right = toLocalDateTime(literal);
            if (left == null || right == null) {
                return -1;
            }
            return left.compareTo(right);
        }
        return String.valueOf(rowValue).compareTo(String.valueOf(literal));
    }

    private boolean matchIn(Object rowValue, Object literal) {
        if (!(literal instanceof List<?> values)) {
            return false;
        }
        for (Object candidate : values) {
            if (equalsValue(rowValue, candidate)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchBetween(Object rowValue, Object literal) {
        if (!(literal instanceof List<?> values) || values.size() != 2) {
            return false;
        }
        return compareValues(rowValue, values.get(0)) >= 0
                && compareValues(rowValue, values.get(1)) <= 0;
    }

    private boolean matchLike(Object rowValue, Object literal, LikeMode mode) {
        if (rowValue == null || literal == null) {
            return false;
        }
        String haystack = String.valueOf(rowValue);
        String needle = String.valueOf(literal);
        return switch (mode) {
            case LIKE -> sqlLikeMatches(haystack, needle);
            case STARTS_WITH -> haystack.toLowerCase(Locale.ROOT).startsWith(needle.toLowerCase(Locale.ROOT));
            case ENDS_WITH -> haystack.toLowerCase(Locale.ROOT).endsWith(needle.toLowerCase(Locale.ROOT));
            case CONTAINS -> haystack.toLowerCase(Locale.ROOT).contains(needle.toLowerCase(Locale.ROOT));
        };
    }

    private boolean sqlLikeMatches(String haystack, String pattern) {
        StringBuilder regex = new StringBuilder();
        regex.append("(?i)^");
        for (int i = 0; i < pattern.length(); i++) {
            char c = pattern.charAt(i);
            switch (c) {
                case '%' -> regex.append(".*");
                case '_' -> regex.append('.');
                case '.', '\\', '(', ')', '[', ']', '{', '}', '+', '*', '?', '^', '$', '|' -> {
                    regex.append('\\').append(c);
                }
                default -> regex.append(c);
            }
        }
        regex.append('$');
        return haystack.matches(regex.toString());
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal bd) {
            return bd;
        }
        if (value instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        if (value instanceof String str) {
            try {
                return new BigDecimal(str);
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        return null;
    }

    private LocalDate toLocalDate(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDate ld) {
            return ld;
        }
        if (value instanceof LocalDateTime ldt) {
            return ldt.toLocalDate();
        }
        if (value instanceof java.sql.Date sqlDate) {
            return sqlDate.toLocalDate();
        }
        if (value instanceof String str) {
            try {
                return LocalDate.parse(str);
            } catch (DateTimeParseException ex) {
                return null;
            }
        }
        return null;
    }

    private LocalDateTime toLocalDateTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDateTime ldt) {
            return ldt;
        }
        if (value instanceof LocalDate ld) {
            return ld.atStartOfDay();
        }
        if (value instanceof java.sql.Timestamp ts) {
            return ts.toLocalDateTime();
        }
        if (value instanceof String str) {
            try {
                return LocalDateTime.parse(str);
            } catch (DateTimeParseException ex) {
                return null;
            }
        }
        return null;
    }

    private enum LikeMode { LIKE, STARTS_WITH, ENDS_WITH, CONTAINS }
}
