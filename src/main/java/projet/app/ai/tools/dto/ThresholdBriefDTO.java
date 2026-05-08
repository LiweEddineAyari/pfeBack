package projet.app.ai.tools.dto;

import java.util.List;

/**
 * Categorised view of threshold compliance for all ratios on a given reference date.
 * The breach categorisation is computed in Java; the LLM only formats the result.
 *
 * <p>Severity ladder, lowest first:
 * <ul>
 *   <li>{@code critical} — value below {@code seuilAppetence}</li>
 *   <li>{@code alert}    — value below {@code seuilAlerte}</li>
 *   <li>{@code warning}  — value below {@code seuilTolerance}</li>
 *   <li>{@code healthy}  — value at or above all configured thresholds</li>
 * </ul>
 */
public record ThresholdBriefDTO(
        String referenceDate,
        List<BreachItem> critical,
        List<BreachItem> alert,
        List<BreachItem> warning,
        List<BreachItem> healthy
) {
    public record BreachItem(
            String code,
            String label,
            Double value,
            Double seuilTolerance,
            Double seuilAlerte,
            Double seuilAppetence,
            String familleCode,
            String categorieCode,
            String severity
    ) {}
}
