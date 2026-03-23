package projet.app.service.quality;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import projet.app.entity.quality.DataQualityResultContrat;
import projet.app.repository.quality.DataQualityResultContratRepository;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContratDataQualityService {

    private final JdbcTemplate jdbcTemplate;
    private final DataQualityResultContratRepository resultRepository;

    @Transactional
    public DataQualityResult cleanStagingTable() {
        log.info("Starting data quality checks on stg_contrat_raw");

        DataQualityResult result = new DataQualityResult();

        // Rule 1: Null check - delete rows where ANY column is null
        int nullsDeleted = deleteRowsWithNullValues();
        result.setNullCheckDeletedCount(nullsDeleted);
        log.info("Rule 1 (Null Check): Deleted {} rows with null values", nullsDeleted);

        // Rule 2: Duplicate check - delete duplicate rows by idcontrat
        int duplicatesDeleted = deleteDuplicateRows();
        result.setDuplicateDeletedCount(duplicatesDeleted);
        log.info("Rule 2 (Duplicate Check): Deleted {} duplicate rows by idcontrat", duplicatesDeleted);

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
        DataQualityResultContrat savedResult = saveResultToDatabase(result);
        log.info("Data quality result saved with id: {}", savedResult.getId());

        return result;
    }

    private DataQualityResultContrat saveResultToDatabase(DataQualityResult result) {
        DataQualityResultContrat entity = DataQualityResultContrat.builder()
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
     * Rule 1: Delete rows where ANY column is null (all columns must be not null).
     */
    private int deleteRowsWithNullValues() {
        String sql = """
            DELETE FROM staging.stg_contrat_raw
            WHERE agence IS NULL
               OR devise IS NULL
               OR idcontrat IS NULL
               OR ancienneteimpaye IS NULL
               OR typcontrat IS NULL
               OR datouv IS NULL
               OR datech IS NULL
               OR idtiers IS NULL
               OR tauxcontrat IS NULL
               OR actif IS NULL
            """;
        return jdbcTemplate.update(sql);
    }

    /**
     * Rule 2: Delete duplicate rows by idcontrat, keeping the first occurrence (lowest id).
     */
    private int deleteDuplicateRows() {
        String sql = """
            DELETE FROM staging.stg_contrat_raw
            WHERE id IN (
                SELECT id FROM (
                    SELECT id, ROW_NUMBER() OVER (PARTITION BY idcontrat ORDER BY id) as rn
                    FROM staging.stg_contrat_raw
                    WHERE idcontrat IS NOT NULL
                ) duplicates
                WHERE rn > 1
            )
            """;

        return jdbcTemplate.update(sql);
    }

    /**
     * Rule 3: Type validation.
     *
     * Integer columns (must be valid integers):
     *   agence, ancienneteimpaye, objetfinance, idtiers, tauxcontrat, actif
     *
     * String columns (must NOT be numeric):
     *   devise, idcontrat, typcontrat
     *
     * Date columns (must be valid date format):
     *   datouv, datech
     */
    private int deleteRowsWithInvalidTypes() {
        String sql = """
            DELETE FROM staging.stg_contrat_raw
            WHERE
                -- Integer columns must BE numeric (delete if not numbers, allow NULL and empty)
                (agence IS NOT NULL AND agence <> '' AND agence !~ '^-?[0-9]+$')
                OR (ancienneteimpaye IS NOT NULL AND ancienneteimpaye <> '' AND ancienneteimpaye !~ '^-?[0-9]+$')
                OR (objetfinance IS NOT NULL AND objetfinance <> '' AND objetfinance !~ '^-?[0-9]+$')
                OR (idtiers IS NOT NULL AND idtiers <> '' AND idtiers !~ '^-?[0-9]+$')
                OR (tauxcontrat IS NOT NULL AND tauxcontrat <> '' AND tauxcontrat !~ '^-?[0-9]+$')
                OR (actif IS NOT NULL AND actif <> '' AND actif !~ '^-?[0-9]+$')
                -- String columns must NOT be numeric (delete if they are numbers)
                OR (devise IS NOT NULL AND devise <> '' AND devise ~ '^-?[0-9]+\\.?[0-9]*$')
                OR (idcontrat IS NOT NULL AND idcontrat <> '' AND idcontrat ~ '^-?[0-9]+\\.?[0-9]*$')
                OR (typcontrat IS NOT NULL AND typcontrat <> '' AND typcontrat ~ '^-?[0-9]+\\.?[0-9]*$')
                -- Date columns must be valid date format (yyyy-MM-dd or dd/MM/yyyy or similar)
                OR (datouv IS NOT NULL AND datouv <> '' AND datouv !~ '^[0-9]{4}-[0-9]{2}-[0-9]{2}' AND datouv !~ '^[0-9]{2}/[0-9]{2}/[0-9]{4}')
                OR (datech IS NOT NULL AND datech <> '' AND datech !~ '^[0-9]{4}-[0-9]{2}-[0-9]{2}' AND datech !~ '^[0-9]{2}/[0-9]{2}/[0-9]{4}')
            """;

        return jdbcTemplate.update(sql);
    }

    /**
     * Set all blank empty strings to NULL across all text columns.
     */
    private int setBlankStringsToNull() {
        String sql = """
            UPDATE staging.stg_contrat_raw
            SET
                agence = NULLIF(TRIM(agence), ''),
                devise = NULLIF(TRIM(devise), ''),
                idcontrat = NULLIF(TRIM(idcontrat), ''),
                ancienneteimpaye = NULLIF(TRIM(ancienneteimpaye), ''),
                objetfinance = NULLIF(TRIM(objetfinance), ''),
                typcontrat = NULLIF(TRIM(typcontrat), ''),
                datouv = NULLIF(TRIM(datouv), ''),
                datech = NULLIF(TRIM(datech), ''),
                idtiers = NULLIF(TRIM(idtiers), ''),
                tauxcontrat = NULLIF(TRIM(tauxcontrat), ''),
                actif = NULLIF(TRIM(actif), '')
            WHERE
                agence = '' OR devise = '' OR idcontrat = '' OR ancienneteimpaye = ''
                OR objetfinance = '' OR typcontrat = '' OR datouv = '' OR datech = ''
                OR idtiers = '' OR tauxcontrat = '' OR actif = ''
            """;

        return jdbcTemplate.update(sql);
    }

    @lombok.Data
    public static class DataQualityResult {
        private int nullCheckDeletedCount;
        private int duplicateDeletedCount;
        private int typeCheckDeletedCount;
        private int totalDeletedCount;
    }
}
