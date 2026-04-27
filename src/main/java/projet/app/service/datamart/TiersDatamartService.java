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
import java.util.Objects;

/**
 * Builds TIERS datamart dimensions from staging.stg_tiers_raw.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TiersDatamartService {

    private final JdbcTemplate jdbcTemplate;

    private static final Map<Long, String> AGENTECO_LIBELLE = new LinkedHashMap<>();
    private static final Map<Long, String> SECTION_ACTIVITE_LIBELLE = new LinkedHashMap<>();

    static {
        AGENTECO_LIBELLE.put(21100L, "Banque Centrales");
        AGENTECO_LIBELLE.put(21210L, "Centre des Cheques Postaux");
        AGENTECO_LIBELLE.put(21220L, "Caisse National D'Epargne");
        AGENTECO_LIBELLE.put(21230L, "Banques");
        AGENTECO_LIBELLE.put(21240L, "Etablissements financiers a caractere bancaire autorises a recevoir des depots");
        AGENTECO_LIBELLE.put(21250L, "Systemes Financiers Decentralises (SFD) autorises a collecter les depots");
        AGENTECO_LIBELLE.put(21311L, "Societes d'assurances");
        AGENTECO_LIBELLE.put(21312L, "Fonds de pension");
        AGENTECO_LIBELLE.put(21321L, "Etablissements financiers a caractere bancaires non autorises a recevoir des depots");
        AGENTECO_LIBELLE.put(21322L, "SFD non autorises a collecter de l'epargne");
        AGENTECO_LIBELLE.put(21323L, "Divers autres intermediaires financiers");
        AGENTECO_LIBELLE.put(21330L, "Auxiliaires financiers");
        AGENTECO_LIBELLE.put(23100L, "Administration publique centrale");
        AGENTECO_LIBELLE.put(23200L, "Administrations locales et regionales");
        AGENTECO_LIBELLE.put(23300L, "Administrations de securite sociale");
        AGENTECO_LIBELLE.put(24100L, "Entreprises individuelles");
        AGENTECO_LIBELLE.put(24200L, "Particuliers");
        AGENTECO_LIBELLE.put(25000L, "Institutions Sans But Lucratif au Service des Menages (ISBLSM)");
        AGENTECO_LIBELLE.put(26100L, "Banques Multilaterales de Developpement");
        AGENTECO_LIBELLE.put(26200L, "Autres institutions financieres internationales");
        AGENTECO_LIBELLE.put(27000L, "Autres organismes");
        AGENTECO_LIBELLE.put(22120L, "Etablissements publics a caractere indistruel ou commercial");
        AGENTECO_LIBELLE.put(22220L, "Societes non financieres privees nationales");
        AGENTECO_LIBELLE.put(22110L, "Societes publiques non financieres Production biens et services");
        AGENTECO_LIBELLE.put(22210L, "Societes non financieres sous controle etranger");

        SECTION_ACTIVITE_LIBELLE.put(1L, "Agriculture, elevage, chasse et activites de soutien");
        SECTION_ACTIVITE_LIBELLE.put(2L, "Sylviculture, exploitation forestiere et activites de soutien");
        SECTION_ACTIVITE_LIBELLE.put(3L, "Peche, pisciculture, acquaculture");
        SECTION_ACTIVITE_LIBELLE.put(5L, "Extraction de charbon et lignite");
        SECTION_ACTIVITE_LIBELLE.put(6L, "Extraction d'hydrocarbure");
        SECTION_ACTIVITE_LIBELLE.put(7L, "Extraction de minerais metallurgiques");
        SECTION_ACTIVITE_LIBELLE.put(8L, "Autres activites extractives");
        SECTION_ACTIVITE_LIBELLE.put(9L, "Activites de soutien au industrie extractives");
        SECTION_ACTIVITE_LIBELLE.put(10L, "Fabrication de produits alimentaires");
        SECTION_ACTIVITE_LIBELLE.put(11L, "fabrication de boissons");
        SECTION_ACTIVITE_LIBELLE.put(12L, "fabrication de produit a base de tabac");
        SECTION_ACTIVITE_LIBELLE.put(13L, "activites de fabrication de textiles");
        SECTION_ACTIVITE_LIBELLE.put(14L, "fabrication d'articles d'habillement");
        SECTION_ACTIVITE_LIBELLE.put(15L, "travail de cuir, fabrication d'articles de voyage et chaussures");
        SECTION_ACTIVITE_LIBELLE.put(16L, "travail de bois et fabrication d'articles hors meubles");
        SECTION_ACTIVITE_LIBELLE.put(17L, "fabrication du papier et du carton");
        SECTION_ACTIVITE_LIBELLE.put(18L, "imprimerie et reproduction d'enregistrements");
        SECTION_ACTIVITE_LIBELLE.put(19L, "raffinage petrolier, cokefaction");
        SECTION_ACTIVITE_LIBELLE.put(20L, "fabrication de produits chimiques");
        SECTION_ACTIVITE_LIBELLE.put(21L, "fabrication de produits pharmaceutiques");
        SECTION_ACTIVITE_LIBELLE.put(22L, "travail du caoutchouc et du plastic");
        SECTION_ACTIVITE_LIBELLE.put(23L, "fabrication de materiaux mineraux");
        SECTION_ACTIVITE_LIBELLE.put(24L, "metallurgie");
        SECTION_ACTIVITE_LIBELLE.put(25L, "fabrication d'ouvrage en metaux");
        SECTION_ACTIVITE_LIBELLE.put(26L, "fabrication de produits electroniques et informatiques");
        SECTION_ACTIVITE_LIBELLE.put(27L, "fabrication d'equipement informatiques");
        SECTION_ACTIVITE_LIBELLE.put(28L, "fabrication de machines et d'equipements NCA");
        SECTION_ACTIVITE_LIBELLE.put(29L, "construction de vehicules automobiles");
        SECTION_ACTIVITE_LIBELLE.put(30L, "fabrication d'autres materiaux de transport");
        SECTION_ACTIVITE_LIBELLE.put(31L, "fabrication de meubles et metals");
        SECTION_ACTIVITE_LIBELLE.put(32L, "autres indutries manufacturieres");
        SECTION_ACTIVITE_LIBELLE.put(33L, "reparation et installation de machines et d'equipements profetionnels");
        SECTION_ACTIVITE_LIBELLE.put(35L, "production et distribution d'electricite et de gaz");
        SECTION_ACTIVITE_LIBELLE.put(36L, "captage, traitement et distribution d'eau");
        SECTION_ACTIVITE_LIBELLE.put(37L, "collecte et traitement d'eaux usees");
        SECTION_ACTIVITE_LIBELLE.put(38L, "collecte, traitement et elimination des dechets, recuperation");
        SECTION_ACTIVITE_LIBELLE.put(39L, "depolution et autres activites de gestion des dechets");
        SECTION_ACTIVITE_LIBELLE.put(41L, "construction de batiments");
        SECTION_ACTIVITE_LIBELLE.put(42L, "genie civil");
        SECTION_ACTIVITE_LIBELLE.put(43L, "activites specialisees de construction");
        SECTION_ACTIVITE_LIBELLE.put(45L, "commerce et reparation d'automobiles et de motocycles");
        SECTION_ACTIVITE_LIBELLE.put(46L, "commerce de gros et activies des intermediaires");
        SECTION_ACTIVITE_LIBELLE.put(47L, "commerce de detail");
        SECTION_ACTIVITE_LIBELLE.put(49L, "transports des terrestres");
        SECTION_ACTIVITE_LIBELLE.put(50L, "transpotrs par eau");
        SECTION_ACTIVITE_LIBELLE.put(51L, "transport aeriens");
        SECTION_ACTIVITE_LIBELLE.put(52L, "entroposages et activites des auxiliaires de transports");
        SECTION_ACTIVITE_LIBELLE.put(53L, "activites  de poste de courrier");
        SECTION_ACTIVITE_LIBELLE.put(55L, "hebergement");
        SECTION_ACTIVITE_LIBELLE.put(56L, "restauration et debits de boisson");
        SECTION_ACTIVITE_LIBELLE.put(58L, "edition");
        SECTION_ACTIVITE_LIBELLE.put(59L, "production audio et video: television, cinema, son");
        SECTION_ACTIVITE_LIBELLE.put(60L, "programmation televisuelle; radiodiffusion");
        SECTION_ACTIVITE_LIBELLE.put(61L, "telecommunications");
        SECTION_ACTIVITE_LIBELLE.put(62L, "activites informatiques");
        SECTION_ACTIVITE_LIBELLE.put(63L, "activites de fournitures d'informatiques");
        SECTION_ACTIVITE_LIBELLE.put(64L, "activites financieres");
        SECTION_ACTIVITE_LIBELLE.put(65L, "assurance");
        SECTION_ACTIVITE_LIBELLE.put(66L, "activites d'auxiliaires financieres et d'assurance");
        SECTION_ACTIVITE_LIBELLE.put(68L, "activites immobiliaires");
        SECTION_ACTIVITE_LIBELLE.put(69L, "activites juridiques et comptables");
        SECTION_ACTIVITE_LIBELLE.put(70L, "activites des sieges sociaux conseils en gestion");
        SECTION_ACTIVITE_LIBELLE.put(71L, "activites d'architecture, d'ingenierie et et techniques");
        SECTION_ACTIVITE_LIBELLE.put(72L, "recherche developpement");
        SECTION_ACTIVITE_LIBELLE.put(73L, "publicite et etudes de marche");
        SECTION_ACTIVITE_LIBELLE.put(74L, "autres activite de profetionnelles de services specialisees");
        SECTION_ACTIVITE_LIBELLE.put(75L, "activites veterinaires");
        SECTION_ACTIVITE_LIBELLE.put(77L, "location et location bail");
        SECTION_ACTIVITE_LIBELLE.put(78L, "activites liees aux ressources humaines");
        SECTION_ACTIVITE_LIBELLE.put(79L, "activites des agences de reservation et voyagistes");
        SECTION_ACTIVITE_LIBELLE.put(80L, "enquetes et securite");
        SECTION_ACTIVITE_LIBELLE.put(81L, "soutien aux batiments, amenagement paysagers");
        SECTION_ACTIVITE_LIBELLE.put(82L, "activites de soutien aux entreprises, activites de bureau");
        SECTION_ACTIVITE_LIBELLE.put(84L, "activites d'administration publique");
        SECTION_ACTIVITE_LIBELLE.put(85L, "enseignement");
        SECTION_ACTIVITE_LIBELLE.put(86L, "activites pour la sante humaine");
        SECTION_ACTIVITE_LIBELLE.put(87L, "activites d'hebergement medicaux social et social");
        SECTION_ACTIVITE_LIBELLE.put(88L, "action social sans hebergement");
        SECTION_ACTIVITE_LIBELLE.put(90L, "activites recreatives");
        SECTION_ACTIVITE_LIBELLE.put(91L, "conservation et valorisation du patrimoine");
        SECTION_ACTIVITE_LIBELLE.put(92L, "organisation de jeux de hasart et d'argent");
        SECTION_ACTIVITE_LIBELLE.put(93L, "activites sportives, recreatives et de loisirs");
        SECTION_ACTIVITE_LIBELLE.put(94L, "activites des organisations associatives");
        SECTION_ACTIVITE_LIBELLE.put(95L, "reparation d'odinateurs, biens personnels et domestiques");
        SECTION_ACTIVITE_LIBELLE.put(96L, "fournitures d'autres services personnels");
        SECTION_ACTIVITE_LIBELLE.put(97L, "activites des menages en tant qu'employeurs de personnel");
        SECTION_ACTIVITE_LIBELLE.put(98L, "activites indifferenciees auto-produites des menages");
        SECTION_ACTIVITE_LIBELLE.put(99L, "Activites des organisations extraterritoriales");
        SECTION_ACTIVITE_LIBELLE.put(100L, "Autres");
    }

    @Transactional
    public LoadResult loadTiersDatamart() {
        log.info("[TIERS] Starting datamart load");

        log.info("[TIERS] Ensuring datamart tables exist...");
        ensureDatamartTablesExist();
        log.info("[TIERS] Datamart tables ready");

        log.info("[TIERS] Creating staging indexes for join performance...");
        createStagingIndexes();
        log.info("[TIERS] Staging indexes ready");

        log.info("[TIERS] Upserting sub_dim_agenteco...");
        int agentecoRows = upsertLabelDimension("sub_dim_agenteco", AGENTECO_LIBELLE);
        log.info("[TIERS] sub_dim_agenteco done: {} rows", agentecoRows);

        log.info("[TIERS] Upserting sub_dim_sectionactivite...");
        int sectionRows = upsertLabelDimension("sub_dim_sectionactivite", SECTION_ACTIVITE_LIBELLE);
        log.info("[TIERS] sub_dim_sectionactivite done: {} rows", sectionRows);

        log.info("[TIERS] Populating sub_dim_residence...");
        int residenceRows = populateResidenceDimension();
        log.info("[TIERS] sub_dim_residence done: {} rows", residenceRows);

        log.info("[TIERS] Populating sub_dim_douteux...");
        int douteuxRows = populateDouteuxDimension();
        log.info("[TIERS] sub_dim_douteux done: {} rows", douteuxRows);

        log.info("[TIERS] Populating sub_dim_grpaffaire...");
        int grpAffRows = populateGrpAffaireDimension();
        log.info("[TIERS] sub_dim_grpaffaire done: {} rows", grpAffRows);

        log.info("[TIERS] Populating dim_client...");
        int dimClientRows = populateDimClient();
        log.info("[TIERS] dim_client done: {} rows", dimClientRows);

        LoadResult result = new LoadResult();
        result.setSubDimResidenceRows(residenceRows);
        result.setSubDimAgentecoRows(agentecoRows);
        result.setSubDimDouteuxRows(douteuxRows);
        result.setSubDimGrpaffaireRows(grpAffRows);
        result.setSubDimSectionactiviteRows(sectionRows);
        result.setDimClientRows(dimClientRows);

        log.info("[TIERS] Datamart load completed: {}", result);
        return result;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> fetchClientList(int page, int size) {
        int normalizedPage = Math.max(page, 0);
        int normalizedSize = size > 0 ? size : 20;
        int offset = normalizedPage * normalizedSize;

        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM datamart.dim_client", Long.class);
        long totalElements = total == null ? 0L : total;

        List<Map<String, Object>> items = jdbcTemplate.queryForList("""
                SELECT
                    c.idtiers,
                    c.nomprenom,
                    c.raisonsoc,
                    c.chiffreaffaires,
                    r.pays,
                    r.geo,
                    ae.libelle AS libelle,
                    d.douteux,
                    ga.nomgrpaffaires AS nomgroupaffaire,
                    sa.libelle AS sectionactivite
                FROM datamart.dim_client c
                LEFT JOIN datamart.sub_dim_residence r ON r.id = c.id_residence
                LEFT JOIN datamart.sub_dim_agenteco ae ON ae.id = c.id_agenteco
                LEFT JOIN datamart.sub_dim_douteux d ON d.id = c.id_douteux
                LEFT JOIN datamart.sub_dim_grpaffaire ga ON ga.id = c.id_grpaffaire
                LEFT JOIN datamart.sub_dim_sectionactivite sa ON sa.id = c.id_sectionactivite
                ORDER BY c.idtiers
                LIMIT ? OFFSET ?
                """, normalizedSize, offset);

        return buildPaginatedResponse(normalizedPage, normalizedSize, totalElements, items);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> fetchResidenceList(int page, int size) {
        return fetchSimpleList(
                "datamart.sub_dim_residence",
                "SELECT id, pays, residence, geo FROM datamart.sub_dim_residence ORDER BY id LIMIT ? OFFSET ?",
                page,
                size
        );
    }

    @Transactional(readOnly = true)
    public Map<String, Object> fetchAgentecoList(int page, int size) {
        return fetchSimpleList(
                "datamart.sub_dim_agenteco",
                "SELECT id, libelle FROM datamart.sub_dim_agenteco ORDER BY id LIMIT ? OFFSET ?",
                page,
                size
        );
    }

    @Transactional(readOnly = true)
    public Map<String, Object> fetchDouteuxList(int page, int size) {
        return fetchSimpleList(
                "datamart.sub_dim_douteux",
                "SELECT id, douteux, datdouteux FROM datamart.sub_dim_douteux ORDER BY id LIMIT ? OFFSET ?",
                page,
                size
        );
    }

    @Transactional(readOnly = true)
    public Map<String, Object> fetchGrpAffaireList(int page, int size) {
        return fetchSimpleList(
                "datamart.sub_dim_grpaffaire",
                "SELECT id, nomgrpaffaires AS nomgroupaffaire FROM datamart.sub_dim_grpaffaire ORDER BY id LIMIT ? OFFSET ?",
                page,
                size
        );
    }

    @Transactional(readOnly = true)
    public Map<String, Object> fetchSectionActiviteList(int page, int size) {
        return fetchSimpleList(
                "datamart.sub_dim_sectionactivite",
                "SELECT id, libelle FROM datamart.sub_dim_sectionactivite ORDER BY id LIMIT ? OFFSET ?",
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
            CREATE TABLE IF NOT EXISTS datamart.sub_dim_residence (
                id BIGSERIAL PRIMARY KEY,
                pays TEXT,
                residence TEXT,
                geo TEXT,
                CONSTRAINT uq_sub_dim_residence UNIQUE (pays, residence, geo)
            )
            """);

        // Ensure uniqueness exists even when table was created in earlier versions without constraints.
        jdbcTemplate.execute("""
            CREATE UNIQUE INDEX IF NOT EXISTS ux_sub_dim_residence_pays_residence_geo
            ON datamart.sub_dim_residence (pays, residence, geo)
            """);

        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS datamart.sub_dim_agenteco (
                id BIGINT PRIMARY KEY,
                libelle TEXT
            )
            """);

        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS datamart.sub_dim_douteux (
                id BIGSERIAL PRIMARY KEY,
                douteux INTEGER,
                datdouteux DATE,
                CONSTRAINT uq_sub_dim_douteux UNIQUE (douteux, datdouteux)
            )
            """);

        jdbcTemplate.execute("""
            CREATE UNIQUE INDEX IF NOT EXISTS ux_sub_dim_douteux_douteux_datdouteux
            ON datamart.sub_dim_douteux (douteux, datdouteux)
            """);

        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS datamart.sub_dim_grpaffaire (
                id BIGINT PRIMARY KEY,
                nomgrpaffaires TEXT
            )
            """);

        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS datamart.sub_dim_sectionactivite (
                id BIGINT PRIMARY KEY,
                libelle TEXT
            )
            """);

        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS datamart.dim_client (
                idtiers TEXT PRIMARY KEY,
                id_residence BIGINT REFERENCES datamart.sub_dim_residence(id),
                id_agenteco BIGINT REFERENCES datamart.sub_dim_agenteco(id),
                id_douteux BIGINT REFERENCES datamart.sub_dim_douteux(id),
                id_grpaffaire BIGINT REFERENCES datamart.sub_dim_grpaffaire(id),
                id_sectionactivite BIGINT REFERENCES datamart.sub_dim_sectionactivite(id),
                nomprenom TEXT,
                raisonsoc TEXT,
                chiffreaffaires TEXT
            )
            """);
    }

    private int upsertLabelDimension(String tableName, Map<Long, String> mapping) {
        String sql = "INSERT INTO datamart." + tableName + " (id, libelle) VALUES (?, ?) " +
                "ON CONFLICT (id) DO NOTHING";

        int[][] counts = jdbcTemplate.batchUpdate(
                sql,
                mapping.entrySet(),
                100,
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

    private int populateResidenceDimension() {
        String geoExpr = geoExpression("t.residencenum");

        String sql = Objects.requireNonNull("""
            INSERT INTO datamart.sub_dim_residence (pays, residence, geo)
            SELECT DISTINCT
                NULLIF(TRIM(t.nationalite), '') AS pays,
                NULLIF(TRIM(t.residence), '') AS residence,
                %s AS geo
            FROM staging.stg_tiers_raw t
            WHERE NULLIF(TRIM(t.nationalite), '') IS NOT NULL
               OR NULLIF(TRIM(t.residence), '') IS NOT NULL
               OR t.residencenum IS NOT NULL
            ON CONFLICT (pays, residence, geo) DO NOTHING
            """.formatted(geoExpr));

        return jdbcTemplate.update(sql);
    }

    private int populateDouteuxDimension() {
        String sql = """
            INSERT INTO datamart.sub_dim_douteux (douteux, datdouteux)
            SELECT DISTINCT
                CASE
                    WHEN NULLIF(TRIM(t.douteux), '') ~ '^-?[0-9]+$' THEN NULLIF(TRIM(t.douteux), '')::INTEGER
                    ELSE NULL
                END AS douteux,
                CASE
                    WHEN NULLIF(TRIM(t.datdouteux), '') ~ '^\\d{2}/\\d{2}/\\d{4}$' THEN TO_DATE(NULLIF(TRIM(t.datdouteux), ''), 'DD/MM/YYYY')
                    WHEN NULLIF(TRIM(t.datdouteux), '') ~ '^\\d{4}-\\d{2}-\\d{2}$' THEN NULLIF(TRIM(t.datdouteux), '')::DATE
                    ELSE NULL
                END AS datdouteux
            FROM staging.stg_tiers_raw t
            WHERE NULLIF(TRIM(t.douteux), '') IS NOT NULL
               OR NULLIF(TRIM(t.datdouteux), '') IS NOT NULL
            ON CONFLICT (douteux, datdouteux) DO NOTHING
            """;

        return jdbcTemplate.update(sql);
    }

    private int populateGrpAffaireDimension() {
        String sql = """
            INSERT INTO datamart.sub_dim_grpaffaire (id, nomgrpaffaires)
            SELECT DISTINCT
                t.grpaffaires::BIGINT AS id,
                NULLIF(TRIM(t.nomgrpaffaires), '') AS nomgrpaffaires
            FROM staging.stg_tiers_raw t
            WHERE t.grpaffaires IS NOT NULL
            ON CONFLICT (id) DO NOTHING
            """;

        return jdbcTemplate.update(sql);
    }

    private int populateDimClient() {
        String geoExpr = geoExpression("residencenum");

        jdbcTemplate.execute("DROP TABLE IF EXISTS staging.tmp_tiers_prep");
        jdbcTemplate.execute(Objects.requireNonNull("""
            CREATE TABLE staging.tmp_tiers_prep AS
            SELECT
                NULLIF(TRIM(t.idtiers), '') AS idtiers,
                NULLIF(TRIM(t.nationalite), '') AS pays,
                NULLIF(TRIM(t.residence), '') AS residence,
                %s AS geo,
                CASE WHEN NULLIF(TRIM(t.agenteco), '') ~ '^[0-9]+$'
                     THEN NULLIF(TRIM(t.agenteco), '')::BIGINT END AS agenteco_id,
                CASE WHEN NULLIF(TRIM(t.sectionactivite), '') ~ '^[0-9]+$'
                     THEN NULLIF(TRIM(t.sectionactivite), '')::BIGINT END AS section_id,
                t.grpaffaires,
                CASE WHEN NULLIF(TRIM(t.douteux), '') ~ '^-?[0-9]+$'
                     THEN NULLIF(TRIM(t.douteux), '')::INTEGER END AS douteux_val,
                CASE WHEN NULLIF(TRIM(t.datdouteux), '') ~ '^\\d{2}/\\d{2}/\\d{4}$'
                          THEN TO_DATE(NULLIF(TRIM(t.datdouteux), ''), 'DD/MM/YYYY')
                     WHEN NULLIF(TRIM(t.datdouteux), '') ~ '^\\d{4}-\\d{2}-\\d{2}$'
                          THEN NULLIF(TRIM(t.datdouteux), '')::DATE
                     END AS datdouteux_val,
                NULLIF(TRIM(t.nomprenom), '') AS nomprenom,
                NULLIF(TRIM(t.raisonsoc), '') AS raisonsoc,
                NULLIF(TRIM(t.chiffreaffaires), '') AS chiffreaffaires
            FROM staging.stg_tiers_raw t
            WHERE NULLIF(TRIM(t.idtiers), '') IS NOT NULL
            """.formatted(geoExpr)));

        log.info("[TIERS] tmp_tiers_prep created, adding indexes...");
        jdbcTemplate.execute("CREATE INDEX ON staging.tmp_tiers_prep (idtiers)");
        jdbcTemplate.execute("CREATE INDEX ON staging.tmp_tiers_prep (pays, residence, geo)");
        jdbcTemplate.execute("CREATE INDEX ON staging.tmp_tiers_prep (douteux_val, datdouteux_val)");
        log.info("[TIERS] tmp_tiers_prep indexes ready, inserting into dim_client...");

        String insertSql = """
            INSERT INTO datamart.dim_client (
                idtiers, id_residence, id_agenteco, id_douteux,
                id_grpaffaire, id_sectionactivite,
                nomprenom, raisonsoc, chiffreaffaires
            )
            SELECT
                p.idtiers,
                sr.id,
                sae.id,
                sd.id,
                sga.id,
                ssa.id,
                p.nomprenom,
                p.raisonsoc,
                p.chiffreaffaires
            FROM staging.tmp_tiers_prep p
            LEFT JOIN datamart.sub_dim_residence sr
                   ON sr.pays IS NOT DISTINCT FROM p.pays
                  AND sr.residence IS NOT DISTINCT FROM p.residence
                  AND sr.geo IS NOT DISTINCT FROM p.geo
            LEFT JOIN datamart.sub_dim_agenteco sae ON sae.id = p.agenteco_id
            LEFT JOIN datamart.sub_dim_sectionactivite ssa ON ssa.id = p.section_id
            LEFT JOIN datamart.sub_dim_grpaffaire sga ON sga.id = p.grpaffaires
            LEFT JOIN datamart.sub_dim_douteux sd
                   ON sd.douteux IS NOT DISTINCT FROM p.douteux_val
                  AND sd.datdouteux IS NOT DISTINCT FROM p.datdouteux_val
            ON CONFLICT (idtiers) DO NOTHING
            """;

        int rows = jdbcTemplate.update(insertSql);

        jdbcTemplate.execute("DROP TABLE IF EXISTS staging.tmp_tiers_prep");
        return rows;
    }

    private void createStagingIndexes() {
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS ix_stg_tiers_idtiers ON staging.stg_tiers_raw (idtiers)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS ix_stg_tiers_grpaffaires ON staging.stg_tiers_raw (grpaffaires)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS ix_stg_tiers_residencenum ON staging.stg_tiers_raw (residencenum)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS ix_stg_contrat_idtiers ON staging.stg_contrat_raw (idtiers)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS ix_stg_contrat_idcontrat ON staging.stg_contrat_raw (idcontrat)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS ix_stg_compta_idtiers ON staging.stg_compta_raw (idtiers)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS ix_stg_compta_idcontrat ON staging.stg_compta_raw (idcontrat)");
    }

    private String geoExpression(String residenceNumExpr) {
        return "CASE " + residenceNumExpr +
                " WHEN 110 THEN 'Etat du Declarant'" +
                " WHEN 120 THEN 'Autres Etats Membre de l''UMOA'" +
                " WHEN 130 THEN 'Residents UMOA'" +
                " WHEN 141 THEN 'Autre pays de la Zone Franc'" +
                " WHEN 142 THEN 'Autre Etats membres de la CEDEAO'" +
                " WHEN 143 THEN 'Zone euro'" +
                " WHEN 144 THEN 'Autres Etats'" +
                " ELSE 'Autres Etats' END";
    }

    @Data
    public static class LoadResult {
        private int subDimResidenceRows;
        private int subDimAgentecoRows;
        private int subDimDouteuxRows;
        private int subDimGrpaffaireRows;
        private int subDimSectionactiviteRows;
        private int dimClientRows;
    }
}
