package projet.app.service.quality;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import projet.app.entity.quality.DataQualityResultCompta;
import projet.app.repository.quality.DataQualityResultComptaRepository;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class ComptaDataQualityService {

    private final JdbcTemplate jdbcTemplate;
    private final DataQualityResultComptaRepository resultRepository;

    @Transactional
    public DataQualityResult cleanStagingTable() {
        log.info("Starting data quality checks on stg_compta_raw");

        DataQualityResult result = new DataQualityResult();

        // Rule 1: Null check - count rows where any column is null (no deletion)
        int nullCount = countRowsWithNullValues();
        result.setNullCheckCount(nullCount);
        log.info("Rule 1 (Null Check): Found {} rows with null values", nullCount);

        // Rule 2: Duplicate check by chapitre, compte, idtiers (no deletion)
        int duplicateCount = countDuplicateRows();
        result.setDuplicateCount(duplicateCount);
        log.info("Rule 2 (Duplicate Check): Found {} duplicate rows by chapitre, compte, idtiers", duplicateCount);

        // Rule 3: Type validation - count rows with invalid data types (no deletion)
        int typeCheckCount = countRowsWithInvalidTypes();
        result.setTypeCheckCount(typeCheckCount);
        log.info("Rule 3 (Type Check): Found {} rows with invalid data types", typeCheckCount);

        int totalIssues = nullCount + duplicateCount + typeCheckCount;
        result.setTotalIssues(totalIssues);
        log.info("Data quality checks completed. Total issues found: {}", totalIssues);

        // Rule 4: Calculate sum of soldeconvertie for all rows
        long balanceSum = calculateBalanceSum();
        result.setBalanceSum(balanceSum);
        log.info("Balance sum (soldeconvertie): {}", balanceSum);

        // Rule 5: Relation checks
        int contratRelationCount = countMissingContratRelations();
        result.setContratRelationCheck(contratRelationCount);
        log.info("Rule 5a (Contrat Relation Check): Found {} rows with idcontrat not in stg_contrat_raw", contratRelationCount);

        int tiersRelationCount = countMissingTiersRelations();
        result.setTiersRelationCheck(tiersRelationCount);
        log.info("Rule 5b (Tiers Relation Check): Found {} rows with idtiers not in stg_tiers_raw", tiersRelationCount);

        // Set all blank strings to NULL
        int blanksFixed = setBlankStringsToNull();
        log.info("Set {} blank string values to NULL", blanksFixed);

        // Save result to database
        DataQualityResultCompta savedResult = saveResultToDatabase(result);
        log.info("Data quality result saved with id: {}", savedResult.getId());

        return result;
    }

    private DataQualityResultCompta saveResultToDatabase(DataQualityResult result) {
        DataQualityResultCompta entity = DataQualityResultCompta.builder()
                .nullCheckCount(result.getNullCheckCount())
                .duplicateCount(result.getDuplicateCount())
                .typeCheckCount(result.getTypeCheckCount())
                .balanceSum(result.getBalanceSum())
                .contratRelationCheck(result.getContratRelationCheck())
                .tiersRelationCheck(result.getTiersRelationCheck())
                .totalIssues(result.getTotalIssues())
                .status("COMPLETED")
                .executedAt(LocalDateTime.now())
                .build();

        return resultRepository.save(entity);
    }

    /**
     * Rule 1: Count rows where ANY column is null (all columns must be not null).
     * No rows are deleted.
     */
    private int countRowsWithNullValues() {
        String sql = """
            SELECT COUNT(*) FROM staging.stg_compta_raw
            WHERE agence IS NULL
               OR devise IS NULL
               OR compte IS NULL
               OR chapitre IS NULL
               OR libellecompte IS NULL
               OR idtiers IS NULL
               OR soldeorigine IS NULL
               OR soldeconvertie IS NULL
               OR devisebbnq IS NULL
               OR cumulmvtdb IS NULL
               OR cumulmvtcr IS NULL
               OR soldeinitdebmois IS NULL
               OR amount IS NULL
               OR actif IS NULL
            """;

        int count = jdbcTemplate.queryForObject(sql, Integer.class);

        if (count > 0) {
            String idSql = """
                SELECT id FROM staging.stg_compta_raw
                WHERE agence IS NULL
                   OR devise IS NULL
                   OR compte IS NULL
                   OR chapitre IS NULL
                   OR libellecompte IS NULL
                   OR idtiers IS NULL
                   OR soldeorigine IS NULL
                   OR soldeconvertie IS NULL
                   OR devisebbnq IS NULL
                   OR cumulmvtdb IS NULL
                   OR cumulmvtcr IS NULL
                   OR soldeinitdebmois IS NULL
                   OR amount IS NULL
                   OR actif IS NULL
                """;
            java.util.List<Long> ids = jdbcTemplate.queryForList(idSql, Long.class);
            log.info("Rule 1 (Null Check) - affected row ids: {}", ids);
        }

        return count;
    }

    /**
     * Rule 2: Count duplicate rows by chapitre, compte, idtiers (keeping first occurrence).
     * No rows are deleted.
     */
    private int countDuplicateRows() {
        String sql = """
            SELECT COUNT(*) FROM (
                SELECT
                    id,
                    ROW_NUMBER() OVER (
                        PARTITION BY TRIM(chapitre), TRIM(compte), TRIM(idtiers)
                        ORDER BY id
                    ) AS rn
                FROM staging.stg_compta_raw
                WHERE NULLIF(TRIM(chapitre), '') IS NOT NULL
                  AND NULLIF(TRIM(compte), '') IS NOT NULL
                  AND NULLIF(TRIM(idtiers), '') IS NOT NULL
            ) duplicates
            WHERE rn > 1
            """;

        return jdbcTemplate.queryForObject(sql, Integer.class);
    }

    /**
     * Rule 3: Count rows with invalid data types.
     *
     * Integer columns (must be valid integers):
     *   agence, compte, chapitre, idtiers, actif
     *
     * Numeric columns (must be valid numbers - integers, decimals, or scientific notation):
     *   soldeorigine, soldeconvertie, cumulmvtdb, cumulmvtcr, soldeinitdebmois, amount
     *
     * String columns (must NOT be purely numeric):
     *   devise, libellecompte, devisebbnq, idcontrat
     *
     * No rows are deleted.
     */
    private int countRowsWithInvalidTypes() {
        String numericRegex = "'^-?[0-9]+(\\.[0-9]+)?([eE][+-]?[0-9]+)?$'";
        String integerRegex = "'^-?[0-9]+$'";
        String whereClause =
                "(agence IS NOT NULL AND agence <> '' AND agence !~ " + integerRegex + ")" +
                " OR (compte IS NOT NULL AND compte <> '' AND compte !~ " + integerRegex + ")" +
                " OR (chapitre IS NOT NULL AND chapitre <> '' AND chapitre !~ " + integerRegex + ")" +
                " OR (idtiers IS NOT NULL AND idtiers <> '' AND idtiers !~ " + integerRegex + ")" +
                " OR (actif IS NOT NULL AND actif <> '' AND actif !~ " + integerRegex + ")" +
                " OR (soldeorigine IS NOT NULL AND soldeorigine <> '' AND soldeorigine !~ " + numericRegex + ")" +
                " OR (soldeconvertie IS NOT NULL AND soldeconvertie <> '' AND soldeconvertie !~ " + numericRegex + ")" +
                " OR (cumulmvtdb IS NOT NULL AND cumulmvtdb <> '' AND cumulmvtdb !~ " + numericRegex + ")" +
                " OR (cumulmvtcr IS NOT NULL AND cumulmvtcr <> '' AND cumulmvtcr !~ " + numericRegex + ")" +
                " OR (soldeinitdebmois IS NOT NULL AND soldeinitdebmois <> '' AND soldeinitdebmois !~ " + numericRegex + ")" +
                " OR (amount IS NOT NULL AND amount <> '' AND amount !~ " + numericRegex + ")" +
                " OR (devise IS NOT NULL AND devise <> '' AND devise ~ " + numericRegex + ")" +
                " OR (libellecompte IS NOT NULL AND libellecompte <> '' AND libellecompte ~ " + numericRegex + ")" +
                " OR (devisebbnq IS NOT NULL AND devisebbnq <> '' AND devisebbnq ~ " + numericRegex + ")" +
                " OR (idcontrat IS NOT NULL AND idcontrat <> '' AND idcontrat ~ " + numericRegex + ")";

        int count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM staging.stg_compta_raw WHERE " + whereClause, Integer.class);

        if (count > 0) {
            // Log per-column invalid counts and sample values
            java.util.Map<String, String> integerColumns = java.util.Map.of(
                    "agence", "agence IS NOT NULL AND agence <> '' AND agence !~ " + integerRegex,
                    "compte", "compte IS NOT NULL AND compte <> '' AND compte !~ " + integerRegex,
                    "chapitre", "chapitre IS NOT NULL AND chapitre <> '' AND chapitre !~ " + integerRegex,
                    "idtiers", "idtiers IS NOT NULL AND idtiers <> '' AND idtiers !~ " + integerRegex,
                    "actif", "actif IS NOT NULL AND actif <> '' AND actif !~ " + integerRegex
            );
            java.util.Map<String, String> numericColumns = java.util.Map.of(
                    "soldeorigine", "soldeorigine IS NOT NULL AND soldeorigine <> '' AND soldeorigine !~ " + numericRegex,
                    "soldeconvertie", "soldeconvertie IS NOT NULL AND soldeconvertie <> '' AND soldeconvertie !~ " + numericRegex,
                    "cumulmvtdb", "cumulmvtdb IS NOT NULL AND cumulmvtdb <> '' AND cumulmvtdb !~ " + numericRegex,
                    "cumulmvtcr", "cumulmvtcr IS NOT NULL AND cumulmvtcr <> '' AND cumulmvtcr !~ " + numericRegex,
                    "soldeinitdebmois", "soldeinitdebmois IS NOT NULL AND soldeinitdebmois <> '' AND soldeinitdebmois !~ " + numericRegex,
                    "amount", "amount IS NOT NULL AND amount <> '' AND amount !~ " + numericRegex
            );
            java.util.Map<String, String> stringColumns = java.util.Map.of(
                    "devise", "devise IS NOT NULL AND devise <> '' AND devise ~ " + numericRegex,
                    "libellecompte", "libellecompte IS NOT NULL AND libellecompte <> '' AND libellecompte ~ " + numericRegex,
                    "devisebbnq", "devisebbnq IS NOT NULL AND devisebbnq <> '' AND devisebbnq ~ " + numericRegex,
                    "idcontrat", "idcontrat IS NOT NULL AND idcontrat <> '' AND idcontrat ~ " + numericRegex
            );

            java.util.Map<String, String> allColumns = new java.util.LinkedHashMap<>();
            allColumns.putAll(integerColumns);
            allColumns.putAll(numericColumns);
            allColumns.putAll(stringColumns);

            for (java.util.Map.Entry<String, String> entry : allColumns.entrySet()) {
                String col = entry.getKey();
                String condition = entry.getValue();
                int colCount = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM staging.stg_compta_raw WHERE " + condition, Integer.class);
                if (colCount > 0) {
                    java.util.List<String> sampleValues = jdbcTemplate.queryForList(
                            "SELECT " + col + " FROM staging.stg_compta_raw WHERE " + condition + " LIMIT 5", String.class);
                    log.info("Rule 3 (Type Check) - column '{}': {} invalid rows, sample values: {}", col, colCount, sampleValues);
                }
            }

            // Log a sample of full rows with invalid types
            java.util.List<java.util.Map<String, Object>> sampleRows = jdbcTemplate.queryForList(
                    "SELECT id, agence, devise, compte, chapitre, libellecompte, idtiers, soldeorigine, soldeconvertie, devisebbnq, cumulmvtdb, cumulmvtcr, soldeinitdebmois, idcontrat, amount, actif FROM staging.stg_compta_raw WHERE " + whereClause + " LIMIT 5");
            log.info("Rule 3 (Type Check) - sample affected rows: {}", sampleRows);
        }

        return count;
    }

    /**
     * Rule 4: Calculate sum of soldeconvertie for all rows in the table.
     * Sums rows where soldeconvertie is a valid number (integer, decimal, or scientific notation).
     */
    private long calculateBalanceSum() {
        String sql = """
            SELECT COALESCE(SUM(soldeconvertie::DOUBLE PRECISION), 0)::BIGINT
            FROM staging.stg_compta_raw
            WHERE soldeconvertie IS NOT NULL
              AND soldeconvertie <> ''
              AND soldeconvertie ~ '^-?[0-9]+(\\.[0-9]+)?([eE][+-]?[0-9]+)?$'
            """;

        return jdbcTemplate.queryForObject(sql, Long.class);
    }

    /**
     * Set all blank empty strings to NULL across all text columns.
     */
    private int setBlankStringsToNull() {
        String sql = """
            UPDATE staging.stg_compta_raw
            SET
                agence = NULLIF(TRIM(agence), ''),
                devise = NULLIF(TRIM(devise), ''),
                compte = NULLIF(TRIM(compte), ''),
                chapitre = NULLIF(TRIM(chapitre), ''),
                libellecompte = NULLIF(TRIM(libellecompte), ''),
                idtiers = NULLIF(TRIM(idtiers), ''),
                soldeorigine = NULLIF(TRIM(soldeorigine), ''),
                soldeconvertie = NULLIF(TRIM(soldeconvertie), ''),
                devisebbnq = NULLIF(TRIM(devisebbnq), ''),
                cumulmvtdb = NULLIF(TRIM(cumulmvtdb), ''),
                cumulmvtcr = NULLIF(TRIM(cumulmvtcr), ''),
                soldeinitdebmois = NULLIF(TRIM(soldeinitdebmois), ''),
                idcontrat = NULLIF(TRIM(idcontrat), ''),
                amount = NULLIF(TRIM(amount), ''),
                actif = NULLIF(TRIM(actif), '')
            WHERE
                agence = '' OR devise = '' OR compte = '' OR chapitre = ''
                OR libellecompte = '' OR idtiers = '' OR soldeorigine = ''
                OR soldeconvertie = '' OR devisebbnq = '' OR cumulmvtdb = ''
                OR cumulmvtcr = '' OR soldeinitdebmois = '' OR idcontrat = ''
                OR amount = '' OR actif = ''
            """;

        return jdbcTemplate.update(sql);
    }

    /**
     * Rule 5a: Count rows where idcontrat is not null but does not exist in stg_contrat_raw.
     */
    private int countMissingContratRelations() {
        String sql = """
            SELECT COUNT(*) FROM staging.stg_compta_raw c
            WHERE c.idcontrat IS NOT NULL
              AND c.idcontrat <> ''
              AND NOT EXISTS (
                  SELECT 1 FROM staging.stg_contrat_raw cr
                  WHERE cr.idcontrat = c.idcontrat
              )
            """;
        return jdbcTemplate.queryForObject(sql, Integer.class);
    }

    /**
     * Rule 5b: Count rows where idtiers does not exist in stg_tiers_raw.
     * Logs a sample of 10 affected rows (id + idtiers).
     */
    private int countMissingTiersRelations() {
        String condition = """
            c.idtiers IS NOT NULL
              AND c.idtiers <> ''
              AND NOT EXISTS (
                  SELECT 1 FROM staging.stg_tiers_raw t
                  WHERE t.idtiers = c.idtiers
              )
            """;

        int count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM staging.stg_compta_raw c WHERE " + condition, Integer.class);

        if (count > 0) {
            java.util.List<java.util.Map<String, Object>> sample = jdbcTemplate.queryForList(
                    "SELECT c.id, c.idtiers FROM staging.stg_compta_raw c WHERE " + condition + " LIMIT 10");
            log.info("Rule 5b (Tiers Relation Check) - sample of affected rows (id, idtiers): {}", sample);
        }

        return count;
    }

    @lombok.Data
    public static class DataQualityResult {
        private int nullCheckCount;
        private int duplicateCount;
        private int typeCheckCount;
        private long balanceSum;
        private int contratRelationCheck;
        private int tiersRelationCheck;
        private int totalIssues;
    }
}
