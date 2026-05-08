package projet.app.ai.tools.dto;

import java.util.List;

/**
 * Multi-date trend snapshot for a single ratio.
 * Computed entirely in Java by the {@code compareRatioAcrossDates} tool —
 * the LLM receives this structure and never re-derives the math.
 *
 * @param ratioCode  ratio code, e.g. {@code "RS"}
 * @param points     ordered data points (oldest first)
 * @param delta      arithmetic delta between the most-recent and earliest values
 * @param direction  one of {@code IMPROVING}, {@code DETERIORATING}, {@code STABLE}
 */
public record RatioTrendDTO(
        String ratioCode,
        List<DataPoint> points,
        double delta,
        double percentChange,
        String direction
) {
    public record DataPoint(String date, Double value) {}
}
