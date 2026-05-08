package projet.app.ai.rag.ingestion;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Converts {@link RawRow}s from a knowledge-base spreadsheet into one or more
 * {@link RagChunk}s. Implements the chunking rules from the master prompt:
 * <ul>
 *   <li>One concept = one chunk (no merging across logical rows).</li>
 *   <li>Each row whose {@code title} or {@code content} exceeds 400 tokens is
 *       split into self-contained sub-chunks at sentence boundaries.</li>
 *   <li>Every chunk's text begins with the ratio / parameter code (when present)
 *       so that the FTS strategy keeps high recall on code mentions.</li>
 *   <li>Raw spreadsheet rows are NEVER stored verbatim; we always synthesize
 *       prose from {@code title + content}.</li>
 * </ul>
 *
 * <p>Expected source columns (case-insensitive): {@code title}, {@code content}
 * (or {@code body} / {@code description}), {@code document_type}, {@code category},
 * {@code ratio_code}, {@code parameter_code}, {@code ratio_family}, {@code domain},
 * {@code regulation}, {@code source}, {@code keywords}, {@code language}.
 * Unknown / missing columns degrade gracefully.
 */
@Slf4j
@Component
public class ChunkingEngine {

    private static final int MAX_CHUNK_CHARS = 1_800; // ≈400 tokens at French average

    public List<RagChunk> chunk(List<RawRow> rows) {
        List<RagChunk> chunks = new ArrayList<>();
        for (RawRow row : rows) {
            String title = firstNonBlank(row.get("title"), row.get("titre"));
            String content = firstNonBlank(
                    row.get("content"), row.get("contenu"),
                    row.get("body"), row.get("description"));
            if (content == null || content.isBlank()) {
                continue;
            }
            String docType = nonBlankOr(row.get("document_type"), row.get("type"), null);
            String category = row.get("category");
            String ratioCode = upperOrNull(firstNonBlank(row.get("ratio_code"), row.get("ratio")));
            String paramCode = upperOrNull(firstNonBlank(row.get("parameter_code"), row.get("parameter")));
            String family = row.get("ratio_family");
            String domain = lowerOrNull(row.get("domain"));
            String regulation = row.get("regulation");
            String source = row.get("source");
            String[] keywords = splitKeywords(row.get("keywords"));
            String language = nonBlankOr(row.get("language"), null, "fr");

            String prefix = codePrefix(ratioCode, paramCode);
            String fullTitle = (title == null || title.isBlank())
                    ? (prefix + " — knowledge chunk").trim()
                    : title.trim();
            String body = prefix.isEmpty() ? content.trim() : prefix + " — " + content.trim();

            for (String slice : splitForSize(body, MAX_CHUNK_CHARS)) {
                chunks.add(RagChunk.builder()
                        .title(fullTitle)
                        .text(slice)
                        .documentType(docType)
                        .category(category)
                        .ratioCode(ratioCode)
                        .parameterCode(paramCode)
                        .ratioFamily(family)
                        .domain(domain)
                        .regulation(regulation)
                        .source(source)
                        .keywords(keywords)
                        .language(language)
                        .build());
            }
        }
        log.info("ChunkingEngine produced {} chunk(s) from {} row(s)",
                chunks.size(), rows.size());
        return chunks;
    }

    private List<String> splitForSize(String text, int max) {
        if (text.length() <= max) {
            return List.of(text);
        }
        List<String> out = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + max, text.length());
            if (end < text.length()) {
                int dot = text.lastIndexOf('.', end);
                int qm = text.lastIndexOf('?', end);
                int em = text.lastIndexOf('!', end);
                int boundary = Math.max(dot, Math.max(qm, em));
                if (boundary > start + max / 2) {
                    end = boundary + 1;
                }
            }
            out.add(text.substring(start, end).trim());
            start = end;
        }
        return out;
    }

    private static String codePrefix(String ratioCode, String parameterCode) {
        if (ratioCode != null && !ratioCode.isBlank()) {
            return "[" + ratioCode + "]";
        }
        if (parameterCode != null && !parameterCode.isBlank()) {
            return "[" + parameterCode + "]";
        }
        return "";
    }

    private static String[] splitKeywords(String raw) {
        if (raw == null || raw.isBlank()) {
            return new String[0];
        }
        String[] parts = raw.split("[,;|]\\s*");
        List<String> kept = new ArrayList<>();
        for (String p : parts) {
            String t = p.trim();
            if (!t.isEmpty()) kept.add(t);
        }
        return kept.toArray(new String[0]);
    }

    private static String firstNonBlank(String... candidates) {
        for (String c : candidates) {
            if (c != null && !c.isBlank()) return c;
        }
        return null;
    }

    private static String nonBlankOr(String primary, String secondary, String fallback) {
        if (primary != null && !primary.isBlank()) return primary;
        if (secondary != null && !secondary.isBlank()) return secondary;
        return fallback;
    }

    private static String upperOrNull(String v) {
        return (v == null || v.isBlank()) ? null : v.trim().toUpperCase();
    }

    private static String lowerOrNull(String v) {
        return (v == null || v.isBlank()) ? null : v.trim().toLowerCase();
    }
}
