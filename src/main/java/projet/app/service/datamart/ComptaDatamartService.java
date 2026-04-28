package projet.app.service.datamart;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds COMPTA datamart fact table and dimensions from staging.stg_compta_raw.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ComptaDatamartService {

    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public LoadResult loadComptaDatamart() {
        log.info("[COMPTA] Starting datamart load");

        log.info("[COMPTA] Ensuring datamart tables exist...");
        ensureDatamartTablesExist();
        log.info("[COMPTA] Datamart tables ready");

        log.info("[COMPTA] Populating sub_dim_agence...");
        int agenceRows = populateAgenceDimension();
        log.info("[COMPTA] sub_dim_agence done: {} rows", agenceRows);

        log.info("[COMPTA] Populating sub_dim_devise...");
        int deviseRows = populateDeviseDimension();
        log.info("[COMPTA] sub_dim_devise done: {} rows", deviseRows);

        log.info("[COMPTA] Populating sub_dim_chapitre...");
        int chapitreRows = populateChapitreDimension();
        log.info("[COMPTA] sub_dim_chapitre done: {} rows", chapitreRows);

        log.info("[COMPTA] Populating sub_dim_compte...");
        int compteRows = populateCompteDimension();
        log.info("[COMPTA] sub_dim_compte done: {} rows", compteRows);

        log.info("[COMPTA] Populating sub_dim_date...");
        int dateRows = populateDateDimension();
        log.info("[COMPTA] sub_dim_date done: {} rows", dateRows);

        long factCountBefore = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM datamart.fact_balance", Long.class);
        log.info("[COMPTA] fact_balance row count BEFORE insert: {}", factCountBefore);

        log.info("[COMPTA] Inserting into fact_balance (full-row uniqueness, skip exact duplicates)...");
        int factRows = populateFactBalance();
        log.info("[COMPTA] fact_balance insert done: {} rows affected", factRows);

        long factCountAfter = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM datamart.fact_balance", Long.class);
        log.info("[COMPTA] fact_balance row count AFTER insert: {} (delta={})", factCountAfter, factCountAfter - factCountBefore);

        LoadResult result = new LoadResult();
        result.setSubDimAgenceRows(agenceRows);
        result.setSubDimDeviseRows(deviseRows);
        result.setSubDimChapitreRows(chapitreRows);
        result.setSubDimCompteRows(compteRows);
        result.setSubDimDateRows(dateRows);
        result.setFactBalanceRows(factRows);

        log.info("[COMPTA] Datamart load completed: {}", result);
        return result;
    }


    @Transactional(readOnly = true)
    public Map<String, Object> fetchBalanceList(int page, int size) {
        int normalizedPage = Math.max(page, 0);
        int normalizedSize = size > 0 ? size : 20;
        int offset = normalizedPage * normalizedSize;

        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM datamart.fact_balance", Long.class);
        long totalElements = total == null ? 0L : total;

        List<Map<String, Object>> items = jdbcTemplate.queryForList("""
                SELECT
                    b.id,
                    b.id_client,
                    b.id_contrat,
                    b.soldeorigine,
                    b.soldeconvertie,
                    b.cumulmvtdb,
                    b.cumulmvtcr,
                    b.soldeinitdebmois,
                    b.amount,
                    b.actif,
                    a.numagence,
                    d.devise,
                    db.devise AS devisebanque,
                    c.libellecompte,
                    ch.chapitre,
                    dt.date_value AS datevalue
                FROM datamart.fact_balance b
                LEFT JOIN datamart.sub_dim_agence a ON a.id = b.id_agence
                LEFT JOIN datamart.sub_dim_devise d ON d.id = b.id_devise
                LEFT JOIN datamart.sub_dim_devise db ON db.id = b.id_devisebnq
                LEFT JOIN datamart.sub_dim_compte c ON c.id = b.id_compte
                LEFT JOIN datamart.sub_dim_chapitre ch ON ch.id = b.id_chapitre
                LEFT JOIN datamart.sub_dim_date dt ON dt.id = b.id_date
                ORDER BY b.id
                LIMIT ? OFFSET ?
                """, normalizedSize, offset);

        return buildPaginatedResponse(normalizedPage, normalizedSize, totalElements, items);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> fetchCompteList(int page, int size) {
        return fetchSimpleList(
                "datamart.sub_dim_compte",
                "SELECT id, numcompte, libellecompte FROM datamart.sub_dim_compte ORDER BY id LIMIT ? OFFSET ?",
                page,
                size
        );
    }

    @Transactional(readOnly = true)
    public Map<String, Object> fetchChapitreList(int page, int size) {
        return fetchSimpleList(
                "datamart.sub_dim_chapitre",
                "SELECT id, chapitre FROM datamart.sub_dim_chapitre ORDER BY id LIMIT ? OFFSET ?",
                page,
                size
        );
    }

    private Map<String, Object> fetchSimpleList(String tableName, String selectSql, int page, int size) {
        int normalizedPage = Math.max(page, 0);
        int normalizedSize = size > 0 ? size : 20;
        int offset = normalizedPage * normalizedSize;

        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName, Long.class);
        long totalElements = total == null ? 0L : total;

        List<Map<String, Object>> items = jdbcTemplate.queryForList(selectSql, normalizedSize, offset);
        return buildPaginatedResponse(normalizedPage, normalizedSize, totalElements, items);
    }

    private Map<String, Object> buildPaginatedResponse(int page, int size, long totalElements, List<Map<String, Object>> items) {
        long totalPages = size == 0 ? 0 : (long) Math.ceil((double) totalElements / size);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("page", page);
        response.put("size", size);
        response.put("totalElements", totalElements);
        response.put("totalPages", totalPages);
        response.put("items", items);

        return response;
    }

    private void ensureDatamartTablesExist() {
        jdbcTemplate.execute("CREATE SCHEMA IF NOT EXISTS datamart");

        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS datamart.sub_dim_agence (
                id BIGSERIAL PRIMARY KEY,
                numagence INTEGER UNIQUE
            )
            """);

        jdbcTemplate.execute("""
            CREATE UNIQUE INDEX IF NOT EXISTS ux_sub_dim_agence_numagence
            ON datamart.sub_dim_agence (numagence)
            """);

        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS datamart.sub_dim_devise (
                id BIGSERIAL PRIMARY KEY,
                devise TEXT UNIQUE
            )
            """);

        jdbcTemplate.execute("""
            CREATE UNIQUE INDEX IF NOT EXISTS ux_sub_dim_devise_devise
            ON datamart.sub_dim_devise (devise)
            """);

        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS datamart.sub_dim_date (
                id BIGSERIAL PRIMARY KEY,
                date_value DATE UNIQUE
            )
            """);

        jdbcTemplate.execute("""
            CREATE UNIQUE INDEX IF NOT EXISTS ux_sub_dim_date_date_value
            ON datamart.sub_dim_date (date_value)
            """);

        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS datamart.sub_dim_chapitre (
                id BIGSERIAL PRIMARY KEY,
                chapitre BIGINT UNIQUE
            )
            """);

        jdbcTemplate.execute("""
            CREATE UNIQUE INDEX IF NOT EXISTS ux_sub_dim_chapitre_chapitre
            ON datamart.sub_dim_chapitre (chapitre)
            """);

        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS datamart.sub_dim_compte (
                id BIGSERIAL PRIMARY KEY,
                numcompte BIGINT,
                libellecompte TEXT,
                CONSTRAINT uq_sub_dim_compte UNIQUE (numcompte, libellecompte)
            )
            """);

        // Migration: enforce one row per numcompte (libellecompte is descriptive metadata only).
        // Previously the table was declared UNIQUE (numcompte, libellecompte), which let a
        // single account end up with multiple rows when its libellé changed between months.
        // That in turn multiplied rows in fact_balance because populateFactBalance joins on
        // numcompte alone.
        migrateSubDimCompteToNumcompteUnique();

        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS datamart.fact_balance (
                id BIGSERIAL PRIMARY KEY,
                id_agence BIGINT REFERENCES datamart.sub_dim_agence(id),
                id_devise BIGINT REFERENCES datamart.sub_dim_devise(id),
                id_devisebnq BIGINT REFERENCES datamart.sub_dim_devise(id),
                id_compte BIGINT REFERENCES datamart.sub_dim_compte(id),
                id_chapitre BIGINT REFERENCES datamart.sub_dim_chapitre(id),
                id_client TEXT REFERENCES datamart.dim_client(idtiers),
                id_contrat TEXT REFERENCES datamart.dim_contrat(id),
                id_date BIGINT REFERENCES datamart.sub_dim_date(id),
                soldeorigine BIGINT,
                soldeconvertie NUMERIC(38,10),
                cumulmvtdb BIGINT,
                cumulmvtcr BIGINT,
                soldeinitdebmois BIGINT,
                amount BIGINT,
                actif INTEGER
            )
            """);

        jdbcTemplate.execute("""
            ALTER TABLE datamart.fact_balance
            ALTER COLUMN soldeconvertie TYPE NUMERIC(38,10)
            USING soldeconvertie::NUMERIC(38,10)
            """);

        jdbcTemplate.execute("DROP INDEX IF EXISTS datamart.ux_fact_balance_bk");
        jdbcTemplate.execute("DROP INDEX IF EXISTS datamart.ux_fact_balance_business_key");

        // One row per full inserted payload (all columns except surrogate id), including id_date and measures.
        jdbcTemplate.execute("""
            DELETE FROM datamart.fact_balance fb
            WHERE fb.id IN (
                SELECT id FROM (
                    SELECT
                        id,
                        ROW_NUMBER() OVER (
                            PARTITION BY
                                COALESCE(id_agence::TEXT, '__NULL__'),
                                COALESCE(id_devise::TEXT, '__NULL__'),
                                COALESCE(id_devisebnq::TEXT, '__NULL__'),
                                COALESCE(id_compte::TEXT, '__NULL__'),
                                COALESCE(id_client, '__NULL__'),
                                COALESCE(id_contrat, '__NULL__'),
                                COALESCE(id_chapitre::TEXT, '__NULL__'),
                                COALESCE(id_date::TEXT, '__NULL__'),
                                COALESCE(soldeorigine::TEXT, '__NULL__'),
                                COALESCE(soldeconvertie::TEXT, '__NULL__'),
                                COALESCE(cumulmvtdb::TEXT, '__NULL__'),
                                COALESCE(cumulmvtcr::TEXT, '__NULL__'),
                                COALESCE(soldeinitdebmois::TEXT, '__NULL__'),
                                COALESCE(amount::TEXT, '__NULL__'),
                                COALESCE(actif::TEXT, '__NULL__')
                            ORDER BY id
                        ) AS rn
                    FROM datamart.fact_balance
                ) d
                WHERE d.rn > 1
            )
            """);

        jdbcTemplate.execute("""
            CREATE UNIQUE INDEX IF NOT EXISTS ux_fact_balance_business_key
            ON datamart.fact_balance (
                COALESCE(id_agence::TEXT, '__NULL__'),
                COALESCE(id_devise::TEXT, '__NULL__'),
                COALESCE(id_devisebnq::TEXT, '__NULL__'),
                COALESCE(id_compte::TEXT, '__NULL__'),
                COALESCE(id_client, '__NULL__'),
                COALESCE(id_contrat, '__NULL__'),
                COALESCE(id_chapitre::TEXT, '__NULL__'),
                COALESCE(id_date::TEXT, '__NULL__'),
                COALESCE(soldeorigine::TEXT, '__NULL__'),
                COALESCE(soldeconvertie::TEXT, '__NULL__'),
                COALESCE(cumulmvtdb::TEXT, '__NULL__'),
                COALESCE(cumulmvtcr::TEXT, '__NULL__'),
                COALESCE(soldeinitdebmois::TEXT, '__NULL__'),
                COALESCE(amount::TEXT, '__NULL__'),
                COALESCE(actif::TEXT, '__NULL__')
            )
            """);
    }

    private int populateAgenceDimension() {
        String sql = """
            INSERT INTO datamart.sub_dim_agence (numagence)
            SELECT DISTINCT src.numagence
            FROM (
                SELECT NULLIF(TRIM(agence), '')::INTEGER AS numagence
                FROM staging.stg_compta_raw
                WHERE NULLIF(TRIM(agence), '') ~ '^-?[0-9]+$'
            ) src
            WHERE src.numagence IS NOT NULL
            ON CONFLICT (numagence) DO NOTHING
            """;
        return jdbcTemplate.update(sql);
    }

    private int populateDeviseDimension() {
        String sql = """
            INSERT INTO datamart.sub_dim_devise (devise)
            SELECT DISTINCT src.devise
            FROM (
                SELECT NULLIF(TRIM(devise), '') AS devise FROM staging.stg_compta_raw
                UNION ALL
                SELECT NULLIF(TRIM(devisebbnq), '') AS devise FROM staging.stg_compta_raw
            ) src
            WHERE src.devise IS NOT NULL
            ON CONFLICT (devise) DO NOTHING
            """;
        return jdbcTemplate.update(sql);
    }

    private int populateChapitreDimension() {
        String sql = """
            INSERT INTO datamart.sub_dim_chapitre (chapitre)
            SELECT DISTINCT NULLIF(TRIM(chapitre), '')::BIGINT AS chapitre
            FROM staging.stg_compta_raw
            WHERE NULLIF(TRIM(chapitre), '') ~ '^-?[0-9]+$'
            ON CONFLICT (chapitre) DO NOTHING
            """;
        return jdbcTemplate.update(sql);
    }

    private int populateCompteDimension() {
        String sql = """
            INSERT INTO datamart.sub_dim_compte (numcompte, libellecompte)
            SELECT
                NULLIF(TRIM(compte), '')::BIGINT AS numcompte,
                MAX(NULLIF(TRIM(libellecompte), '')) AS libellecompte
            FROM staging.stg_compta_raw
            WHERE NULLIF(TRIM(compte), '') ~ '^-?[0-9]+$'
            GROUP BY NULLIF(TRIM(compte), '')::BIGINT
            ON CONFLICT (numcompte) DO UPDATE SET
                libellecompte = COALESCE(EXCLUDED.libellecompte, datamart.sub_dim_compte.libellecompte)
            """;
        return jdbcTemplate.update(sql);
    }

    /**
     * One-time, idempotent migration that converts the historical
     * UNIQUE (numcompte, libellecompte) constraint on datamart.sub_dim_compte
     * into a UNIQUE (numcompte) constraint.
     *
     * Steps:
     *   1. For every numcompte that has multiple rows, keep the row with the smallest id
     *      and repoint any fact_balance.id_compte values that referenced the losing rows
     *      to the kept row.
     *   2. Delete the now-orphaned losing rows from sub_dim_compte.
     *   3. Drop the legacy compound uniqueness (constraint + index).
     *   4. Add a new uniqueness on numcompte alone.
     *
     * This is safe to run on every startup: when there are no duplicates the
     * UPDATE/DELETE statements simply affect zero rows and the constraints are
     * created with IF NOT EXISTS / IF EXISTS guards.
     */
    private void migrateSubDimCompteToNumcompteUnique() {
        // Repoint fact_balance.id_compte from "loser" duplicate rows to the kept row.
        int repointed = jdbcTemplate.update("""
            WITH winners AS (
                SELECT numcompte, MIN(id) AS keep_id
                FROM datamart.sub_dim_compte
                WHERE numcompte IS NOT NULL
                GROUP BY numcompte
                HAVING COUNT(*) > 1
            )
            UPDATE datamart.fact_balance fb
            SET id_compte = w.keep_id
            FROM datamart.sub_dim_compte sdc
            JOIN winners w ON w.numcompte = sdc.numcompte
            WHERE fb.id_compte = sdc.id
              AND sdc.id <> w.keep_id
            """);
        if (repointed > 0) {
            log.warn("[COMPTA migration] Repointed {} fact_balance rows from duplicate sub_dim_compte rows to the canonical row", repointed);
        }

        // Remove duplicate sub_dim_compte rows (keep the smallest id per numcompte).
        int deletedDuplicates = jdbcTemplate.update("""
            DELETE FROM datamart.sub_dim_compte
            WHERE id IN (
                SELECT id
                FROM (
                    SELECT id,
                           ROW_NUMBER() OVER (PARTITION BY numcompte ORDER BY id) AS rn
                    FROM datamart.sub_dim_compte
                    WHERE numcompte IS NOT NULL
                ) d
                WHERE d.rn > 1
            )
            """);
        if (deletedDuplicates > 0) {
            log.warn("[COMPTA migration] Deleted {} duplicate sub_dim_compte rows that violated the new numcompte-unique invariant", deletedDuplicates);
        }

        // Drop the legacy compound uniqueness.
        jdbcTemplate.execute("ALTER TABLE datamart.sub_dim_compte DROP CONSTRAINT IF EXISTS uq_sub_dim_compte");
        jdbcTemplate.execute("DROP INDEX IF EXISTS datamart.ux_sub_dim_compte_num_libelle");

        // Enforce uniqueness on numcompte alone going forward.
        jdbcTemplate.execute("""
            CREATE UNIQUE INDEX IF NOT EXISTS ux_sub_dim_compte_numcompte
            ON datamart.sub_dim_compte (numcompte)
            """);
    }

    private int populateDateDimension() {
        String parseDateExpr = "CASE " +
                "WHEN NULLIF(TRIM(src.raw_date), '') ~ '^[0-9]{2}/[0-9]{2}/[0-9]{4}$' THEN TO_DATE(NULLIF(TRIM(src.raw_date), ''), 'DD/MM/YYYY') " +
                "WHEN NULLIF(TRIM(src.raw_date), '') ~ '^[0-9]{4}-[0-9]{2}-[0-9]{2}$' THEN NULLIF(TRIM(src.raw_date), '')::DATE " +
                "ELSE NULL END";

        String sql = """
            INSERT INTO datamart.sub_dim_date (date_value)
            SELECT DISTINCT %s AS date_value
            FROM (
                SELECT date_bal AS raw_date FROM staging.stg_compta_raw
            ) src
            WHERE %s IS NOT NULL
            ON CONFLICT (date_value) DO NOTHING
            """.formatted(parseDateExpr, parseDateExpr);

        return jdbcTemplate.update(sql);
    }

    private String cleanNumericExpr(String col) {
        return "REPLACE(REGEXP_REPLACE(NULLIF(TRIM(" + col + "), ''), '[\\s\\u00A0]', '', 'g'), ',', '.')";
    }

    private int populateFactBalance() {
        String parseDateExpr = parseDateExpression("date_bal");

        jdbcTemplate.execute("DROP TABLE IF EXISTS staging.tmp_compta_prep");
        jdbcTemplate.execute("""
            CREATE TABLE staging.tmp_compta_prep AS
            SELECT
                CASE WHEN NULLIF(TRIM(t.agence), '') ~ '^-?[0-9]+$'
                     THEN NULLIF(TRIM(t.agence), '')::INTEGER END AS numagence,
                NULLIF(TRIM(t.devise), '') AS devise,
                NULLIF(TRIM(t.devisebbnq), '') AS devisebbnq,
                CASE WHEN NULLIF(TRIM(t.compte), '') ~ '^-?[0-9]+$'
                     THEN NULLIF(TRIM(t.compte), '')::BIGINT END AS numcompte,
                CASE WHEN NULLIF(TRIM(t.chapitre), '') ~ '^-?[0-9]+$'
                     THEN NULLIF(TRIM(t.chapitre), '')::BIGINT END AS chapitre,
                NULLIF(TRIM(t.idtiers), '') AS idtiers,
                NULLIF(TRIM(t.idcontrat), '') AS idcontrat,
                %s AS date_val,
                %s AS soldeorigine,
                %s AS soldeconvertie,
                %s AS cumulmvtdb,
                %s AS cumulmvtcr,
                %s AS soldeinitdebmois,
                %s AS amount,
                CASE WHEN NULLIF(TRIM(t.actif), '') ~ '^-?[0-9]+$'
                     THEN NULLIF(TRIM(t.actif), '')::INTEGER END AS actif
            FROM staging.stg_compta_raw t
            """.formatted(
                parseDateExpr,
                cleanLongExpr("t.soldeorigine"),
                cleanDecimalExpr("t.soldeconvertie"),
                cleanLongExpr("t.cumulmvtdb"),
                cleanLongExpr("t.cumulmvtcr"),
                cleanLongExpr("t.soldeinitdebmois"),
                cleanLongExpr("t.amount")
            ));

        log.info("[COMPTA] tmp_compta_prep created, adding indexes...");
        jdbcTemplate.execute("CREATE INDEX ON staging.tmp_compta_prep (idtiers)");
        jdbcTemplate.execute("CREATE INDEX ON staging.tmp_compta_prep (idcontrat)");
        jdbcTemplate.execute("CREATE INDEX ON staging.tmp_compta_prep (numcompte)");
        jdbcTemplate.execute("CREATE INDEX ON staging.tmp_compta_prep (chapitre)");
        log.info("[COMPTA] tmp_compta_prep indexes ready, inserting into fact_balance...");

        String insertSql = """
            INSERT INTO datamart.fact_balance (
                id_agence, id_devise, id_devisebnq, id_compte, id_chapitre,
                id_client, id_contrat, id_date,
                soldeorigine, soldeconvertie, cumulmvtdb, cumulmvtcr,
                soldeinitdebmois, amount, actif
            )
            SELECT
                a.id, d.id, db.id, c.id, ch.id,
                dc.idtiers, dct.id, dt.id,
                p.soldeorigine, p.soldeconvertie, p.cumulmvtdb, p.cumulmvtcr,
                p.soldeinitdebmois, p.amount, p.actif
            FROM staging.tmp_compta_prep p
            LEFT JOIN datamart.sub_dim_agence a ON a.numagence = p.numagence
            LEFT JOIN datamart.sub_dim_devise d ON d.devise = p.devise
            LEFT JOIN datamart.sub_dim_devise db ON db.devise = p.devisebbnq
            LEFT JOIN datamart.sub_dim_compte c ON c.numcompte = p.numcompte
            LEFT JOIN datamart.sub_dim_chapitre ch ON ch.chapitre = p.chapitre
            LEFT JOIN datamart.dim_client dc ON dc.idtiers = p.idtiers
            LEFT JOIN datamart.dim_contrat dct ON dct.id = p.idcontrat
            LEFT JOIN datamart.sub_dim_date dt ON dt.date_value = p.date_val
            ON CONFLICT DO NOTHING
            """;

        int rows = jdbcTemplate.update(insertSql);
        jdbcTemplate.execute("DROP TABLE IF EXISTS staging.tmp_compta_prep");
        return rows;
    }

    private String cleanLongExpr(String col) {
        String cleaned = cleanNumericExpr(col);
        return "CASE WHEN " + cleaned + " ~ '^-?[0-9]+(\\.[0-9]+)?([eE][+-]?[0-9]+)?$' " +
                "THEN (" + cleaned + "::DOUBLE PRECISION)::BIGINT ELSE NULL END";
    }

    private String cleanDecimalExpr(String col) {
        String cleaned = cleanNumericExpr(col);
        return "CASE WHEN NULLIF(TRIM(" + col + "), '') IS NULL THEN NULL " +
                "ELSE " + cleaned + "::DOUBLE PRECISION::NUMERIC(38,10) END";
    }

    private String parseDateExpression(String columnExpr) {
        return "CASE " +
                "WHEN NULLIF(TRIM(" + columnExpr + "), '') ~ '^[0-9]{2}/[0-9]{2}/[0-9]{4}$' THEN TO_DATE(NULLIF(TRIM(" + columnExpr + "), ''), 'DD/MM/YYYY') " +
                "WHEN NULLIF(TRIM(" + columnExpr + "), '') ~ '^[0-9]{4}-[0-9]{2}-[0-9]{2}$' THEN NULLIF(TRIM(" + columnExpr + "), '')::DATE " +
                "ELSE NULL END";
    }

    @Data
    public static class LoadResult {
        private int subDimAgenceRows;
        private int subDimDeviseRows;
        private int subDimChapitreRows;
        private int subDimCompteRows;
        private int subDimDateRows;
        private int factBalanceRows;
    }
}
