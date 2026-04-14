package projet.app.engine.registry;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class JoinCatalog {

    private final Map<String, JoinDefinition> joins = new LinkedHashMap<>();

    public JoinCatalog() {
        register("COMPTE", "c", "LEFT JOIN datamart.sub_dim_compte c ON f.id_compte = c.id");
        register("CHAPITRE", "ch", "LEFT JOIN datamart.sub_dim_chapitre ch ON f.id_chapitre = ch.id");
        register(
            "CLIENT",
            "cl",
            "LEFT JOIN datamart.dim_client cl ON f.id_client = cl.idtiers "
                + "LEFT JOIN datamart.sub_dim_residence res ON cl.id_residence = res.id "
                + "LEFT JOIN datamart.sub_dim_agenteco aec ON cl.id_agenteco = aec.id "
                + "LEFT JOIN datamart.sub_dim_douteux dout ON cl.id_douteux = dout.id "
                + "LEFT JOIN datamart.sub_dim_grpaffaire gaf ON cl.id_grpaffaire = gaf.id "
                + "LEFT JOIN datamart.sub_dim_sectionactivite sac ON cl.id_sectionactivite = sac.id"
        );
        register(
            "CONTRAT",
            "dc",
            "LEFT JOIN datamart.dim_contrat dc ON f.id_contrat = dc.id "
                + "LEFT JOIN datamart.sub_dim_agence agc ON dc.id_agence = agc.id "
                + "LEFT JOIN datamart.sub_dim_devise dvc ON dc.id_devise = dvc.id "
                + "LEFT JOIN datamart.sub_dim_objetfinance ofc ON dc.id_objetfinance = ofc.id "
                + "LEFT JOIN datamart.sub_dim_typcontrat tyc ON dc.id_typcontrat = tyc.id "
                + "LEFT JOIN datamart.sub_dim_date dto ON dc.id_dateouverture = dto.id "
                + "LEFT JOIN datamart.sub_dim_date dte ON dc.id_dateecheance = dte.id"
        );
        register("FACT_DATE", "dtf", "LEFT JOIN datamart.sub_dim_date dtf ON f.id_date = dtf.id");
        register("FACT_AGENCE", "agf", "LEFT JOIN datamart.sub_dim_agence agf ON f.id_agence = agf.id");
        register("FACT_DEVISE", "dvf", "LEFT JOIN datamart.sub_dim_devise dvf ON f.id_devise = dvf.id");
        register("FACT_DEVISE_BNQ", "dvb", "LEFT JOIN datamart.sub_dim_devise dvb ON f.id_devisebnq = dvb.id");
    }

    public JoinDefinition getRequired(String joinKey) {
        JoinDefinition definition = joins.get(joinKey);
        if (definition == null) {
            throw new IllegalArgumentException("Unknown join key: " + joinKey);
        }
        return definition;
    }

    private void register(String key, String alias, String clause) {
        joins.put(key, new JoinDefinition(key, alias, clause));
    }
}
