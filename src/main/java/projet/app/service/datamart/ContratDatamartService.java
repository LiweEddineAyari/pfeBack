package projet.app.service.datamart;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds CONTRAT datamart dimensions from staging.stg_contrat_raw.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContratDatamartService {

    private final JdbcTemplate jdbcTemplate;

    private static final Map<Long, String> OBJET_FINANCE_LIBELLE = new LinkedHashMap<>();

    static {
        OBJET_FINANCE_LIBELLE.put(1311L, "Credits immobiliers residentiel");
        OBJET_FINANCE_LIBELLE.put(1312L, "Credits immobiliers commercial");
        OBJET_FINANCE_LIBELLE.put(1320L, "Credits d'exploitation");
        OBJET_FINANCE_LIBELLE.put(1330L, "Credits d'equipement");
        OBJET_FINANCE_LIBELLE.put(1340L, "Credits a la consommation");
        OBJET_FINANCE_LIBELLE.put(1350L, "Credits de tresorerie");
        OBJET_FINANCE_LIBELLE.put(1360L, "Autres Credits");
    }

    @Transactional
    public LoadResult loadContratDatamart() {
        ensureDatamartTablesExist();

        int agenceRows = populateAgenceDimension();
        int deviseRows = populateDeviseDimension();
        int objetRows = upsertObjetFinanceDimension();
        int typRows = populateTypContratDimension();
        int dateRows = populateDateDimension();

        int dimRows = populateDimContrat();

        LoadResult result = new LoadResult();
        result.setSubDimAgenceRows(agenceRows);
        result.setSubDimDeviseRows(deviseRows);
        result.setSubDimObjetfinanceRows(objetRows);
        result.setSubDimTypcontratRows(typRows);
        result.setSubDimDateRows(dateRows);
        result.setDimContratRows(dimRows);

        log.info("Contrat datamart load completed: {}", result);
        return result;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> fetchContratList(int page, int size) {
        int normalizedPage = Math.max(page, 0);
        int normalizedSize = size > 0 ? size : 20;
        int offset = normalizedPage * normalizedSize;

        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM datamart.dim_contrat", Long.class);
        long totalElements = total == null ? 0L : total;

        List<Map<String, Object>> items = jdbcTemplate.queryForList("""
                SELECT
                    c.id,
                    c.id_client,
                    c.ancienneteimpaye,
                    c.tauxcontrat,
                    c.actif,
                    a.numagence,
                    cl.nomprenom,
                    dv.devise,
                    ofi.libelle AS objetfinance,
                    tc.typcontrat,
                    douv.date_value AS dateouverture,
                    dech.date_value AS dateecheance
                FROM datamart.dim_contrat c
                LEFT JOIN datamart.sub_dim_agence a ON a.id = c.id_agence
                LEFT JOIN datamart.dim_client cl ON cl.idtiers = c.id_client
                LEFT JOIN datamart.sub_dim_devise dv ON dv.id = c.id_devise
                LEFT JOIN datamart.sub_dim_objetfinance ofi ON ofi.id = c.id_objetfinance
                LEFT JOIN datamart.sub_dim_typcontrat tc ON tc.id = c.id_typcontrat
                LEFT JOIN datamart.sub_dim_date douv ON douv.id = c.id_dateouverture
                LEFT JOIN datamart.sub_dim_date dech ON dech.id = c.id_dateecheance
                ORDER BY c.id
                LIMIT ? OFFSET ?
                """, normalizedSize, offset);

        return buildPaginatedResponse(normalizedPage, normalizedSize, totalElements, items);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> fetchAgenceList(int page, int size) {
        return fetchSimpleList(
                "datamart.sub_dim_agence",
                "SELECT id, numagence FROM datamart.sub_dim_agence ORDER BY id LIMIT ? OFFSET ?",
                page,
                size
        );
    }

    @Transactional(readOnly = true)
    public Map<String, Object> fetchDeviseList(int page, int size) {
        return fetchSimpleList(
                "datamart.sub_dim_devise",
                "SELECT id, devise FROM datamart.sub_dim_devise ORDER BY id LIMIT ? OFFSET ?",
                page,
                size
        );
    }

    @Transactional(readOnly = true)
    public Map<String, Object> fetchObjetFinanceList(int page, int size) {
        return fetchSimpleList(
                "datamart.sub_dim_objetfinance",
                "SELECT id, libelle FROM datamart.sub_dim_objetfinance ORDER BY id LIMIT ? OFFSET ?",
                page,
                size
        );
    }

    @Transactional(readOnly = true)
    public Map<String, Object> fetchTypeContratList(int page, int size) {
        return fetchSimpleList(
                "datamart.sub_dim_typcontrat",
                "SELECT id, typcontrat FROM datamart.sub_dim_typcontrat ORDER BY id LIMIT ? OFFSET ?",
                page,
                size
        );
    }

    @Transactional(readOnly = true)
    public Map<String, Object> fetchDateList(int page, int size) {
        return fetchSimpleList(
                "datamart.sub_dim_date",
                "SELECT id, date_value FROM datamart.sub_dim_date ORDER BY id LIMIT ? OFFSET ?",
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
            CREATE TABLE IF NOT EXISTS datamart.sub_dim_devise (
                id BIGSERIAL PRIMARY KEY,
                devise TEXT UNIQUE
            )
            """);

        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS datamart.sub_dim_objetfinance (
                id BIGINT PRIMARY KEY,
                libelle TEXT
            )
            """);

        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS datamart.sub_dim_typcontrat (
                id BIGSERIAL PRIMARY KEY,
                typcontrat TEXT UNIQUE
            )
            """);

        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS datamart.sub_dim_date (
                id BIGSERIAL PRIMARY KEY,
                date_value DATE UNIQUE
            )
            """);

        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS datamart.dim_contrat (
                id TEXT PRIMARY KEY,
                id_client TEXT REFERENCES datamart.dim_client(idtiers),
                id_agence BIGINT REFERENCES datamart.sub_dim_agence(id),
                id_devise BIGINT REFERENCES datamart.sub_dim_devise(id),
                id_objetfinance BIGINT REFERENCES datamart.sub_dim_objetfinance(id),
                id_typcontrat BIGINT REFERENCES datamart.sub_dim_typcontrat(id),
                id_dateouverture BIGINT REFERENCES datamart.sub_dim_date(id),
                id_dateecheance BIGINT REFERENCES datamart.sub_dim_date(id),
                ancienneteimpaye INTEGER,
                tauxcontrat INTEGER,
                actif INTEGER
            )
            """);
    }

    private int populateAgenceDimension() {
        String sql = """
            INSERT INTO datamart.sub_dim_agence (numagence)
            SELECT DISTINCT src.numagence
            FROM (
                SELECT NULLIF(TRIM(agence), '')::INTEGER AS numagence
                FROM staging.stg_contrat_raw
                WHERE NULLIF(TRIM(agence), '') ~ '^-?[0-9]+$'
            ) src
            WHERE src.numagence IS NOT NULL
              AND NOT EXISTS (
                  SELECT 1
                  FROM datamart.sub_dim_agence a
                  WHERE a.numagence = src.numagence
              )
            """;
        return jdbcTemplate.update(sql);
    }

    private int populateDeviseDimension() {
        String sql = """
            INSERT INTO datamart.sub_dim_devise (devise)
            SELECT DISTINCT src.devise
            FROM (
                SELECT NULLIF(TRIM(devise), '') AS devise
                FROM staging.stg_contrat_raw
            ) src
            WHERE src.devise IS NOT NULL
              AND NOT EXISTS (
                  SELECT 1
                  FROM datamart.sub_dim_devise d
                  WHERE d.devise = src.devise
              )
            """;
        return jdbcTemplate.update(sql);
    }

    private int upsertObjetFinanceDimension() {
        String sql = """
            INSERT INTO datamart.sub_dim_objetfinance (id, libelle)
            VALUES (?, ?)
            ON CONFLICT (id) DO NOTHING
            """;

        int[][] counts = jdbcTemplate.batchUpdate(
                sql,
                OBJET_FINANCE_LIBELLE.entrySet(),
                50,
                (ps, entry) -> {
                    ps.setLong(1, entry.getKey());
                    ps.setString(2, entry.getValue());
                }
        );

        int total = 0;
        for (int[] batch : counts) {
            for (int c : batch) {
                if (c > 0) {
                    total += c;
                }
            }
        }
        return total;
    }

    private int populateTypContratDimension() {
        String sql = """
            INSERT INTO datamart.sub_dim_typcontrat (typcontrat)
            SELECT DISTINCT src.typcontrat
            FROM (
                SELECT NULLIF(TRIM(typcontrat), '') AS typcontrat
                FROM staging.stg_contrat_raw
            ) src
            WHERE src.typcontrat IS NOT NULL
              AND NOT EXISTS (
                  SELECT 1
                  FROM datamart.sub_dim_typcontrat tc
                  WHERE tc.typcontrat = src.typcontrat
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
            SELECT DISTINCT parsed.date_value
            FROM (
                SELECT %s AS date_value
                FROM (
                    SELECT datouv AS raw_date FROM staging.stg_contrat_raw
                    UNION ALL
                    SELECT datech AS raw_date FROM staging.stg_contrat_raw
                ) src
            ) parsed
            WHERE parsed.date_value IS NOT NULL
              AND NOT EXISTS (
                  SELECT 1
                  FROM datamart.sub_dim_date dt
                  WHERE dt.date_value = parsed.date_value
              )
            """.formatted(parseDateExpr);

        return jdbcTemplate.update(sql);
    }

    private int populateDimContrat() {
        String datouvExpr = parseDateExpression("t.datouv");
        String datechExpr = parseDateExpression("t.datech");

        String sql = """
            INSERT INTO datamart.dim_contrat (
                id,
                id_client,
                id_agence,
                id_devise,
                id_objetfinance,
                id_typcontrat,
                id_dateouverture,
                id_dateecheance,
                ancienneteimpaye,
                tauxcontrat,
                actif
            )
            SELECT
                NULLIF(TRIM(t.idcontrat), '') AS id,
                dc.idtiers AS id_client,
                a.id AS id_agence,
                d.id AS id_devise,
                o.id AS id_objetfinance,
                tc.id AS id_typcontrat,
                dov.id AS id_dateouverture,
                dech.id AS id_dateecheance,
                CASE
                    WHEN NULLIF(TRIM(t.ancienneteimpaye), '') ~ '^-?[0-9]+$' THEN NULLIF(TRIM(t.ancienneteimpaye), '')::INTEGER
                    ELSE NULL
                END AS ancienneteimpaye,
                CASE
                    WHEN NULLIF(TRIM(t.tauxcontrat), '') ~ '^-?[0-9]+$' THEN NULLIF(TRIM(t.tauxcontrat), '')::INTEGER
                    ELSE NULL
                END AS tauxcontrat,
                CASE
                    WHEN NULLIF(TRIM(t.actif), '') ~ '^-?[0-9]+$' THEN NULLIF(TRIM(t.actif), '')::INTEGER
                    ELSE NULL
                END AS actif
            FROM staging.stg_contrat_raw t
            LEFT JOIN datamart.dim_client dc
                   ON dc.idtiers = NULLIF(TRIM(t.idtiers), '')
            LEFT JOIN (
                SELECT numagence, MIN(id) AS id
                FROM datamart.sub_dim_agence
                GROUP BY numagence
            ) a
                   ON a.numagence = CASE
                                        WHEN NULLIF(TRIM(t.agence), '') ~ '^-?[0-9]+$' THEN NULLIF(TRIM(t.agence), '')::INTEGER
                                        ELSE NULL
                                    END
            LEFT JOIN (
                SELECT devise, MIN(id) AS id
                FROM datamart.sub_dim_devise
                GROUP BY devise
            ) d
                   ON d.devise = NULLIF(TRIM(t.devise), '')
            LEFT JOIN datamart.sub_dim_objetfinance o
                   ON o.id = CASE
                                 WHEN NULLIF(TRIM(t.objetfinance), '') ~ '^-?[0-9]+$' THEN NULLIF(TRIM(t.objetfinance), '')::BIGINT
                                 ELSE NULL
                             END
            LEFT JOIN (
                SELECT typcontrat, MIN(id) AS id
                FROM datamart.sub_dim_typcontrat
                GROUP BY typcontrat
            ) tc
                   ON tc.typcontrat = NULLIF(TRIM(t.typcontrat), '')
            LEFT JOIN (
                SELECT date_value, MIN(id) AS id
                FROM datamart.sub_dim_date
                GROUP BY date_value
            ) dov
                   ON dov.date_value = %s
            LEFT JOIN (
                SELECT date_value, MIN(id) AS id
                FROM datamart.sub_dim_date
                GROUP BY date_value
            ) dech
                   ON dech.date_value = %s
            WHERE NULLIF(TRIM(t.idcontrat), '') IS NOT NULL
            ON CONFLICT (id) DO NOTHING
            """.formatted(datouvExpr, datechExpr);

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
        private int subDimObjetfinanceRows;
        private int subDimTypcontratRows;
        private int subDimDateRows;
        private int dimContratRows;
    }
}
