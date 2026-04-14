package projet.app.engine.compiler;

public enum JoinKey {
    DIM_CLIENT("LEFT JOIN datamart.dim_client dc ON f.id_client = dc.idtiers"),
    DIM_CONTRAT("LEFT JOIN datamart.dim_contrat dct ON f.id_contrat = dct.id"),

    SUB_DIM_AGENCE_FACT("LEFT JOIN datamart.sub_dim_agence sagf ON f.id_agence = sagf.id"),
    SUB_DIM_DEVISE_FACT("LEFT JOIN datamart.sub_dim_devise sdevf ON f.id_devise = sdevf.id"),
    SUB_DIM_DEVISE_BNQ_FACT("LEFT JOIN datamart.sub_dim_devise sdevbnq ON f.id_devisebnq = sdevbnq.id"),
    SUB_DIM_COMPTE_FACT("LEFT JOIN datamart.sub_dim_compte scompf ON f.id_compte = scompf.id"),
    SUB_DIM_CHAPITRE_FACT("LEFT JOIN datamart.sub_dim_chapitre schapf ON f.id_chapitre = schapf.id"),
    SUB_DIM_DATE_FACT("LEFT JOIN datamart.sub_dim_date sdatef ON f.id_date = sdatef.id"),

    SUB_DIM_RESIDENCE_CLIENT("LEFT JOIN datamart.sub_dim_residence sresc ON dc.id_residence = sresc.id"),
    SUB_DIM_AGENTECO_CLIENT("LEFT JOIN datamart.sub_dim_agenteco sagec ON dc.id_agenteco = sagec.id"),
    SUB_DIM_DOUTEUX_CLIENT("LEFT JOIN datamart.sub_dim_douteux sdoutc ON dc.id_douteux = sdoutc.id"),
    SUB_DIM_GRPAFFAIRE_CLIENT("LEFT JOIN datamart.sub_dim_grpaffaire sgrpc ON dc.id_grpaffaire = sgrpc.id"),
    SUB_DIM_SECTIONACTIVITE_CLIENT("LEFT JOIN datamart.sub_dim_sectionactivite ssecc ON dc.id_sectionactivite = ssecc.id"),

    SUB_DIM_AGENCE_CONTRAT("LEFT JOIN datamart.sub_dim_agence sagct ON dct.id_agence = sagct.id"),
    SUB_DIM_DEVISE_CONTRAT("LEFT JOIN datamart.sub_dim_devise sdevct ON dct.id_devise = sdevct.id"),
    SUB_DIM_OBJETFINANCE_CONTRAT("LEFT JOIN datamart.sub_dim_objetfinance sobjct ON dct.id_objetfinance = sobjct.id"),
    SUB_DIM_TYPCONTRAT_CONTRAT("LEFT JOIN datamart.sub_dim_typcontrat stypct ON dct.id_typcontrat = stypct.id"),
    SUB_DIM_DATE_OUVERTURE_CONTRAT("LEFT JOIN datamart.sub_dim_date sdoc ON dct.id_dateouverture = sdoc.id"),
    SUB_DIM_DATE_ECHEANCE_CONTRAT("LEFT JOIN datamart.sub_dim_date sdec ON dct.id_dateecheance = sdec.id");

    private final String joinSql;

    JoinKey(String joinSql) {
        this.joinSql = joinSql;
    }

    public String getJoinSql() {
        return joinSql;
    }
}
