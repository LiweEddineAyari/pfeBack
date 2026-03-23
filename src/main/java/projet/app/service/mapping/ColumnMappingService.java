package projet.app.service.mapping;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class ColumnMappingService {

    // Schema definitions - all expected DB column names per type
    private static final Map<String, List<String>> SCHEMAS = Map.of(
            "TIERS", List.of("idtiers", "nomprenom", "raisonsoc", "residence",
                    "agenteco", "sectionactivite", "chiffreaffaires", "nationalite",
                    "douteux", "datdouteux", "grpaffaires", "nomgrpaffaires", "residencenum"),
            "CONTRAT", List.of("idcontrat", "agence", "devise", "ancienneteimpaye",
                    "objetfinance", "typcontrat", "datouv", "datech",
                    "idtiers", "tauxcontrat", "actif"),
            "COMPTA", List.of("agence", "devise", "compte", "chapitre",
                    "libellecompte", "idtiers", "soldeorigine", "soldeconvertie",
                    "devisebbnq", "cumulmvtdb", "cumulmvtcr", "soldeinitdebmois",
                    "idcontrat", "amount", "actif", "date_bal")
    );

    /**
     * Resolve the effective column mapping for a given row.
     * <p>
     * Priority:
     * 1. Explicit user mapping (fileCol → dbCol)
     * 2. Auto-match: normalize both sides, match if equal
     * 3. No match: column is passed through as-is
     * (will be ignored during staging insert if unknown)
     * <p>
     * Returns a map: fileColumnName → dbColumnName
     */
    public Map<String, String> resolveMapping(
            List<String> fileColumns,
            String type,
            Map<String, String> explicitMapping) {

        List<String> dbColumns = SCHEMAS.getOrDefault(
                type.toUpperCase(), List.of());

        Map<String, String> resolved = new LinkedHashMap<>();

        for (String fileCol : fileColumns) {

            // 1. Explicit mapping provided by user
            if (explicitMapping.containsKey(fileCol)) {
                resolved.put(fileCol, explicitMapping.get(fileCol));
                log.debug("Explicit mapping: {} → {}",
                        fileCol, explicitMapping.get(fileCol));
                continue;
            }

            // 2. Auto-match by normalized name
            String normFile = normalize(fileCol);
            String autoMatch = dbColumns.stream()
                    .filter(db -> normalize(db).equals(normFile))
                    .findFirst()
                    .orElse(null);

            if (autoMatch != null) {
                resolved.put(fileCol, autoMatch);
                log.debug("Auto-matched: {} → {}", fileCol, autoMatch);
            } else {
                // 3. No match — pass through (will be skipped on insert)
                resolved.put(fileCol, fileCol);
                log.debug("No match for column: {}", fileCol);
            }
        }

        // Log summary
        long matched = resolved.values().stream()
                .filter(v -> dbColumns.contains(v.toLowerCase())).count();
        log.info("Column mapping resolved: {}/{} DB columns matched " +
                "for type {}", matched, dbColumns.size(), type);

        log.info("=== Column Mapping Summary for {} ===", type);
        log.info("{:<30} → {}", "FILE COLUMN", "DB COLUMN");
        log.info("{}", "-".repeat(50));
        resolved.forEach((fileCol, dbCol) -> {
            boolean isDbCol = dbColumns.contains(dbCol.toLowerCase());
            String source = explicitMapping.containsKey(fileCol)
                    ? "[explicit]" : "[auto]";
            String status = isDbCol ? "✓" : "✗ (dropped)";
            log.info("{:<30} → {:<25} {} {}",
                    fileCol, dbCol, source, status);
        });
        log.info("Matched: {}/{} required DB columns",
                matched, dbColumns.size());

        return resolved;
    }

    /**
     * Apply the resolved mapping to a single data row.
     * Renames keys from fileColumn names to dbColumn names.
     * Drops any file columns that don't map to a known DB column.
     */
    public Map<String, String> applyMapping(
            Map<String, String> rowData,
            Map<String, String> resolvedMapping,
            String type) {

        List<String> dbColumns = SCHEMAS.getOrDefault(
                type.toUpperCase(), List.of());

        Map<String, String> mapped = new LinkedHashMap<>();

        for (Map.Entry<String, String> entry : rowData.entrySet()) {
            String fileCol = entry.getKey();
            String dbCol = resolvedMapping.getOrDefault(fileCol, fileCol);

            // Only keep columns that exist in the DB schema
            if (dbColumns.contains(dbCol.toLowerCase())) {
                mapped.put(dbCol.toLowerCase(), entry.getValue());
            }
        }

        return mapped;
    }

    /**
     * Normalize a column name for comparison:
     * lowercase, remove underscores, spaces, hyphens.
     */
    private String normalize(String col) {
        if (col == null) return "";
        return col.toLowerCase()
                .replaceAll("[_\\s\\-]", "");
    }
}