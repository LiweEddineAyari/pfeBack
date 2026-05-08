package projet.app.ai.shared.util;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Tiny multilingual tokenizer used by the RAG layer for full-text query building.
 * Strips punctuation, lower-cases, and removes common French / English stop-words.
 */
public final class Tokenizer {

    // Use Set.copyOf(List.of(...)) instead of Set.of(...) so that any accidental
    // duplicate stop-word (e.g. "on" exists in both French and English) does not
    // crash the JVM with IllegalArgumentException at class-load time.
    private static final Set<String> STOP_WORDS = Set.copyOf(List.of(
            // French
            "le", "la", "les", "un", "une", "des", "de", "du", "et", "ou", "à", "au",
            "aux", "en", "pour", "par", "sur", "sous", "dans", "avec", "sans", "ce",
            "cet", "cette", "ces", "que", "qui", "quoi", "est", "sont", "ils", "elles",
            "il", "elle", "on", "nous", "vous", "se", "sa", "son", "ses", "notre",
            "votre", "leur", "leurs", "y", "n", "d", "l", "j", "m", "s", "t", "qu",
            // English (note: "on" intentionally omitted — already in French list)
            "the", "a", "an", "and", "or", "of", "to", "in", "for", "with",
            "by", "is", "are", "be", "been", "was", "were", "this", "that", "these",
            "those", "it", "its", "as", "at", "from", "into", "than", "then"
    ));

    private Tokenizer() {}

    public static List<String> normalize(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String cleaned = text
                .toLowerCase()
                .replaceAll("[^\\p{L}\\p{Nd}\\s-]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return Arrays.stream(cleaned.split(" "))
                .filter(t -> t.length() > 1)
                .filter(t -> !STOP_WORDS.contains(t))
                .distinct()
                .collect(Collectors.toList());
    }
}
