package projet.app.engine.compiler;

import org.springframework.stereotype.Component;
import projet.app.engine.enums.FormulaValueType;
import projet.app.exception.FormulaValidationException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class FieldMappingCatalog {

    private final Map<String, FieldSpec> fields;

    public FieldMappingCatalog() {
        Map<String, FieldSpec> catalog = new LinkedHashMap<>();

        // fact_balance
        add(catalog, "id", "fact_balance", "f.id", FormulaValueType.NUMERIC);
        add(catalog, "idAgence", "fact_balance", "f.id_agence", FormulaValueType.NUMERIC);
        add(catalog, "idDevise", "fact_balance", "f.id_devise", FormulaValueType.NUMERIC);
        add(catalog, "idDevisebnq", "fact_balance", "f.id_devisebnq", FormulaValueType.NUMERIC);
        add(catalog, "idCompte", "fact_balance", "f.id_compte", FormulaValueType.NUMERIC);
        add(catalog, "idChapitre", "fact_balance", "f.id_chapitre", FormulaValueType.NUMERIC);
        add(catalog, "idClient", "fact_balance", "f.id_client", FormulaValueType.STRING);
        add(catalog, "idContrat", "fact_balance", "f.id_contrat", FormulaValueType.STRING);
        add(catalog, "idDate", "fact_balance", "f.id_date", FormulaValueType.NUMERIC);
        add(catalog, "soldeorigine", "fact_balance", "f.soldeorigine", FormulaValueType.NUMERIC);
        add(catalog, "soldeconvertie", "fact_balance", "f.soldeconvertie", FormulaValueType.NUMERIC);
        add(catalog, "cumulmvtdb", "fact_balance", "f.cumulmvtdb", FormulaValueType.NUMERIC);
        add(catalog, "cumulmvtcr", "fact_balance", "f.cumulmvtcr", FormulaValueType.NUMERIC);
        add(catalog, "soldeinitdebmois", "fact_balance", "f.soldeinitdebmois", FormulaValueType.NUMERIC);
        add(catalog, "amount", "fact_balance", "f.amount", FormulaValueType.NUMERIC);
        add(catalog, "actif", "fact_balance", "f.actif", FormulaValueType.NUMERIC);

        // dim_client
        add(catalog, "dimClient.idtiers", "dim_client", "dc.idtiers", FormulaValueType.STRING, JoinKey.DIM_CLIENT);
        add(catalog, "dimClient.idResidence", "dim_client", "dc.id_residence", FormulaValueType.NUMERIC, JoinKey.DIM_CLIENT);
        add(catalog, "dimClient.idAgenteco", "dim_client", "dc.id_agenteco", FormulaValueType.NUMERIC, JoinKey.DIM_CLIENT);
        add(catalog, "dimClient.idDouteux", "dim_client", "dc.id_douteux", FormulaValueType.NUMERIC, JoinKey.DIM_CLIENT);
        add(catalog, "dimClient.idGrpaffaire", "dim_client", "dc.id_grpaffaire", FormulaValueType.NUMERIC, JoinKey.DIM_CLIENT);
        add(catalog, "dimClient.idSectionactivite", "dim_client", "dc.id_sectionactivite", FormulaValueType.NUMERIC, JoinKey.DIM_CLIENT);
        add(catalog, "dimClient.nomprenom", "dim_client", "dc.nomprenom", FormulaValueType.STRING, JoinKey.DIM_CLIENT);
        add(catalog, "dimClient.raisonsoc", "dim_client", "dc.raisonsoc", FormulaValueType.STRING, JoinKey.DIM_CLIENT);
        add(catalog, "dimClient.chiffreaffaires", "dim_client", "dc.chiffreaffaires", FormulaValueType.STRING, JoinKey.DIM_CLIENT);

        // dim_contrat
        add(catalog, "dimContrat.id", "dim_contrat", "dct.id", FormulaValueType.STRING, JoinKey.DIM_CONTRAT);
        add(catalog, "dimContrat.idClient", "dim_contrat", "dct.id_client", FormulaValueType.STRING, JoinKey.DIM_CONTRAT);
        add(catalog, "dimContrat.idAgence", "dim_contrat", "dct.id_agence", FormulaValueType.NUMERIC, JoinKey.DIM_CONTRAT);
        add(catalog, "dimContrat.idDevise", "dim_contrat", "dct.id_devise", FormulaValueType.NUMERIC, JoinKey.DIM_CONTRAT);
        add(catalog, "dimContrat.idObjetfinance", "dim_contrat", "dct.id_objetfinance", FormulaValueType.NUMERIC, JoinKey.DIM_CONTRAT);
        add(catalog, "dimContrat.idTypcontrat", "dim_contrat", "dct.id_typcontrat", FormulaValueType.NUMERIC, JoinKey.DIM_CONTRAT);
        add(catalog, "dimContrat.idDateouverture", "dim_contrat", "dct.id_dateouverture", FormulaValueType.NUMERIC, JoinKey.DIM_CONTRAT);
        add(catalog, "dimContrat.idDateecheance", "dim_contrat", "dct.id_dateecheance", FormulaValueType.NUMERIC, JoinKey.DIM_CONTRAT);
        add(catalog, "dimContrat.ancienneteimpaye", "dim_contrat", "dct.ancienneteimpaye", FormulaValueType.NUMERIC, JoinKey.DIM_CONTRAT);
        add(catalog, "dimContrat.tauxcontrat", "dim_contrat", "dct.tauxcontrat", FormulaValueType.NUMERIC, JoinKey.DIM_CONTRAT);
        add(catalog, "dimContrat.actif", "dim_contrat", "dct.actif", FormulaValueType.NUMERIC, JoinKey.DIM_CONTRAT);

        // sub_dim_agence
        add(catalog, "subDimAgence.numagence", "sub_dim_agence", "sagf.numagence", FormulaValueType.NUMERIC, JoinKey.SUB_DIM_AGENCE_FACT);
        add(catalog, "dimContrat.subDimAgence.numagence", "sub_dim_agence", "sagct.numagence", FormulaValueType.NUMERIC, JoinKey.DIM_CONTRAT, JoinKey.SUB_DIM_AGENCE_CONTRAT);

        // sub_dim_agenteco
        add(catalog, "subDimAgenteco.libelle", "sub_dim_agenteco", "sagec.libelle", FormulaValueType.STRING, JoinKey.DIM_CLIENT, JoinKey.SUB_DIM_AGENTECO_CLIENT);

        // sub_dim_chapitre
        add(catalog, "subDimChapitre.chapitre", "sub_dim_chapitre", "schapf.chapitre", FormulaValueType.NUMERIC, JoinKey.SUB_DIM_CHAPITRE_FACT);

        // sub_dim_compte
        add(catalog, "subDimCompte.numcompte", "sub_dim_compte", "scompf.numcompte", FormulaValueType.NUMERIC, JoinKey.SUB_DIM_COMPTE_FACT);
        add(catalog, "subDimCompte.libellecompte", "sub_dim_compte", "scompf.libellecompte", FormulaValueType.STRING, JoinKey.SUB_DIM_COMPTE_FACT);

        // sub_dim_date
        add(catalog, "subDimDate.dateValue", "sub_dim_date", "sdatef.date_value", FormulaValueType.DATE, JoinKey.SUB_DIM_DATE_FACT);
        add(catalog, "dimContrat.subDimDateOuverture.dateValue", "sub_dim_date", "sdoc.date_value", FormulaValueType.DATE, JoinKey.DIM_CONTRAT, JoinKey.SUB_DIM_DATE_OUVERTURE_CONTRAT);
        add(catalog, "dimContrat.subDimDateEcheance.dateValue", "sub_dim_date", "sdec.date_value", FormulaValueType.DATE, JoinKey.DIM_CONTRAT, JoinKey.SUB_DIM_DATE_ECHEANCE_CONTRAT);

        // sub_dim_devise
        add(catalog, "subDimDevise.devise", "sub_dim_devise", "sdevf.devise", FormulaValueType.STRING, JoinKey.SUB_DIM_DEVISE_FACT);
        add(catalog, "subDimDeviseBnq.devise", "sub_dim_devise", "sdevbnq.devise", FormulaValueType.STRING, JoinKey.SUB_DIM_DEVISE_BNQ_FACT);
        add(catalog, "dimContrat.subDimDevise.devise", "sub_dim_devise", "sdevct.devise", FormulaValueType.STRING, JoinKey.DIM_CONTRAT, JoinKey.SUB_DIM_DEVISE_CONTRAT);

        // sub_dim_douteux
        add(catalog, "subDimDouteux.douteux", "sub_dim_douteux", "sdoutc.douteux", FormulaValueType.NUMERIC, JoinKey.DIM_CLIENT, JoinKey.SUB_DIM_DOUTEUX_CLIENT);
        add(catalog, "subDimDouteux.datdouteux", "sub_dim_douteux", "sdoutc.datdouteux", FormulaValueType.DATE, JoinKey.DIM_CLIENT, JoinKey.SUB_DIM_DOUTEUX_CLIENT);

        // sub_dim_grpaffaire
        add(catalog, "subDimGrpaffaire.nomgrpaffaires", "sub_dim_grpaffaire", "sgrpc.nomgrpaffaires", FormulaValueType.STRING, JoinKey.DIM_CLIENT, JoinKey.SUB_DIM_GRPAFFAIRE_CLIENT);

        // sub_dim_objetfinance
        add(catalog, "dimContrat.subDimObjetfinance.libelle", "sub_dim_objetfinance", "sobjct.libelle", FormulaValueType.STRING, JoinKey.DIM_CONTRAT, JoinKey.SUB_DIM_OBJETFINANCE_CONTRAT);

        // sub_dim_residence
        add(catalog, "subDimResidence.pays", "sub_dim_residence", "sresc.pays", FormulaValueType.STRING, JoinKey.DIM_CLIENT, JoinKey.SUB_DIM_RESIDENCE_CLIENT);
        add(catalog, "subDimResidence.residence", "sub_dim_residence", "sresc.residence", FormulaValueType.STRING, JoinKey.DIM_CLIENT, JoinKey.SUB_DIM_RESIDENCE_CLIENT);
        add(catalog, "subDimResidence.geo", "sub_dim_residence", "sresc.geo", FormulaValueType.STRING, JoinKey.DIM_CLIENT, JoinKey.SUB_DIM_RESIDENCE_CLIENT);

        // sub_dim_sectionactivite
        add(catalog, "subDimSectionactivite.libelle", "sub_dim_sectionactivite", "ssecc.libelle", FormulaValueType.STRING, JoinKey.DIM_CLIENT, JoinKey.SUB_DIM_SECTIONACTIVITE_CLIENT);

        // sub_dim_typcontrat
        add(catalog, "dimContrat.subDimTypcontrat.typcontrat", "sub_dim_typcontrat", "stypct.typcontrat", FormulaValueType.STRING, JoinKey.DIM_CONTRAT, JoinKey.SUB_DIM_TYPCONTRAT_CONTRAT);

        // Friendly aliases for unqualified formula fields.
        alias(catalog, "numcompte", "subDimCompte.numcompte");
        alias(catalog, "libellecompte", "subDimCompte.libellecompte");
        alias(catalog, "iddouteux", "dimClient.idDouteux");
        alias(catalog, "nomprenom", "dimClient.nomprenom");
        alias(catalog, "raisonsoc", "dimClient.raisonsoc");
        alias(catalog, "chiffreaffaires", "dimClient.chiffreaffaires");
        alias(catalog, "pays", "subDimResidence.pays");
        alias(catalog, "residence", "subDimResidence.residence");
        alias(catalog, "geo", "subDimResidence.geo");
        alias(catalog, "libelleagenteco", "subDimAgenteco.libelle");
        alias(catalog, "sectionactivite", "subDimSectionactivite.libelle");
        alias(catalog, "grpaffaire", "subDimGrpaffaire.nomgrpaffaires");
        alias(catalog, "devise", "subDimDevise.devise");
        alias(catalog, "datevalue", "subDimDate.dateValue");
        alias(catalog, "idclient", "idClient");
        alias(catalog, "idcontrat", "idContrat");
        alias(catalog, "idagence", "idAgence");
        alias(catalog, "iddevise", "idDevise");
        alias(catalog, "iddate", "idDate");
        alias(catalog, "idcompte", "idCompte");
        alias(catalog, "idchapitre", "idChapitre");

        fields = Map.copyOf(catalog);
    }

    public FieldSpec getRequired(String fieldName) {
        FieldSpec spec = fields.get(normalize(fieldName));
        if (spec == null) {
            throw new FormulaValidationException(List.of("Unknown field: " + fieldName));
        }
        return spec;
    }

    public boolean exists(String fieldName) {
        return fields.containsKey(normalize(fieldName));
    }

    public FormulaValueType getFieldType(String fieldName) {
        return getRequired(fieldName).valueType();
    }

    public Set<String> supportedFields() {
        return fields.keySet();
    }

    private void add(
            Map<String, FieldSpec> target,
            String canonicalName,
            String sourceTable,
            String sqlExpression,
            FormulaValueType valueType,
            JoinKey... joins
    ) {
        target.put(normalize(canonicalName), new FieldSpec(
                canonicalName,
                sourceTable,
                sqlExpression,
                valueType,
                List.of(joins)
        ));
    }

    private void alias(Map<String, FieldSpec> target, String aliasName, String canonicalName) {
        FieldSpec canonical = target.get(normalize(canonicalName));
        if (canonical == null) {
            throw new IllegalStateException("Cannot create alias for unknown canonical field: " + canonicalName);
        }
        target.put(normalize(aliasName), canonical);
    }

    private String normalize(String fieldName) {
        return fieldName == null ? "" : fieldName.trim().toLowerCase(Locale.ROOT);
    }
}
