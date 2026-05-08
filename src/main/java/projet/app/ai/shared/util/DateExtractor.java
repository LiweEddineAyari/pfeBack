package projet.app.ai.shared.util;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Pulls ISO-style {@code YYYY-MM-DD} dates from free-text user queries. */
public final class DateExtractor {

    private static final Pattern ISO_DATE =
            Pattern.compile("\\b(\\d{4})-(\\d{2})-(\\d{2})\\b");

    private DateExtractor() {}

    public static List<String> extractDates(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<String> dates = new ArrayList<>();
        Matcher m = ISO_DATE.matcher(text);
        while (m.find()) {
            dates.add(m.group());
        }
        return dates;
    }
}
