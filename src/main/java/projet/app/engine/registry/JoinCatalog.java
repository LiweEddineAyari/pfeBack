package projet.app.engine.registry;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class JoinCatalog {

    private final Map<String, JoinDefinition> joins = new LinkedHashMap<>();

    public JoinCatalog() {
        // fact-side direct sub-dim joins
        register("COMPTE", "c", "LEFT JOIN datamart.sub_dim_compte c ON f.id_compte = c.id");
        register("CHAPITRE", "ch", "LEFT JOIN datamart.sub_dim_chapitre ch ON f.id_chapitre = ch.id");

        // base dim joins
        register("DIM_CLIENT", "cl", "LEFT JOIN datamart.dim_client cl ON f.id_client = cl.idtiers");
        register("DIM_CONTRAT", "dc", "LEFT JOIN datamart.dim_contrat dc ON f.id_contrat = dc.id");

        // client sub-dim joins (depend on DIM_CLIENT)
        register("CLIENT_RESIDENCE", "res", "LEFT JOIN datamart.sub_dim_residence res ON cl.id_residence = res.id", "DIM_CLIENT");
        register("CLIENT_AGENTECO", "aec", "LEFT JOIN datamart.sub_dim_agenteco aec ON cl.id_agenteco = aec.id", "DIM_CLIENT");
        register("CLIENT_DOUTEUX", "dout", "LEFT JOIN datamart.sub_dim_douteux dout ON cl.id_douteux = dout.id", "DIM_CLIENT");
        register("CLIENT_GRPAFFAIRE", "gaf", "LEFT JOIN datamart.sub_dim_grpaffaire gaf ON cl.id_grpaffaire = gaf.id", "DIM_CLIENT");
        register("CLIENT_SECTIONACTIVITE", "sac", "LEFT JOIN datamart.sub_dim_sectionactivite sac ON cl.id_sectionactivite = sac.id", "DIM_CLIENT");

        // contrat sub-dim joins (depend on DIM_CONTRAT)
        register("CONTRAT_AGENCE", "agc", "LEFT JOIN datamart.sub_dim_agence agc ON dc.id_agence = agc.id", "DIM_CONTRAT");
        register("CONTRAT_DEVISE", "dvc", "LEFT JOIN datamart.sub_dim_devise dvc ON dc.id_devise = dvc.id", "DIM_CONTRAT");
        register("CONTRAT_OBJETFINANCE", "ofc", "LEFT JOIN datamart.sub_dim_objetfinance ofc ON dc.id_objetfinance = ofc.id", "DIM_CONTRAT");
        register("CONTRAT_TYPCONTRAT", "tyc", "LEFT JOIN datamart.sub_dim_typcontrat tyc ON dc.id_typcontrat = tyc.id", "DIM_CONTRAT");
        register("CONTRAT_DATE_OUVERTURE", "dto", "LEFT JOIN datamart.sub_dim_date dto ON dc.id_dateouverture = dto.id", "DIM_CONTRAT");
        register("CONTRAT_DATE_ECHEANCE", "dte", "LEFT JOIN datamart.sub_dim_date dte ON dc.id_dateecheance = dte.id", "DIM_CONTRAT");

        // fact-side optional dimensions
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

    private void register(String key, String alias, String clause, String... dependsOn) {
        joins.put(key, new JoinDefinition(key, alias, clause, List.of(dependsOn)));
    }
}
