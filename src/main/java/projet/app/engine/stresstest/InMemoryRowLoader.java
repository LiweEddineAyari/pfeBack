package projet.app.engine.stresstest;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import projet.app.engine.registry.FieldDefinition;
import projet.app.engine.registry.FieldRegistry;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Loads a flattened in-memory snapshot of fact_balance joined with every supported dimension table.
 *
 * <p>Each returned {@link InMemoryRow} exposes the same logical fields as the SQL formula engine.
 * Values are keyed by the canonical field name (see {@link FieldRegistry}).</p>
 */
@Component
public class InMemoryRowLoader {

    private static final String FLAT_JOIN_SQL = """
            SELECT
                f.id AS f_id,
                f.id_agence AS f_id_agence,
                f.id_devise AS f_id_devise,
                f.id_devisebnq AS f_id_devisebnq,
                f.id_compte AS f_id_compte,
                f.id_chapitre AS f_id_chapitre,
                f.id_client AS f_id_client,
                f.id_contrat AS f_id_contrat,
                f.id_date AS f_id_date,
                f.soldeorigine AS f_soldeorigine,
                f.soldeconvertie AS f_soldeconvertie,
                f.cumulmvtdb AS f_cumulmvtdb,
                f.cumulmvtcr AS f_cumulmvtcr,
                f.soldeinitdebmois AS f_soldeinitdebmois,
                f.amount AS f_amount,
                f.actif AS f_actif,
                cl.idtiers AS cl_idtiers,
                cl.id_residence AS cl_id_residence,
                cl.id_agenteco AS cl_id_agenteco,
                cl.id_douteux AS cl_id_douteux,
                cl.id_grpaffaire AS cl_id_grpaffaire,
                cl.id_sectionactivite AS cl_id_sectionactivite,
                cl.nomprenom AS cl_nomprenom,
                cl.raisonsoc AS cl_raisonsoc,
                cl.chiffreaffaires AS cl_chiffreaffaires,
                dc.id AS dc_id,
                dc.id_client AS dc_id_client,
                dc.id_agence AS dc_id_agence,
                dc.id_devise AS dc_id_devise,
                dc.id_objetfinance AS dc_id_objetfinance,
                dc.id_typcontrat AS dc_id_typcontrat,
                dc.id_dateouverture AS dc_id_dateouverture,
                dc.id_dateecheance AS dc_id_dateecheance,
                dc.ancienneteimpaye AS dc_ancienneteimpaye,
                dc.tauxcontrat AS dc_tauxcontrat,
                dc.actif AS dc_actif,
                agf.numagence AS agf_numagence,
                agc.numagence AS agc_numagence,
                aec.libelle AS aec_libelle,
                ch.chapitre AS ch_chapitre,
                c.numcompte AS c_numcompte,
                c.libellecompte AS c_libellecompte,
                dtf.date_value AS dtf_date_value,
                dto.date_value AS dto_date_value,
                dte.date_value AS dte_date_value,
                dvf.devise AS dvf_devise,
                dvb.devise AS dvb_devise,
                dvc.devise AS dvc_devise,
                dout.douteux AS dout_douteux,
                dout.datdouteux AS dout_datdouteux,
                gaf.nomgrpaffaires AS gaf_nomgrpaffaires,
                ofc.libelle AS ofc_libelle,
                res.pays AS res_pays,
                res.residence AS res_residence,
                res.geo AS res_geo,
                sac.libelle AS sac_libelle,
                tyc.typcontrat AS tyc_typcontrat
            FROM datamart.fact_balance f
            LEFT JOIN datamart.dim_client cl ON f.id_client = cl.idtiers
            LEFT JOIN datamart.dim_contrat dc ON f.id_contrat = dc.id
            LEFT JOIN datamart.sub_dim_agence agf ON f.id_agence = agf.id
            LEFT JOIN datamart.sub_dim_agence agc ON dc.id_agence = agc.id
            LEFT JOIN datamart.sub_dim_agenteco aec ON cl.id_agenteco = aec.id
            LEFT JOIN datamart.sub_dim_chapitre ch ON f.id_chapitre = ch.id
            LEFT JOIN datamart.sub_dim_compte c ON f.id_compte = c.id
            LEFT JOIN datamart.sub_dim_date dtf ON f.id_date = dtf.id
            LEFT JOIN datamart.sub_dim_date dto ON dc.id_dateouverture = dto.id
            LEFT JOIN datamart.sub_dim_date dte ON dc.id_dateecheance = dte.id
            LEFT JOIN datamart.sub_dim_devise dvf ON f.id_devise = dvf.id
            LEFT JOIN datamart.sub_dim_devise dvb ON f.id_devisebnq = dvb.id
            LEFT JOIN datamart.sub_dim_devise dvc ON dc.id_devise = dvc.id
            LEFT JOIN datamart.sub_dim_douteux dout ON cl.id_douteux = dout.id
            LEFT JOIN datamart.sub_dim_grpaffaire gaf ON cl.id_grpaffaire = gaf.id
            LEFT JOIN datamart.sub_dim_objetfinance ofc ON dc.id_objetfinance = ofc.id
            LEFT JOIN datamart.sub_dim_residence res ON cl.id_residence = res.id
            LEFT JOIN datamart.sub_dim_sectionactivite sac ON cl.id_sectionactivite = sac.id
            LEFT JOIN datamart.sub_dim_typcontrat tyc ON dc.id_typcontrat = tyc.id
            """;

