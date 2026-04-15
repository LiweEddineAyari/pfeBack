package projet.app.engine.registry;

import org.springframework.stereotype.Component;
import projet.app.engine.enums.FormulaValueType;
import projet.app.exception.FormulaValidationException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class FieldRegistry {

    private final Map<String, FieldDefinition> fields;

    public FieldRegistry() {
        Map<String, FieldDefinition> catalog = new LinkedHashMap<>();

        // fact_balance (base alias f)
        add(catalog, "id", "id", "f", null, FieldDataType.NUMBER);
        add(catalog, "idAgence", "id_agence", "f", null, FieldDataType.NUMBER);
        add(catalog, "idDevise", "id_devise", "f", null, FieldDataType.NUMBER);
        add(catalog, "idDevisebnq", "id_devisebnq", "f", null, FieldDataType.NUMBER);
        add(catalog, "idCompte", "id_compte", "f", null, FieldDataType.NUMBER);
        add(catalog, "idChapitre", "id_chapitre", "f", null, FieldDataType.NUMBER);
        add(catalog, "idClient", "id_client", "f", null, FieldDataType.TEXT);
        add(catalog, "idContrat", "id_contrat", "f", null, FieldDataType.TEXT);
        add(catalog, "idDate", "id_date", "f", null, FieldDataType.NUMBER);
        add(catalog, "soldeorigine", "soldeorigine", "f", null, FieldDataType.NUMBER);
        add(catalog, "soldeconvertie", "soldeconvertie", "f", null, FieldDataType.NUMBER);
        add(catalog, "cumulmvtdb", "cumulmvtdb", "f", null, FieldDataType.NUMBER);
        add(catalog, "cumulmvtcr", "cumulmvtcr", "f", null, FieldDataType.NUMBER);
        add(catalog, "soldeinitdebmois", "soldeinitdebmois", "f", null, FieldDataType.NUMBER);
        add(catalog, "amount", "amount", "f", null, FieldDataType.NUMBER);
        add(catalog, "actif", "actif", "f", null, FieldDataType.NUMBER);

        // dim_client (base dim join)
        add(catalog, "dimClient.idtiers", "idtiers", "cl", "DIM_CLIENT", FieldDataType.TEXT);
        add(catalog, "dimClient.idResidence", "id_residence", "cl", "DIM_CLIENT", FieldDataType.NUMBER);
        add(catalog, "dimClient.idAgenteco", "id_agenteco", "cl", "DIM_CLIENT", FieldDataType.NUMBER);
        add(catalog, "dimClient.idDouteux", "id_douteux", "cl", "DIM_CLIENT", FieldDataType.NUMBER);
        add(catalog, "dimClient.idGrpaffaire", "id_grpaffaire", "cl", "DIM_CLIENT", FieldDataType.NUMBER);
        add(catalog, "dimClient.idSectionactivite", "id_sectionactivite", "cl", "DIM_CLIENT", FieldDataType.NUMBER);
        add(catalog, "dimClient.nomprenom", "nomprenom", "cl", "DIM_CLIENT", FieldDataType.TEXT);
        add(catalog, "dimClient.raisonsoc", "raisonsoc", "cl", "DIM_CLIENT", FieldDataType.TEXT);
        add(catalog, "dimClient.chiffreaffaires", "chiffreaffaires", "cl", "DIM_CLIENT", FieldDataType.TEXT);

        // dim_contrat (base dim join)
        add(catalog, "dimContrat.id", "id", "dc", "DIM_CONTRAT", FieldDataType.TEXT);
        add(catalog, "dimContrat.idClient", "id_client", "dc", "DIM_CONTRAT", FieldDataType.TEXT);
        add(catalog, "dimContrat.idAgence", "id_agence", "dc", "DIM_CONTRAT", FieldDataType.NUMBER);
        add(catalog, "dimContrat.idDevise", "id_devise", "dc", "DIM_CONTRAT", FieldDataType.NUMBER);
        add(catalog, "dimContrat.idObjetfinance", "id_objetfinance", "dc", "DIM_CONTRAT", FieldDataType.NUMBER);
        add(catalog, "dimContrat.idTypcontrat", "id_typcontrat", "dc", "DIM_CONTRAT", FieldDataType.NUMBER);
        add(catalog, "dimContrat.idDateouverture", "id_dateouverture", "dc", "DIM_CONTRAT", FieldDataType.NUMBER);
        add(catalog, "dimContrat.idDateecheance", "id_dateecheance", "dc", "DIM_CONTRAT", FieldDataType.NUMBER);
        add(catalog, "dimContrat.ancienneteimpaye", "ancienneteimpaye", "dc", "DIM_CONTRAT", FieldDataType.NUMBER);
        add(catalog, "dimContrat.tauxcontrat", "tauxcontrat", "dc", "DIM_CONTRAT", FieldDataType.NUMBER);
        add(catalog, "dimContrat.actif", "actif", "dc", "DIM_CONTRAT", FieldDataType.NUMBER);

        // sub_dim_agence
        add(catalog, "subDimAgence.numagence", "numagence", "agf", "FACT_AGENCE", FieldDataType.NUMBER);
        add(catalog, "dimContrat.subDimAgence.numagence", "numagence", "agc", "CONTRAT_AGENCE", FieldDataType.NUMBER);

        // sub_dim_agenteco
        add(catalog, "subDimAgenteco.libelle", "libelle", "aec", "CLIENT_AGENTECO", FieldDataType.TEXT);

        // sub_dim_chapitre
        add(catalog, "subDimChapitre.chapitre", "chapitre", "ch", "CHAPITRE", FieldDataType.NUMBER);

        // sub_dim_compte
        add(catalog, "subDimCompte.numcompte", "numcompte", "c", "COMPTE", FieldDataType.TEXT);
        add(catalog, "subDimCompte.libellecompte", "libellecompte", "c", "COMPTE", FieldDataType.TEXT);

        // sub_dim_date
        add(catalog, "subDimDate.dateValue", "date_value", "dtf", "FACT_DATE", FieldDataType.DATE);
        add(catalog, "dimContrat.subDimDateOuverture.dateValue", "date_value", "dto", "CONTRAT_DATE_OUVERTURE", FieldDataType.DATE);
        add(catalog, "dimContrat.subDimDateEcheance.dateValue", "date_value", "dte", "CONTRAT_DATE_ECHEANCE", FieldDataType.DATE);

        // sub_dim_devise
        add(catalog, "subDimDevise.devise", "devise", "dvf", "FACT_DEVISE", FieldDataType.TEXT);
        add(catalog, "subDimDeviseBnq.devise", "devise", "dvb", "FACT_DEVISE_BNQ", FieldDataType.TEXT);
        add(catalog, "dimContrat.subDimDevise.devise", "devise", "dvc", "CONTRAT_DEVISE", FieldDataType.TEXT);

        // sub_dim_douteux
        add(catalog, "subDimDouteux.douteux", "douteux", "dout", "CLIENT_DOUTEUX", FieldDataType.NUMBER);
        add(catalog, "subDimDouteux.datdouteux", "datdouteux", "dout", "CLIENT_DOUTEUX", FieldDataType.DATE);

        // sub_dim_grpaffaire
        add(catalog, "subDimGrpaffaire.nomgrpaffaires", "nomgrpaffaires", "gaf", "CLIENT_GRPAFFAIRE", FieldDataType.TEXT);

        // sub_dim_objetfinance
        add(catalog, "dimContrat.subDimObjetfinance.libelle", "libelle", "ofc", "CONTRAT_OBJETFINANCE", FieldDataType.TEXT);

        // sub_dim_residence
        add(catalog, "subDimResidence.pays", "pays", "res", "CLIENT_RESIDENCE", FieldDataType.TEXT);
        add(catalog, "subDimResidence.residence", "residence", "res", "CLIENT_RESIDENCE", FieldDataType.TEXT);
        add(catalog, "subDimResidence.geo", "geo", "res", "CLIENT_RESIDENCE", FieldDataType.TEXT);

        // sub_dim_sectionactivite
        add(catalog, "subDimSectionactivite.libelle", "libelle", "sac", "CLIENT_SECTIONACTIVITE", FieldDataType.TEXT);

        // sub_dim_typcontrat
        add(catalog, "dimContrat.subDimTypcontrat.typcontrat", "typcontrat", "tyc", "CONTRAT_TYPCONTRAT", FieldDataType.TEXT);

        // Existing short aliases kept for compatibility
        alias(catalog, "numcompte", "subDimCompte.numcompte");
        alias(catalog, "libellecompte", "subDimCompte.libellecompte");
        alias(catalog, "chapitre", "subDimChapitre.chapitre");
        alias(catalog, "idDouteux", "dimClient.idDouteux");
        alias(catalog, "idGrpaffaire", "dimClient.idGrpaffaire");
        alias(catalog, "idAgenteco", "dimClient.idAgenteco");
        alias(catalog, "idSectionactivite", "dimClient.idSectionactivite");
        alias(catalog, "idtiers", "dimClient.idtiers");
        alias(catalog, "idResidence", "dimClient.idResidence");
        alias(catalog, "nomprenom", "dimClient.nomprenom");
        alias(catalog, "raisonsoc", "dimClient.raisonsoc");
        alias(catalog, "chiffreaffaires", "dimClient.chiffreaffaires");
        alias(catalog, "contratActif", "dimContrat.actif");
        alias(catalog, "ancienneteimpaye", "dimContrat.ancienneteimpaye");
        alias(catalog, "idTypcontrat", "dimContrat.idTypcontrat");
        alias(catalog, "idObjetfinance", "dimContrat.idObjetfinance");
        alias(catalog, "idDateouverture", "dimContrat.idDateouverture");
        alias(catalog, "idDateecheance", "dimContrat.idDateecheance");
        alias(catalog, "tauxcontrat", "dimContrat.tauxcontrat");
        alias(catalog, "numagence", "subDimAgence.numagence");
        alias(catalog, "douteux", "subDimDouteux.douteux");
        alias(catalog, "datdouteux", "subDimDouteux.datdouteux");
        alias(catalog, "nomgrpaffaires", "subDimGrpaffaire.nomgrpaffaires");
        alias(catalog, "typcontrat", "dimContrat.subDimTypcontrat.typcontrat");
        alias(catalog, "dateouverture", "dimContrat.subDimDateOuverture.dateValue");
        alias(catalog, "dateecheance", "dimContrat.subDimDateEcheance.dateValue");
        alias(catalog, "devisefact", "subDimDevise.devise");
        alias(catalog, "devisebnq", "subDimDeviseBnq.devise");
        alias(catalog, "devisecontrat", "dimContrat.subDimDevise.devise");
        alias(catalog, "numagencecontrat", "dimContrat.subDimAgence.numagence");
        alias(catalog, "pays", "subDimResidence.pays");
        alias(catalog, "residence", "subDimResidence.residence");
        alias(catalog, "geo", "subDimResidence.geo");
        alias(catalog, "libelleagenteco", "subDimAgenteco.libelle");
        alias(catalog, "sectionactivite", "subDimSectionactivite.libelle");
        alias(catalog, "grpaffaire", "subDimGrpaffaire.nomgrpaffaires");
        alias(catalog, "devise", "subDimDevise.devise");
        alias(catalog, "datevalue", "subDimDate.dateValue");

        // Existing lowercase aliases
        alias(catalog, "idclient", "idClient");
        alias(catalog, "idcontrat", "idContrat");
        alias(catalog, "idagence", "idAgence");
        alias(catalog, "iddevise", "idDevise");
        alias(catalog, "iddevisebnq", "idDevisebnq");
        alias(catalog, "iddate", "idDate");
        alias(catalog, "id_douteux", "dimClient.idDouteux");
        alias(catalog, "id_grpaffaire", "dimClient.idGrpaffaire");
        alias(catalog, "id_agenteco", "dimClient.idAgenteco");
        alias(catalog, "id_sectionactivite", "dimClient.idSectionactivite");
        alias(catalog, "id_objetfinance", "dimContrat.idObjetfinance");
        alias(catalog, "id_typcontrat", "dimContrat.idTypcontrat");
        alias(catalog, "id_dateouverture", "dimContrat.idDateouverture");
        alias(catalog, "id_dateecheance", "dimContrat.idDateecheance");
        alias(catalog, "iddouteux", "dimClient.idDouteux");
        alias(catalog, "anciennete_impaye", "dimContrat.ancienneteimpaye");

        // Dot-path aliases already used in examples
        alias(catalog, "f.id", "id");
        alias(catalog, "f.id_agence", "idAgence");
        alias(catalog, "f.id_devise", "idDevise");
        alias(catalog, "f.id_devisebnq", "idDevisebnq");
        alias(catalog, "f.id_compte", "idCompte");
        alias(catalog, "f.id_chapitre", "idChapitre");
        alias(catalog, "f.id_client", "idClient");
        alias(catalog, "f.id_contrat", "idContrat");
        alias(catalog, "f.id_date", "idDate");
        alias(catalog, "f.soldeorigine", "soldeorigine");
        alias(catalog, "f.soldeconvertie", "soldeconvertie");
        alias(catalog, "f.cumulmvtdb", "cumulmvtdb");
        alias(catalog, "f.cumulmvtcr", "cumulmvtcr");
        alias(catalog, "f.soldeinitdebmois", "soldeinitdebmois");
        alias(catalog, "f.amount", "amount");
        alias(catalog, "f.actif", "actif");
        alias(catalog, "c.numcompte", "subDimCompte.numcompte");
        alias(catalog, "c.libellecompte", "subDimCompte.libellecompte");
        alias(catalog, "ch.chapitre", "subDimChapitre.chapitre");
        alias(catalog, "cl.idtiers", "dimClient.idtiers");
        alias(catalog, "cl.idresidence", "dimClient.idResidence");
        alias(catalog, "cl.idagenteco", "dimClient.idAgenteco");
        alias(catalog, "cl.iddouteux", "dimClient.idDouteux");
        alias(catalog, "cl.idgrpaffaire", "dimClient.idGrpaffaire");
        alias(catalog, "cl.idsectionactivite", "dimClient.idSectionactivite");
        alias(catalog, "cl.nomprenom", "dimClient.nomprenom");
        alias(catalog, "cl.raisonsoc", "dimClient.raisonsoc");
        alias(catalog, "cl.chiffreaffaires", "dimClient.chiffreaffaires");
        alias(catalog, "dc.id", "dimContrat.id");
        alias(catalog, "dc.idclient", "dimContrat.idClient");
        alias(catalog, "dc.idagence", "dimContrat.idAgence");
        alias(catalog, "dc.iddevise", "dimContrat.idDevise");
        alias(catalog, "dc.idobjetfinance", "dimContrat.idObjetfinance");
        alias(catalog, "dc.idtypcontrat", "dimContrat.idTypcontrat");
        alias(catalog, "dc.iddateouverture", "dimContrat.idDateouverture");
        alias(catalog, "dc.iddateecheance", "dimContrat.idDateecheance");
        alias(catalog, "dc.actif", "dimContrat.actif");
        alias(catalog, "dc.ancienneteimpaye", "dimContrat.ancienneteimpaye");
        alias(catalog, "dc.anciennete_impaye", "dimContrat.ancienneteimpaye");
        alias(catalog, "dc.tauxcontrat", "dimContrat.tauxcontrat");
        alias(catalog, "agf.numagence", "subDimAgence.numagence");
        alias(catalog, "agc.numagence", "dimContrat.subDimAgence.numagence");
        alias(catalog, "aec.libelle", "subDimAgenteco.libelle");
        alias(catalog, "dout.douteux", "subDimDouteux.douteux");
        alias(catalog, "dout.datdouteux", "subDimDouteux.datdouteux");
        alias(catalog, "gaf.nomgrpaffaires", "subDimGrpaffaire.nomgrpaffaires");
        alias(catalog, "ofc.libelle", "dimContrat.subDimObjetfinance.libelle");
        alias(catalog, "res.pays", "subDimResidence.pays");
        alias(catalog, "res.residence", "subDimResidence.residence");
        alias(catalog, "res.geo", "subDimResidence.geo");
        alias(catalog, "sac.libelle", "subDimSectionactivite.libelle");
        alias(catalog, "tyc.typcontrat", "dimContrat.subDimTypcontrat.typcontrat");
        alias(catalog, "dvf.devise", "subDimDevise.devise");
        alias(catalog, "dvb.devise", "subDimDeviseBnq.devise");
        alias(catalog, "dvc.devise", "dimContrat.subDimDevise.devise");
        alias(catalog, "dtf.date_value", "subDimDate.dateValue");
        alias(catalog, "dto.date_value", "dimContrat.subDimDateOuverture.dateValue");
        alias(catalog, "dte.date_value", "dimContrat.subDimDateEcheance.dateValue");

        // Table-name style aliases
        alias(catalog, "sub_dim_douteux.douteux", "subDimDouteux.douteux");
        alias(catalog, "sub_dim_douteux.datdouteux", "subDimDouteux.datdouteux");
        alias(catalog, "sub_dim_compte.numcompte", "subDimCompte.numcompte");
        alias(catalog, "sub_dim_compte.libellecompte", "subDimCompte.libellecompte");
        alias(catalog, "sub_dim_chapitre.chapitre", "subDimChapitre.chapitre");
        alias(catalog, "sub_dim_devise.devise", "subDimDevise.devise");
        alias(catalog, "sub_dim_date.date_value", "subDimDate.dateValue");
        alias(catalog, "sub_dim_residence.pays", "subDimResidence.pays");
        alias(catalog, "sub_dim_residence.residence", "subDimResidence.residence");
        alias(catalog, "sub_dim_residence.geo", "subDimResidence.geo");
        alias(catalog, "sub_dim_sectionactivite.libelle", "subDimSectionactivite.libelle");
        alias(catalog, "sub_dim_agenteco.libelle", "subDimAgenteco.libelle");
        alias(catalog, "sub_dim_grpaffaire.nomgrpaffaires", "subDimGrpaffaire.nomgrpaffaires");
        alias(catalog, "sub_dim_objetfinance.libelle", "dimContrat.subDimObjetfinance.libelle");
        alias(catalog, "sub_dim_typcontrat.typcontrat", "dimContrat.subDimTypcontrat.typcontrat");

        fields = Map.copyOf(catalog);
    }

    public FieldDefinition resolve(String fieldName) {
        FieldDefinition definition = fields.get(normalize(fieldName));
        if (definition == null) {
            throw new FormulaValidationException(List.of("Unknown field: " + fieldName));
        }
        return definition;
    }

    public boolean exists(String fieldName) {
        return fields.containsKey(normalize(fieldName));
    }

    public FieldDataType getDataType(String fieldName) {
        return resolve(fieldName).dataType();
    }

    public FormulaValueType getFormulaValueType(String fieldName) {
        return toFormulaValueType(resolve(fieldName).dataType());
    }

    public String toSqlExpression(FieldDefinition definition) {
        String qualified = definition.qualifiedColumn();
        if (definition.dataType() == FieldDataType.TEXT && "numcompte".equals(definition.column())) {
            return "CAST(" + qualified + " AS TEXT)";
        }
        return qualified;
    }

    public Set<String> supportedFields() {
        return fields.keySet();
    }

    private FormulaValueType toFormulaValueType(FieldDataType dataType) {
        return switch (dataType) {
            case NUMBER -> FormulaValueType.NUMERIC;
            case TEXT -> FormulaValueType.STRING;
            case DATE -> FormulaValueType.DATE;
            case BOOLEAN -> FormulaValueType.BOOLEAN;
        };
    }

    private void add(
            Map<String, FieldDefinition> catalog,
            String fieldName,
            String column,
            String tableAlias,
            String joinKey,
            FieldDataType dataType
    ) {
        catalog.put(normalize(fieldName), new FieldDefinition(fieldName, column, tableAlias, joinKey, dataType));
    }

    private void alias(Map<String, FieldDefinition> catalog, String aliasName, String canonicalFieldName) {
        FieldDefinition canonical = catalog.get(normalize(canonicalFieldName));
        if (canonical == null) {
            throw new IllegalStateException("Cannot alias unknown field " + canonicalFieldName);
        }
        catalog.put(normalize(aliasName), canonical);
    }

    private String normalize(String fieldName) {
        return fieldName == null ? "" : fieldName.trim().toLowerCase(Locale.ROOT);
    }
}
