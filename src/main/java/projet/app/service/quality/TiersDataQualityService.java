package projet.app.service.quality;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import projet.app.entity.quality.DataQualityResultTiers;
import projet.app.repository.quality.DataQualityResultTiersRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Data Quality Service for STG_TIERS_RAW staging table.
 * Performs validation and cleaning operations directly in PostgreSQL.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TiersDataQualityService {

    private final JdbcTemplate jdbcTemplate;
    private final DataQualityResultTiersRepository resultRepository;

    /**
     * Execute all data quality checks and cleaning operations on stg_tiers_raw.
     * 
     * @return DataQualityResult containing counts of deleted rows per rule
     */
    @Transactional
    public DataQualityResult cleanStagingTable() {
        log.info("Starting data quality checks on stg_tiers_raw");
        
        DataQualityResult result = new DataQualityResult();
        
        // Rule 1: Null check - delete rows with null residence or agenteco
        int nullsDeleted = deleteRowsWithNullValues();
        result.setNullCheckDeletedCount(nullsDeleted);
        log.info("Rule 1 (Null Check): Deleted {} rows with null residence or agenteco", nullsDeleted);
        
        // Rule 2: Duplicate check - delete duplicate rows by idtiers
        int duplicatesDeleted = deleteDuplicateRows();
        result.setDuplicateDeletedCount(duplicatesDeleted);
        log.info("Rule 2 (Duplicate Check): Deleted {} duplicate rows by idtiers", duplicatesDeleted);
        
        // Rule 3: Type validation - delete rows with invalid data types
        int invalidTypesDeleted = deleteRowsWithInvalidTypes();
        result.setTypeCheckDeletedCount(invalidTypesDeleted);
        log.info("Rule 3 (Type Check): Deleted {} rows with invalid data types", invalidTypesDeleted);
        
        int totalDeleted = nullsDeleted + duplicatesDeleted + invalidTypesDeleted;
        result.setTotalDeletedCount(totalDeleted);
        log.info("Data quality checks completed. Total rows deleted: {}", totalDeleted);

        // Set all blank strings to NULL
        int blanksFixed = setBlankStringsToNull();
        log.info("Set {} blank string values to NULL", blanksFixed);
        
        // Save result to database
        DataQualityResultTiers savedResult = saveResultToDatabase(result);
        log.info("Data quality result saved with id: {}", savedResult.getId());
        
        return result;
    }

    /**
     * Save data quality result to database.
     */
    private DataQualityResultTiers saveResultToDatabase(DataQualityResult result) {
        DataQualityResultTiers entity = DataQualityResultTiers.builder()
                .nullCheckDeleted(result.getNullCheckDeletedCount())
                .duplicateDeleted(result.getDuplicateDeletedCount())
                .typeCheckDeleted(result.getTypeCheckDeletedCount())
                .totalDeleted(result.getTotalDeletedCount())
                .status("COMPLETED")
                .executedAt(LocalDateTime.now())
                .build();
        
        return resultRepository.save(entity);
    }

    /**
     * Fetch rows that violate Rule 1 (required NULL values).
     */
    public List<Map<String, Object>> fetchNullCheckList() {
        String sql = """
            SELECT id, idtiers, nomprenom, raisonsoc, residence, agenteco,
                   sectionactivite, chiffreaffaires, nationalite, douteux,
                   datdouteux, nomgrpaffaires
            FROM staging.stg_tiers_raw
            WHERE residence IS NULL
               OR agenteco IS NULL
            ORDER BY id
            """;
        return jdbcTemplate.queryForList(sql);
    }

    /**
     * Fetch rows that violate Rule 2 (duplicate idtiers, keeping first occurrence).
     */
    public List<Map<String, Object>> fetchDuplicateList() {
        String sql = """
            SELECT id, idtiers, nomprenom, raisonsoc, residence, agenteco,
                   sectionactivite, chiffreaffaires, nationalite, douteux,
                   datdouteux, nomgrpaffaires
            FROM (
                SELECT id, idtiers, nomprenom, raisonsoc, residence, agenteco,
                       sectionactivite, chiffreaffaires, nationalite, douteux,
                       datdouteux, nomgrpaffaires,
                       ROW_NUMBER() OVER (PARTITION BY idtiers ORDER BY id) as rn
                FROM staging.stg_tiers_raw
                WHERE idtiers IS NOT NULL
            ) duplicates
            WHERE rn > 1
            ORDER BY id
            """;
        return jdbcTemplate.queryForList(sql);
    }

    /**
     * Fetch rows that violate Rule 3 (invalid data types).
     */
    public List<Map<String, Object>> fetchTypeCheckList() {
        String sql = """
            SELECT id, idtiers, nomprenom, raisonsoc, residence, agenteco,
                   sectionactivite, chiffreaffaires, nationalite, douteux,
                   datdouteux, nomgrpaffaires
            FROM staging.stg_tiers_raw
            WHERE
                (nomprenom IS NOT NULL AND nomprenom <> '' AND nomprenom ~ '^-?[0-9]+\\.?[0-9]*$')
                OR (raisonsoc IS NOT NULL AND raisonsoc <> '' AND raisonsoc ~ '^-?[0-9]+\\.?[0-9]*$')
                OR (residence IS NOT NULL AND residence <> '' AND residence ~ '^-?[0-9]+\\.?[0-9]*$')
                OR (nomgrpaffaires IS NOT NULL AND nomgrpaffaires <> '' AND nomgrpaffaires ~ '^-?[0-9]+\\.?[0-9]*$')
                OR (agenteco IS NOT NULL AND agenteco <> '' AND agenteco !~ '^-?[0-9]+$')
                OR (sectionactivite IS NOT NULL AND sectionactivite <> '' AND sectionactivite !~ '^-?[0-9]+$')
                OR (idtiers IS NOT NULL AND idtiers <> '' AND idtiers !~ '^-?[0-9]+$')
            ORDER BY id
            """;
        return jdbcTemplate.queryForList(sql);
    }

    /**
     * Rule 1: Delete rows where residence or agenteco is NULL.
     */
    private int deleteRowsWithNullValues() {
        String sql = """
            DELETE FROM staging.stg_tiers_raw
            WHERE residence IS NULL 
               OR agenteco IS NULL
            """;
        
        return jdbcTemplate.update(sql);
    }

    /**
     * Rule 2: Delete duplicate rows by idtiers, keeping the first occurrence (lowest id).
     * Uses efficient CTE with ROW_NUMBER() instead of slow NOT IN subquery.
     */
    private int deleteDuplicateRows() {
        String sql = """
            DELETE FROM staging.stg_tiers_raw
            WHERE id IN (
                SELECT id FROM (
                    SELECT id, ROW_NUMBER() OVER (PARTITION BY idtiers ORDER BY id) as rn
                    FROM staging.stg_tiers_raw
                    WHERE idtiers IS NOT NULL
                ) duplicates
                WHERE rn > 1
            )
            """;
        
        return jdbcTemplate.update(sql);
    }

    /**
     * Rule 3: Type validation - fix nationalite and delete rows with invalid data types.
     * 
     * String columns (must NOT be numeric values):
     *   nomprenom, raisonsoc, residence, nomgrpaffaires
     *   Exception: nationalite - if null or numeric, set to residence value
     * 
     * Integer columns (must be valid integers):
     *   agenteco, sectionactivite, chiffreaffaires, douteux, idtiers
     */
    private int deleteRowsWithInvalidTypes() {
        // First: Fix nationalite - if null or empty or looks like a number, set it to residence
        String fixNationaliteSql = """
            UPDATE staging.stg_tiers_raw
            SET nationalite = residence
            WHERE nationalite IS NULL 
               OR nationalite = ''
               OR nationalite ~ '^-?[0-9]+\\.?[0-9]*$'
            """;
        int nationaliteFixed = jdbcTemplate.update(fixNationaliteSql);
        log.info("Fixed {} rows where nationalite was null or numeric (set to residence)", nationaliteFixed);

        // Fix chiffreaffaires - if null, empty or not numeric, set to '0'
        String fixChiffreAffairesSql = """
            UPDATE staging.stg_tiers_raw
            SET chiffreaffaires = '0'
            WHERE chiffreaffaires IS NULL
               OR chiffreaffaires = ''
               OR chiffreaffaires !~ '^-?[0-9]+$'
            """;
        int chiffreAffairesFixed = jdbcTemplate.update(fixChiffreAffairesSql);
        log.info("Fixed {} rows where chiffreaffaires was null or non-numeric (set to 0)", chiffreAffairesFixed);

        // Fix douteux - if null, empty or not numeric, set to '0'
        String fixDouteuxSql = """
            UPDATE staging.stg_tiers_raw
            SET douteux = '0'
            WHERE douteux IS NULL
               OR douteux = ''
               OR douteux !~ '^-?[0-9]+$'
            """;
        int douteuxFixed = jdbcTemplate.update(fixDouteuxSql);
        log.info("Fixed {} rows where douteux was null or non-numeric (set to 0)", douteuxFixed);
        
        // Delete rows with invalid types
        // Note: Also check for empty string '' since it's different from NULL
        String sql = """
            DELETE FROM staging.stg_tiers_raw
            WHERE 
                -- String columns must NOT be numeric (delete if they are numbers)
                (nomprenom IS NOT NULL AND nomprenom <> '' AND nomprenom ~ '^-?[0-9]+\\.?[0-9]*$')
                OR (raisonsoc IS NOT NULL AND raisonsoc <> '' AND raisonsoc ~ '^-?[0-9]+\\.?[0-9]*$')
                OR (residence IS NOT NULL AND residence <> '' AND residence ~ '^-?[0-9]+\\.?[0-9]*$')
                OR (nomgrpaffaires IS NOT NULL AND nomgrpaffaires <> '' AND nomgrpaffaires ~ '^-?[0-9]+\\.?[0-9]*$')
                -- Integer columns must BE numeric (delete if they are not numbers, but allow NULL and empty)
                OR (agenteco IS NOT NULL AND agenteco <> '' AND agenteco !~ '^-?[0-9]+$')
                OR (sectionactivite IS NOT NULL AND sectionactivite <> '' AND sectionactivite !~ '^-?[0-9]+$')
                OR (idtiers IS NOT NULL AND idtiers <> '' AND idtiers !~ '^-?[0-9]+$')
            """;
        
        return jdbcTemplate.update(sql);
    }

    /**
     * Set all blank empty strings to NULL across all text columns.
     */
    private int setBlankStringsToNull() {
        String sql = """
            UPDATE staging.stg_tiers_raw
            SET
                idtiers = NULLIF(TRIM(idtiers), ''),
                nomprenom = NULLIF(TRIM(nomprenom), ''),
                raisonsoc = NULLIF(TRIM(raisonsoc), ''),
                residence = NULLIF(TRIM(residence), ''),
                agenteco = NULLIF(TRIM(agenteco), ''),
                sectionactivite = NULLIF(TRIM(sectionactivite), ''),
                chiffreaffaires = NULLIF(TRIM(chiffreaffaires), ''),
                nationalite = NULLIF(TRIM(nationalite), ''),
                douteux = NULLIF(TRIM(douteux), ''),
                datdouteux = NULLIF(TRIM(datdouteux), ''),
                nomgrpaffaires = NULLIF(TRIM(nomgrpaffaires), '')
            WHERE
                idtiers = '' OR nomprenom = '' OR raisonsoc = '' OR residence = ''
                OR agenteco = '' OR sectionactivite = '' OR chiffreaffaires = ''
                OR nationalite = '' OR douteux = '' OR datdouteux = ''
                OR nomgrpaffaires = ''
            """;

        return jdbcTemplate.update(sql);
    }

    /**
     * Result class for data quality operations.
     */
    @lombok.Data
    public static class DataQualityResult {
        private int nullCheckDeletedCount;
        private int duplicateDeletedCount;
        private int typeCheckDeletedCount;
        private int totalDeletedCount;
    }
}