    private static final String DATE_FILTER = " WHERE dtf.date_value = ?";

    /**
     * Maps canonical field name (lower-case) to SQL column alias used in {@link #FLAT_JOIN_SQL}.
     * Every logical field exposed by {@link FieldRegistry} has an entry here.
     */
    private static final Map<String, String> FIELD_TO_COLUMN = buildFieldToColumnMap();

    private final JdbcTemplate jdbcTemplate;
    private final FieldRegistry fieldRegistry;

    public InMemoryRowLoader(JdbcTemplate jdbcTemplate, FieldRegistry fieldRegistry) {
        this.jdbcTemplate = jdbcTemplate;
        this.fieldRegistry = fieldRegistry;
    }

    public List<InMemoryRow> load(LocalDate referenceDate) {
        String sql = referenceDate == null ? FLAT_JOIN_SQL : FLAT_JOIN_SQL + DATE_FILTER;

        List<InMemoryRow> rows = referenceDate == null
                ? jdbcTemplate.query(sql, this::mapRow)
                : jdbcTemplate.query(sql, this::mapRow, java.sql.Date.valueOf(referenceDate));

        return rows == null ? Collections.emptyList() : rows;
    }

    /**
     * Returns the most recent {@code limit} distinct dates present in {@code fact_balance}, joined
     * with {@code sub_dim_date}. Used by the stress-test diagnostics to help callers pick a
     * {@code referenceDate} that actually has data.
     */
    public List<LocalDate> sampleAvailableDates(int limit) {
        int effectiveLimit = limit <= 0 ? 5 : limit;
        String sql = """
                SELECT DISTINCT dtf.date_value
                FROM datamart.fact_balance f
                LEFT JOIN datamart.sub_dim_date dtf ON f.id_date = dtf.id
                WHERE dtf.date_value IS NOT NULL
                ORDER BY dtf.date_value DESC
                LIMIT ?
                """;
        List<java.sql.Date> rows = jdbcTemplate.query(sql, (rs, rowNum) -> rs.getDate(1), effectiveLimit);
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }
        List<LocalDate> dates = new ArrayList<>(rows.size());
        for (java.sql.Date date : rows) {
            if (date != null) {
                dates.add(date.toLocalDate());
            }
        }
        return dates;
    }

    /**
     * Returns the number of fact_balance rows available for the given date. Useful when the
     * stress-test diagnostics wants to report data density without materialising the full snapshot.
     */
    public long countRowsForDate(LocalDate referenceDate) {
        if (referenceDate == null) {
            Long result = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM datamart.fact_balance",
                    Long.class
            );
            return result == null ? 0L : result;
        }
        Long result = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM datamart.fact_balance f "
                        + "LEFT JOIN datamart.sub_dim_date dtf ON f.id_date = dtf.id "
                        + "WHERE dtf.date_value = ?",
                Long.class,
                java.sql.Date.valueOf(referenceDate)
        );
        return result == null ? 0L : result;
    }

    /**
     * Returns the SQL column alias used for the given canonical field name, or {@code null} if
     * the field is not materialised by the loader.
     */
    public String resolveColumnAlias(String canonicalFieldName) {
        if (canonicalFieldName == null) {
            return null;
        }
        return FIELD_TO_COLUMN.get(canonicalFieldName.toLowerCase(Locale.ROOT));
    }

    /**
     * Returns the canonical field name (lower-case) for a user-provided alias by delegating to
     * the field registry. Useful for cross-referencing with {@link InMemoryRow} values.
     */
    public String canonicalFieldName(String anyAlias) {
        FieldDefinition definition = fieldRegistry.resolve(anyAlias);
        return definition.fieldName().toLowerCase(Locale.ROOT);
    }

    private InMemoryRow mapRow(ResultSet rs, int rowNum) throws SQLException {
        Map<String, Object> values = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : FIELD_TO_COLUMN.entrySet()) {
            Object raw = rs.getObject(entry.getValue());
            Object normalized = normalizeValue(raw);
            values.put(entry.getKey(), normalized);
        }
        return new InMemoryRow(values);
    }

    private Object normalizeValue(Object raw) {
        if (raw instanceof java.sql.Date sqlDate) {
            return sqlDate.toLocalDate();
        }
        if (raw instanceof java.sql.Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        return raw;
    }

    private static Map<String, String> buildFieldToColumnMap() {
        Map<String, String> map = new LinkedHashMap<>();

        // fact_balance
        put(map, "id", "f_id");
        put(map, "idAgence", "f_id_agence");
        put(map, "idDevise", "f_id_devise");
        put(map, "idDevisebnq", "f_id_devisebnq");
        put(map, "idCompte", "f_id_compte");
        put(map, "idChapitre", "f_id_chapitre");
        put(map, "idClient", "f_id_client");
        put(map, "idContrat", "f_id_contrat");
        put(map, "idDate", "f_id_date");
        put(map, "soldeorigine", "f_soldeorigine");
        put(map, "soldeconvertie", "f_soldeconvertie");
        put(map, "cumulmvtdb", "f_cumulmvtdb");
        put(map, "cumulmvtcr", "f_cumulmvtcr");
        put(map, "soldeinitdebmois", "f_soldeinitdebmois");
        put(map, "amount", "f_amount");
        put(map, "actif", "f_actif");

        // dim_client
        put(map, "dimClient.idtiers", "cl_idtiers");
        put(map, "dimClient.idResidence", "cl_id_residence");
        put(map, "dimClient.idAgenteco", "cl_id_agenteco");
        put(map, "dimClient.idDouteux", "cl_id_douteux");
        put(map, "dimClient.idGrpaffaire", "cl_id_grpaffaire");
        put(map, "dimClient.idSectionactivite", "cl_id_sectionactivite");
        put(map, "dimClient.nomprenom", "cl_nomprenom");
        put(map, "dimClient.raisonsoc", "cl_raisonsoc");
        put(map, "dimClient.chiffreaffaires", "cl_chiffreaffaires");

        // dim_contrat
        put(map, "dimContrat.id", "dc_id");
        put(map, "dimContrat.idClient", "dc_id_client");
        put(map, "dimContrat.idAgence", "dc_id_agence");
        put(map, "dimContrat.idDevise", "dc_id_devise");
        put(map, "dimContrat.idObjetfinance", "dc_id_objetfinance");
        put(map, "dimContrat.idTypcontrat", "dc_id_typcontrat");
        put(map, "dimContrat.idDateouverture", "dc_id_dateouverture");
        put(map, "dimContrat.idDateecheance", "dc_id_dateecheance");
        put(map, "dimContrat.ancienneteimpaye", "dc_ancienneteimpaye");
        put(map, "dimContrat.tauxcontrat", "dc_tauxcontrat");
        put(map, "dimContrat.actif", "dc_actif");

        // sub_dim_agence
        put(map, "subDimAgence.numagence", "agf_numagence");
        put(map, "dimContrat.subDimAgence.numagence", "agc_numagence");

        // sub_dim_agenteco
        put(map, "subDimAgenteco.libelle", "aec_libelle");

        // sub_dim_chapitre
        put(map, "subDimChapitre.chapitre", "ch_chapitre");

        // sub_dim_compte
        put(map, "subDimCompte.numcompte", "c_numcompte");
        put(map, "subDimCompte.libellecompte", "c_libellecompte");

        // sub_dim_date
        put(map, "subDimDate.dateValue", "dtf_date_value");
        put(map, "dimContrat.subDimDateOuverture.dateValue", "dto_date_value");
        put(map, "dimContrat.subDimDateEcheance.dateValue", "dte_date_value");

        // sub_dim_devise
        put(map, "subDimDevise.devise", "dvf_devise");
        put(map, "subDimDeviseBnq.devise", "dvb_devise");
        put(map, "dimContrat.subDimDevise.devise", "dvc_devise");

        // sub_dim_douteux
        put(map, "subDimDouteux.douteux", "dout_douteux");
        put(map, "subDimDouteux.datdouteux", "dout_datdouteux");

        // sub_dim_grpaffaire
        put(map, "subDimGrpaffaire.nomgrpaffaires", "gaf_nomgrpaffaires");

        // sub_dim_objetfinance
        put(map, "dimContrat.subDimObjetfinance.libelle", "ofc_libelle");

        // sub_dim_residence
        put(map, "subDimResidence.pays", "res_pays");
        put(map, "subDimResidence.residence", "res_residence");
        put(map, "subDimResidence.geo", "res_geo");

        // sub_dim_sectionactivite
        put(map, "subDimSectionactivite.libelle", "sac_libelle");

        // sub_dim_typcontrat
        put(map, "dimContrat.subDimTypcontrat.typcontrat", "tyc_typcontrat");

        return Collections.unmodifiableMap(map);
    }

    private static void put(Map<String, String> map, String canonicalFieldName, String column) {
        map.put(canonicalFieldName.toLowerCase(Locale.ROOT), column);
    }

    // Exposed for tests / diagnostics.
    public List<String> materialisedFields() {
        return new ArrayList<>(FIELD_TO_COLUMN.keySet());
    }
}
