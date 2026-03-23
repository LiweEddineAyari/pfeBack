package projet.app.service.transform.contrat;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public class DateTransformer {

    private static final DateTimeFormatter OUTPUT_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private static final DateTimeFormatter[] INPUT_FORMATS = {
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd"),
            DateTimeFormatter.ofPattern("dd.MM.yyyy"),
    };

    public static String transform(String sourceValue) {
        if (sourceValue == null || sourceValue.isBlank()) {
            return null;
        }

        String trimmed = sourceValue.trim();

        // If already in dd/MM/yyyy, return as-is
        if (trimmed.matches("^\\d{2}/\\d{2}/\\d{4}$")) {
            try {
                LocalDate.parse(trimmed, OUTPUT_FORMAT);
                return trimmed;
            } catch (DateTimeParseException ignored) {
            }
        }

        // Try each input format
        for (DateTimeFormatter fmt : INPUT_FORMATS) {
            try {
                LocalDate date = LocalDate.parse(trimmed, fmt);
                return date.format(OUTPUT_FORMAT);
            } catch (DateTimeParseException ignored) {
            }
        }

        // Handle ISO datetime (e.g. "2024-01-15T10:30:00")
        if (trimmed.contains("T")) {
            try {
                LocalDate date = LocalDate.parse(trimmed.substring(0, 10), DateTimeFormatter.ISO_LOCAL_DATE);
                return date.format(OUTPUT_FORMAT);
            } catch (DateTimeParseException ignored) {
            }
        }

        return sourceValue;
    }
}
