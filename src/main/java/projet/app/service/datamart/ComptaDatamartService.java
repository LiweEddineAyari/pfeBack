package projet.app.service.datamart;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        ensureDatamartTablesExist();

        int agenceRows = populateAgenceDimension();
        int deviseRows = populateDeviseDimension();
        int chapitreRows = populateChapitreDimension();
        int compteRows = populateCompteDimension();
        int dateRows = populateDateDimension();
        int factRows = populateFactBalance();

        LoadResult result = new LoadResult();
        result.setSubDimAgenceRows(agenceRows);
        result.setSubDimDeviseRows(deviseRows);
        result.setSubDimChapitreRows(chapitreRows);
        result.setSubDimCompteRows(compteRows);
        result.setSubDimDateRows(dateRows);
        result.setFactBalanceRows(factRows);

        log.info("Compta datamart load completed: {}", result);
        return result;
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

        jdbcTemplate.execute("""
            CREATE UNIQUE INDEX IF NOT EXISTS ux_sub_dim_compte_num_libelle
            ON datamart.sub_dim_compte (numcompte, libellecompte)
            """);

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
                soldeconvertie BIGINT,
                cumulmvtdb BIGINT,
                cumulmvtcr BIGINT,
                soldeinitdebmois BIGINT,
                amount BIGINT,
                actif INTEGER
            )
            """);

        // If duplicates were inserted before this rule existed, keep only the first row per business key.
        jdbcTemplate.execute("""
            DELETE FROM datamart.fact_balance fb
            WHERE fb.id IN (
                SELECT id FROM (
                    SELECT
                        id,
                        ROW_NUMBER() OVER (
                            PARTITION BY
                                COALESCE(id_client, '__NULL__'),
                                COALESCE(id_contrat, '__NULL__'),
                                COALESCE(id_chapitre, -1),
                                COALESCE(id_date, -1)
                            ORDER BY id
                        ) AS rn
                    FROM datamart.fact_balance
                ) d
                WHERE d.rn > 1
            )
            """);

        // Business key uniqueness for balance facts.
        jdbcTemplate.execute("""
            CREATE UNIQUE INDEX IF NOT EXISTS ux_fact_balance_business_key
            ON datamart.fact_balance (
                COALESCE(id_client, '__NULL__'),
                COALESCE(id_contrat, '__NULL__'),
                COALESCE(id_chapitre, -1),
                COALESCE(id_date, -1)
            )
            """);

        // Normalize historical data to one row per business duplicate key.
        jdbcTemplate.execute("""
            DELETE FROM datamart.fact_balance fb
            USING (
                SELECT id
                FROM (
                    SELECT
                        id,
                        ROW_NUMBER() OVER (
                            PARTITION BY id_client, id_contrat, id_chapitre, id_date
                            ORDER BY id
                        ) AS rn
                    FROM datamart.fact_balance
                ) ranked
                WHERE ranked.rn > 1
            ) dup
            WHERE fb.id = dup.id
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
            SELECT DISTINCT src.chapitre
            FROM (
                SELECT NULLIF(TRIM(chapitre), '')::BIGINT AS chapitre
                FROM staging.stg_compta_raw
                WHERE NULLIF(TRIM(chapitre), '') ~ '^-?[0-9]+$'
            ) src
            WHERE src.chapitre IS NOT NULL
              AND NOT EXISTS (
                  SELECT 1
                  FROM datamart.sub_dim_chapitre ch
                  WHERE ch.chapitre = src.chapitre
              )
            """;
        return jdbcTemplate.update(sql);
    }

    private int populateCompteDimension() {
        String sql = """
            INSERT INTO datamart.sub_dim_compte (numcompte, libellecompte)
            SELECT
                src.numcompte,
                src.libellecompte
            FROM (
                SELECT
                    NULLIF(TRIM(compte), '')::BIGINT AS numcompte,
                    MAX(NULLIF(TRIM(libellecompte), '')) AS libellecompte
                FROM staging.stg_compta_raw
                WHERE NULLIF(TRIM(compte), '') ~ '^-?[0-9]+$'
                GROUP BY NULLIF(TRIM(compte), '')::BIGINT
            ) src
            WHERE src.numcompte IS NOT NULL
              AND NOT EXISTS (
                  SELECT 1
                  FROM datamart.sub_dim_compte c
                  WHERE c.numcompte = src.numcompte
              )
            """;
        return jdbcTemplate.update(sql);
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

    private int populateFactBalance() {
        String parseDateExpr = parseDateExpression("t.date_bal");
        String parseLongExpr = "CASE WHEN NULLIF(TRIM(%s), '') ~ '^-?[0-9]+(\\\\.[0-9]+)?([eE][+-]?[0-9]+)?$' " +
                "THEN (NULLIF(TRIM(%s), '')::DOUBLE PRECISION)::BIGINT ELSE NULL END";

        String sql = """
            WITH src AS (
                SELECT
                    a.id AS id_agence,
                    d.id AS id_devise,
                    db.id AS id_devisebnq,
                    c.id AS id_compte,
                    ch.id AS id_chapitre,
                    dc.idtiers AS id_client,
                    dct.id AS id_contrat,
                    dt.id AS id_date,
                    %s AS soldeorigine,
                    %s AS soldeconvertie,
                    %s AS cumulmvtdb,
                    %s AS cumulmvtcr,
                    %s AS soldeinitdebmois,
                    %s AS amount,
                    CASE
                        WHEN NULLIF(TRIM(t.actif), '') ~ '^-?[0-9]+$' THEN NULLIF(TRIM(t.actif), '')::INTEGER
                        ELSE NULL
                    END AS actif
                FROM staging.stg_compta_raw t
                LEFT JOIN datamart.sub_dim_agence a
                       ON a.numagence = CASE
                                            WHEN NULLIF(TRIM(t.agence), '') ~ '^-?[0-9]+$' THEN NULLIF(TRIM(t.agence), '')::INTEGER
                                            ELSE NULL
                                        END
                LEFT JOIN datamart.sub_dim_devise d
                       ON d.devise = NULLIF(TRIM(t.devise), '')
                LEFT JOIN datamart.sub_dim_devise db
                       ON db.devise = NULLIF(TRIM(t.devisebbnq), '')
                LEFT JOIN (
                    SELECT numcompte, MIN(id) AS id
                    FROM datamart.sub_dim_compte
                    GROUP BY numcompte
                ) c
                       ON c.numcompte = CASE
                                            WHEN NULLIF(TRIM(t.compte), '') ~ '^-?[0-9]+$' THEN NULLIF(TRIM(t.compte), '')::BIGINT
                                            ELSE NULL
                                        END
                LEFT JOIN (
                    SELECT chapitre, MIN(id) AS id
                    FROM datamart.sub_dim_chapitre
                    GROUP BY chapitre
                ) ch
                       ON ch.chapitre = CASE
                                            WHEN NULLIF(TRIM(t.chapitre), '') ~ '^-?[0-9]+$' THEN NULLIF(TRIM(t.chapitre), '')::BIGINT
                                            ELSE NULL
                                        END
                LEFT JOIN datamart.dim_client dc
                       ON dc.idtiers = NULLIF(TRIM(t.idtiers), '')
                LEFT JOIN datamart.dim_contrat dct
                       ON dct.id = NULLIF(TRIM(t.idcontrat), '')
                LEFT JOIN datamart.sub_dim_date dt
                       ON dt.date_value = %s
            )
            INSERT INTO datamart.fact_balance (
                id_agence,
                id_devise,
                id_devisebnq,
                id_compte,
                id_chapitre,
                id_client,
                id_contrat,
                id_date,
                soldeorigine,
                soldeconvertie,
                cumulmvtdb,
                cumulmvtcr,
                soldeinitdebmois,
                amount,
                actif
            )
            SELECT
                src.id_agence,
                src.id_devise,
                src.id_devisebnq,
                src.id_compte,
                src.id_chapitre,
                src.id_client,
                src.id_contrat,
                src.id_date,
                src.soldeorigine,
                src.soldeconvertie,
                src.cumulmvtdb,
                src.cumulmvtcr,
                src.soldeinitdebmois,
                src.amount,
                src.actif
            FROM src
            ON CONFLICT DO NOTHING
            """.formatted(
                parseLongExpr.formatted("t.soldeorigine", "t.soldeorigine"),
                parseLongExpr.formatted("t.soldeconvertie", "t.soldeconvertie"),
                parseLongExpr.formatted("t.cumulmvtdb", "t.cumulmvtdb"),
                parseLongExpr.formatted("t.cumulmvtcr", "t.cumulmvtcr"),
                parseLongExpr.formatted("t.soldeinitdebmois", "t.soldeinitdebmois"),
                parseLongExpr.formatted("t.amount", "t.amount"),
                parseDateExpr
            );

        return jdbcTemplate.update(sql);
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
