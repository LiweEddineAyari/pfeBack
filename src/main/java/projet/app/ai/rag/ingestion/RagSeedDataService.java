package projet.app.ai.rag.ingestion;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import projet.app.ai.rag.entity.RagDocumentEntity;
import projet.app.ai.rag.repository.RagDocumentRepository;

import javax.sql.DataSource;
import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Production-grade seeder that populates {@code rag.documents} with all
 * financial knowledge extracted from the BFI appetite grid and parameter sheets.
 *
 * <p>Generates ~75 self-contained knowledge chunks:
 * <ul>
 *   <li>26 × {@code PARAMETER_DEFINITION} — one per parameter (FPE, RCR, …)</li>
 *   <li>~33 × {@code RATIO_DEFINITION} + {@code THRESHOLD_INTERPRETATION} + {@code RECOMMENDATION}</li>
 *   <li>12 × {@code RATIO_DEFINITION} + {@code THRESHOLD_INTERPRETATION} (ratios without specific action plans)</li>
 *   <li>4  × cross-domain ({@code REGULATION}, {@code RATIO_RELATIONSHIP}, {@code RISK_INTERPRETATION})</li>
 * </ul>
 *
 * <p>Idempotent: {@link #seed()} is a no-op if documents for {@value #SOURCE} already exist.
 * Force re-seed by calling {@code repository.deleteBySource(SOURCE)} first, then {@link #seed()}.
 *
 * <p>Embedding strategy: chunks are batched in groups of {@value #EMBED_BATCH} and sent to OpenAI
 * in a single {@code embedAll()} call per batch. If no embedding model is available (missing API key
 * or pgvector not installed) chunks are still persisted with {@code embedding = NULL} — they remain
 * fully searchable via FTS and exact-code matching.
 */
@Slf4j
@Service
public class RagSeedDataService {

    public static final String SOURCE   = "appetence_grid_2024_bfi";
    private static final String LANGUAGE = "fr";
    private static final int    EMBED_BATCH = 50;

    /** INSERT used when the {@code embedding} column exists (pgvector installed). */
    private static final String INSERT_SQL_WITH_EMBEDDING = """
            INSERT INTO rag.documents (
                id, title, content, embedding, document_type, category,
                ratio_code, parameter_code, ratio_family, domain,
                regulation, source, keywords, language, created_at
            ) VALUES (?, ?, ?, ?::vector, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (id) DO NOTHING
            """;

    /** INSERT used when pgvector is absent — the {@code embedding} column does not exist. */
    private static final String INSERT_SQL_NO_EMBEDDING = """
            INSERT INTO rag.documents (
                id, title, content, document_type, category,
                ratio_code, parameter_code, ratio_family, domain,
                regulation, source, keywords, language, created_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (id) DO NOTHING
            """;

    private final RagDocumentRepository          repository;
    private final ObjectProvider<EmbeddingModel> embeddingModelProvider;
    private final JdbcTemplate                   jdbc;
    private final int                            embeddingDimension;

    public RagSeedDataService(RagDocumentRepository repository,
                               ObjectProvider<EmbeddingModel> embeddingModelProvider,
                               DataSource dataSource,
                               @Value("${ai.rag.embedding-dimension:1536}") int embeddingDimension) {
        this.repository             = repository;
        this.embeddingModelProvider = embeddingModelProvider;
        this.jdbc                   = new JdbcTemplate(dataSource);
        this.embeddingDimension     = embeddingDimension;
    }

    // ─── Public API ────────────────────────────────────────────────────────────

    public SeedReport seed() {
        long existing = repository.countBySource(SOURCE);
        if (existing > 0) {
            log.info("[RagSeed] Already seeded — {} documents for source '{}'", existing, SOURCE);
            return SeedReport.skipped(existing);
        }

        List<RagDocumentEntity> chunks = buildAllChunks();
        EmbeddingModel model = embeddingModelProvider.getIfAvailable();
        if (model == null) {
            log.warn("[RagSeed] No embedding model — chunks stored WITHOUT vectors (FTS only).");
        }

        long inserted = 0;
        long failed   = 0;

        for (int start = 0; start < chunks.size(); start += EMBED_BATCH) {
            int end   = Math.min(start + EMBED_BATCH, chunks.size());
            List<RagDocumentEntity> batch = chunks.subList(start, end);

            List<Embedding> embeddings;
            try {
                embeddings = embedBatch(model, batch);
            } catch (RuntimeException ex) {
                log.warn("[RagSeed] Embedding batch {}-{} failed (continuing without vectors): {}",
                        start, end, rootCause(ex));
                embeddings = List.of();
            }

            // Insert each chunk individually so a single bad row doesn't sink the whole batch.
            for (int i = 0; i < batch.size(); i++) {
                Embedding emb = i < embeddings.size() ? embeddings.get(i) : null;
                try {
                    insertChunk(batch.get(i), emb);
                    inserted++;
                } catch (RuntimeException ex) {
                    failed++;
                    log.warn("[RagSeed] Insert failed for chunk '{}': {}",
                            batch.get(i).getTitle(), rootCause(ex));
                }
            }

            if (model != null) {
                try {
                    Thread.sleep(300);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        log.info("[RagSeed] Complete — inserted={}, failed={}, source='{}'", inserted, failed, SOURCE);
        return failed == 0 ? SeedReport.success(inserted) : SeedReport.partial(inserted, failed);
    }

    // ─── Embedding ─────────────────────────────────────────────────────────────

    private List<Embedding> embedBatch(EmbeddingModel model, List<RagDocumentEntity> batch) {
        if (model == null) return List.of();
        List<TextSegment> segments = batch.stream()
                .map(c -> TextSegment.from(c.getTitle() + "\n\n" + c.getContent()))
                .toList();
        return model.embedAll(segments).content();
    }

    // ─── JDBC insert ───────────────────────────────────────────────────────────

    private void insertChunk(RagDocumentEntity c, Embedding embedding) {
        boolean withEmbedding = hasEmbeddingColumn();
        String  sql           = withEmbedding ? INSERT_SQL_WITH_EMBEDDING : INSERT_SQL_NO_EMBEDDING;
        String  vec           = (withEmbedding && embedding != null)
                ? toPgVectorLiteral(embedding.vector(), embeddingDimension)
                : null;

        // Use a PreparedStatementSetter so we can call connection.createArrayOf("text", ...)
        // for the keywords TEXT[] column — JdbcTemplate.update(sql, args...) cannot bind
        // Java String[] to a PostgreSQL TEXT[] column directly.
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(sql);
            int p = 1;
            ps.setObject(p++, c.getId());
            ps.setString(p++, c.getTitle());
            ps.setString(p++, c.getContent());
            if (withEmbedding) {
                if (vec == null) ps.setNull(p++, Types.OTHER);
                else             ps.setString(p++, vec);
            }
            setNullableString(ps, p++, c.getDocumentType());
            setNullableString(ps, p++, c.getCategory());
            setNullableString(ps, p++, c.getRatioCode());
            setNullableString(ps, p++, c.getParameterCode());
            setNullableString(ps, p++, c.getRatioFamily());
            setNullableString(ps, p++, c.getDomain());
            setNullableString(ps, p++, c.getRegulation());
            setNullableString(ps, p++, c.getSource());
            setTextArray(ps, p++, con, c.getKeywords());
            setNullableString(ps, p++, c.getLanguage() == null ? LANGUAGE : c.getLanguage());
            ps.setTimestamp(p, Timestamp.from(c.getCreatedAt() == null ? Instant.now() : c.getCreatedAt()));
            return ps;
        });
    }

    private static void setNullableString(PreparedStatement ps, int idx, String value)
            throws SQLException {
        if (value == null) ps.setNull(idx, Types.VARCHAR);
        else               ps.setString(idx, value);
    }

    private static void setTextArray(PreparedStatement ps, int idx,
                                     Connection con, String[] values) throws SQLException {
        if (values == null || values.length == 0) {
            ps.setNull(idx, Types.ARRAY);
            return;
        }
        Array arr = con.createArrayOf("text", values);
        ps.setArray(idx, arr);
    }

    private static String rootCause(Throwable t) {
        Throwable cur = t;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        return cur.getClass().getSimpleName() + ": " + cur.getMessage();
    }

    /**
     * Cached check for whether {@code rag.documents.embedding} exists.
     * Computed once per app instance; pgvector availability cannot change at runtime.
     */
    private Boolean embeddingColumnExists;

    private boolean hasEmbeddingColumn() {
        if (embeddingColumnExists == null) {
            try {
                Integer count = jdbc.queryForObject("""
                        SELECT COUNT(*) FROM information_schema.columns
                        WHERE table_schema = 'rag'
                          AND table_name   = 'documents'
                          AND column_name  = 'embedding'
                        """, Integer.class);
                embeddingColumnExists = count != null && count > 0;
                log.info("[RagSeed] embedding column present: {}", embeddingColumnExists);
            } catch (RuntimeException ex) {
                log.warn("[RagSeed] Could not introspect rag.documents schema: {}", ex.getMessage());
                embeddingColumnExists = false;
            }
        }
        return embeddingColumnExists;
    }

    private static String toPgVectorLiteral(float[] vec, int expectedDim) {
        if (vec.length != expectedDim) {
            throw new IllegalStateException(
                    "Embedding dimension mismatch: got " + vec.length + ", expected " + expectedDim);
        }
        StringBuilder sb = new StringBuilder(vec.length * 8);
        sb.append('[');
        for (int i = 0; i < vec.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(vec[i]);
        }
        sb.append(']');
        return sb.toString();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // CHUNK BUILDERS
    // ═══════════════════════════════════════════════════════════════════════════

    private List<RagDocumentEntity> buildAllChunks() {
        List<RagDocumentEntity> all = new ArrayList<>();
        all.addAll(buildParameterChunks());
        all.addAll(buildRatioChunks());
        all.addAll(buildCrossDomainChunks());
        log.info("[RagSeed] Built {} knowledge chunks in memory", all.size());
        return all;
    }

    // ─── PART A — Parameters ───────────────────────────────────────────────────

    private List<RagDocumentEntity> buildParameterChunks() {
        List<RagDocumentEntity> p = new ArrayList<>();

        // ── A.1 Paramètres Prudentiels ──────────────────────────────────────────
        p.add(param("FPE", "Fonds propres effectifs", "solvency",
                "Les Fonds Propres Effectifs (FPE) représentent le capital total disponible "
                + "de l'établissement bancaire pour couvrir les pertes inattendues et satisfaire "
                + "aux exigences réglementaires de solvabilité. Ils constituent le dénominateur "
                + "commun de nombreux ratios prudentiels. "
                + "FPE est utilisé directement dans le calcul du Ratio de Solvabilité "
                + "(RS = FPE / (RCR + RM + RO)), dans la rentabilité financière (ROE = RNET / FPE) "
                + "et dans la limite des immobilisations. "
                + "Un niveau insuffisant de FPE par rapport aux actifs pondérés entraîne "
                + "un non-respect des normes prudentielles Bâle III et peut déclencher "
                + "des mesures correctives réglementaires immédiates.",
                "fonds propres", "capital", "solvabilité", "fpe", "tier 1", "tier 2", "bilan"));

        p.add(param("RCR", "Risque Crédit", "credit_risk",
                "Le Risque Crédit (RCR) représente l'exposition totale aux risques de crédit "
                + "de l'établissement, exprimée en actifs pondérés par le risque de crédit (APR Crédit). "
                + "Il constitue la composante principale du dénominateur du Ratio de Solvabilité "
                + "(RS = FPE / (RCR + RM + RO)). "
                + "Une augmentation du RCR sans renforcement des FPE entraîne une détérioration "
                + "du ratio RS et un rapprochement vers les seuils d'alerte prudentiels.",
                "risque crédit", "apr", "actifs pondérés", "rcr", "solvabilité", "crédit"));

        p.add(param("RM", "Risque Marché", "market_risk",
                "Le Risque Marché (RM) représente l'exigence en fonds propres au titre "
                + "des risques de marché : risque de taux d'intérêt sur le portefeuille de négociation, "
                + "risque de change, risque sur titres de capital. "
                + "Il constitue l'une des trois composantes du dénominateur du Ratio de Solvabilité "
                + "(RS = FPE / (RCR + RM + RO)). "
                + "Sa maîtrise passe par un encadrement strict des positions de trading "
                + "et une couverture adéquate des expositions de marché.",
                "risque marché", "rm", "taux intérêt", "change", "trading", "var", "solvabilité"));

        p.add(param("RO", "Risque Opérationnel", "op_risk",
                "Le Risque Opérationnel (RO) représente l'exigence en fonds propres "
                + "au titre des risques opérationnels, conformément aux approches Bâle III "
                + "(approche indicateur de base ou approche standard). "
                + "Il inclut les pertes résultant de processus internes défaillants, "
                + "d'erreurs humaines, de défaillances systèmes ou d'événements externes. "
                + "RO est la troisième composante du dénominateur du Ratio de Solvabilité "
                + "(RS = FPE / (RCR + RM + RO)).",
                "risque opérationnel", "ro", "fraude", "incident", "processus", "bâle iii"));

        p.add(param("FPBT1", "Fonds propres de base Tier 1", "solvency",
                "Les Fonds Propres de Base Tier 1 (FPBT1) représentent les fonds propres "
                + "de la meilleure qualité selon le référentiel Bâle III : capital social, "
                + "réserves, résultat reporté et instruments Additional Tier 1 (AT1). "
                + "FPBT1 sert de base au calcul du Ratio de levier (RL = FPBT1 / TOEXP), "
                + "du Ratio CET1 (RCET1 = FPBT1 / APR) et du Ratio T1 (RT1 = FPBT1 / APR). "
                + "C'est la mesure de solidité financière la plus stricte utilisée par les régulateurs.",
                "tier 1", "cet1", "fonds propres durs", "fpbt1", "capital réglementaire", "at1"));

        p.add(param("FPT1", "Fonds propres Tier 1", "solvency",
                "Les Fonds Propres Tier 1 (FPT1) incluent les fonds propres de base "
                + "(Common Equity Tier 1 - CET1) et les instruments Additional Tier 1 (AT1). "
                + "Ils représentent la première ligne de défense de la banque contre les pertes. "
                + "Utilisés dans le calcul du Ratio T1 et comme référence pour de nombreuses "
                + "limites réglementaires sur les grandes expositions et les participations.",
                "tier 1", "fpt1", "at1", "cet1", "capital", "solvabilité"));

        p.add(param("TOEXP", "Total exposition", "leverage",
                "Le Total Exposition (TOEXP) représente la somme de toutes les expositions "
                + "bilan et hors-bilan de l'établissement, calculée conformément aux règles "
                + "prudentielles du ratio de levier Bâle III. "
                + "Il constitue le dénominateur du Ratio de levier (RL = FPBT1 / TOEXP). "
                + "TOEXP intègre : les actifs au bilan, les expositions sur dérivés, "
                + "les opérations de financement de titres et les engagements hors-bilan. "
                + "Sa maîtrise est essentielle pour éviter un endettement excessif.",
                "exposition", "levier", "toexp", "hors-bilan", "bilan", "bâle iii"));

        p.add(param("ENCACTL", "Encours d'actif liquide", "liquidity_risk",
                "L'Encours d'Actif Liquide de Haute Qualité (ENCACTL) représente le stock "
                + "d'actifs liquides utilisé dans le calcul du Ratio de Liquidité à Court Terme (RLCT). "
                + "Il comprend les actifs de Niveau 1 (cash, réserves banque centrale, "
                + "titres souverains éligibles) et de Niveau 2 (titres avec décote). "
                + "Formule : RLCT = ENCACTL / SNT. "
                + "Un niveau insuffisant d'ENCACTL expose l'établissement "
                + "à un risque de stress de liquidité sur 30 jours en cas de crise de marché.",
                "actifs liquides", "hqla", "encactl", "liquidité", "lcr", "stress liquidité"));

        p.add(param("SNT", "Sorties nettes de trésorerie", "liquidity_risk",
                "Les Sorties Nettes de Trésorerie (SNT) représentent le flux net de liquidités "
                + "attendu sur un horizon de 30 jours dans un scénario de stress. "
                + "Elles constituent le dénominateur du Ratio de Liquidité Court Terme "
                + "(RLCT = ENCACTL / SNT). "
                + "Le SNT est calculé comme la différence entre les sorties de trésorerie "
                + "pondérées et les entrées de trésorerie pondérées sur 30 jours. "
                + "Une augmentation du SNT sans renforcement des actifs liquides "
                + "détériore directement le RLCT.",
                "sorties trésorerie", "snt", "lcr", "liquidité", "30 jours", "stress"));

        p.add(param("MFS", "Montant du financement stable", "liquidity_risk",
                "Le Montant du Financement Stable Disponible (MFS) représente "
                + "les ressources à long terme stables disponibles pour financer "
                + "les actifs à long terme de l'établissement. "
                + "Il constitue le numérateur du Ratio de Liquidité Long Terme "
                + "(RLLT = MFS / ERC). Le MFS inclut les fonds propres, "
                + "les dettes à plus d'un an et une partie des dépôts de détail stables. "
                + "Une insuffisance du MFS indique une dépendance excessive "
                + "aux financements courts et une vulnérabilité en cas de crise de liquidité.",
                "financement stable", "mfs", "nsfr", "rllt", "liquidité long terme", "ressources"));

        p.add(param("ERC", "Encours ressource client", "liquidity_risk",
                "L'Encours des Ressources Clientèle (ERC) représente les dépôts "
                + "et ressources collectés auprès de la clientèle. "
                + "Il constitue le dénominateur du Ratio de Liquidité Long Terme "
                + "(RLLT = MFS / ERC). C'est également un indicateur de la stabilité "
                + "du bilan bancaire et de la capacité de refinancement de l'établissement. "
                + "Une base de dépôts clientèle diversifiée et stable réduit le risque "
                + "de refinancement et améliore le RLLT.",
                "ressources clientèle", "erc", "dépôts", "rllt", "liquidité", "collecte"));

        p.add(param("ACTL", "Actifs liquides", "liquidity_risk",
                "Les Actifs Liquides (ACTL) représentent les éléments d'actif "
                + "les plus facilement convertibles en liquidités : trésorerie, "
                + "investissements temporaires, titres négociables. "
                + "Ils constituent le numérateur du Coefficient de Liquidité "
                + "(COEL = ACTL / PAEX). Un ratio COEL suffisant garantit "
                + "la capacité de l'établissement à faire face à ses engagements "
                + "à court terme sans recours à des financements d'urgence.",
                "actifs liquides", "actl", "coel", "trésorerie", "titres négociables", "liquidité"));

        p.add(param("PAEX", "Passif exigible", "liquidity_risk",
                "Le Passif Exigible (PAEX) représente l'ensemble des dettes "
                + "à court terme de l'établissement, c'est-à-dire les engagements "
                + "susceptibles d'être réclamés immédiatement ou à très court terme. "
                + "Il constitue le dénominateur du Coefficient de Liquidité "
                + "(COEL = ACTL / PAEX). Un niveau élevé de passif exigible "
                + "par rapport aux actifs liquides est un signal d'alerte "
                + "pour la gestion de la trésorerie.",
                "passif exigible", "paex", "coel", "dettes court terme", "liquidité", "trésorerie"));

        p.add(param("RNET", "Résultat net", "profitability",
                "Le Résultat Net (RNET) représente le bénéfice ou la perte "
                + "de l'exercice après impôts. Il est utilisé comme numérateur "
                + "dans le calcul de la Rentabilité Financière (ROE = RNET / FPE) "
                + "et de la Rentabilité des Actifs (ROA = RNET / TACT). "
                + "Un résultat net positif renforce les fonds propres via "
                + "la mise en réserve et améliore la capacité de l'établissement "
                + "à respecter les ratios prudentiels. Un résultat négatif "
                + "érode les fonds propres et peut déclencher des mesures correctives.",
                "résultat net", "rnet", "bénéfice", "roe", "roa", "rentabilité", "fonds propres"));

        p.add(param("TACT", "Total actif", "profitability",
                "Le Total Actif (TACT) représente la somme de tous les actifs "
                + "inscrits au bilan de l'établissement. Il constitue le dénominateur "
                + "du ratio de Rentabilité des Actifs (ROA = RNET / TACT). "
                + "Une croissance non maîtrisée du total actif sans augmentation "
                + "proportionnelle du résultat net détériore le ROA. "
                + "Le TACT est également un indicateur de taille et de diversification "
                + "de l'activité bancaire.",
                "total actif", "tact", "bilan", "roa", "rentabilité", "taille bilan"));

        p.add(param("CHEXP", "Charge d'exploitation", "profitability",
                "Les Charges d'Exploitation (CHEXP), aussi appelées frais généraux, "
                + "regroupent l'ensemble des coûts liés au fonctionnement de l'établissement : "
                + "charges de personnel, loyers, informatique, amortissements. "
                + "Elles constituent le numérateur du Coefficient d'Exploitation "
                + "(COEEXP = CHEXP / PNB). Un coefficient d'exploitation élevé (>65%) "
                + "signale une structure de coûts insuffisamment maîtrisée "
                + "et une rentabilité opérationnelle dégradée.",
                "charges exploitation", "chexp", "frais généraux", "coeexp", "efficacité", "coûts"));

        p.add(param("PNB", "Produit net bancaire", "profitability",
                "Le Produit Net Bancaire (PNB) représente la valeur ajoutée "
                + "créée par l'activité bancaire : il agrège la marge d'intérêt, "
                + "les commissions et les revenus du portefeuille de négociation. "
                + "Il constitue le dénominateur du Coefficient d'Exploitation "
                + "(COEEXP = CHEXP / PNB) et une référence clé pour la mesure "
                + "de la productivité et de l'efficacité opérationnelle. "
                + "Un PNB en progression indique une activité commerciale dynamique "
                + "et une meilleure capacité d'absorption des charges.",
                "pnb", "produit net bancaire", "marge intérêt", "commissions", "coeexp", "rentabilité"));

        // ── A.2 Risque Crédit ────────────────────────────────────────────────────
        p.add(param("ENCCLD", "Encours CDL", "credit_risk",
                "L'Encours CDL (Créances Douteuses et Litigieuses) représente le total des créances "
                + "classifiées en souffrance selon la réglementation bancaire. "
                + "Il constitue le numérateur du Taux de NPL (RNPL = ENCCLD / ENTENG). "
                + "Un niveau élevé d'ENCCLD par rapport à l'encours total signale "
                + "une dégradation de la qualité du portefeuille de crédit.",
                "cdl", "créances douteuses", "npl", "enccld", "classification", "provisionnement"));

        p.add(param("ENTENG", "Encours total des engagements", "credit_risk",
                "L'Encours Total des Engagements (ENTENG) représente le total des engagements "
                + "bruts de l'établissement, bilan et hors-bilan, envers la clientèle. "
                + "Il constitue le dénominateur commun des principaux ratios de qualité du portefeuille : "
                + "RNPL = ENCCLD / ENTENG, TCR = ENTRES / ENTENG, TECH = ENCIMP / ENTENG, "
                + "TCS = Créances souffrance / ENTENG, TCPS = CRSR / ENTENG, TCGAR = Garanties / ENTENG. "
                + "Sa croissance rapide non accompagnée d'une politique de provisionnement adéquate "
                + "peut masquer une dégradation silencieuse de la qualité du portefeuille.",
                "encours engagements", "enteng", "portefeuille", "npl", "bilan", "hors-bilan"));

        p.add(param("ENTRES", "Encours total des restructurés", "credit_risk",
                "L'Encours Total des Créances Restructurées (ENTRES) représente les crédits "
                + "ayant fait l'objet d'une modification des conditions contractuelles "
                + "(taux, durée, franchise) suite à des difficultés financières du débiteur. "
                + "Il constitue le numérateur du Taux de Créances Restructurées (TCR = ENTRES / ENTENG). "
                + "Un niveau élevé d'ENTRES peut masquer une dégradation sous-jacente du portefeuille "
                + "et doit être suivi de près par la direction des risques.",
                "restructuration", "entres", "tcr", "créances restructurées", "forborne", "risque crédit"));

        p.add(param("ENCIMP", "Encours des impayés", "credit_risk",
                "L'Encours des Impayés (ENCIMP) représente le montant total des échéances "
                + "impayées et des soldes irréguliers de plus d'un mois. "
                + "Il constitue le numérateur du Taux d'Échéances Impayées (TECH = ENCIMP / ENTENG). "
                + "Un niveau élevé d'impayés est un indicateur avancé de dégradation du portefeuille "
                + "et doit déclencher des actions de recouvrement immédiates.",
                "impayés", "encimp", "tech", "recouvrement", "créances irrégulières", "contentieux"));

        p.add(param("MTPROV", "Montant des provisions", "credit_risk",
                "Le Montant des Provisions (MTPROV) représente les dotations aux provisions "
                + "constituées par l'établissement pour couvrir les pertes probables sur créances. "
                + "Il constitue le numérateur des ratios de couverture : "
                + "TCPS = MTPROV / ENTCS et TCCPD = MTPROV / ENCCLD. "
                + "Un niveau de provisionnement à 100% garantit que les pertes probables "
                + "sont intégralement couvertes sans impact sur les fonds propres.",
                "provisions", "mtprov", "couverture", "tcps", "tccpd", "pertes crédit"));

        p.add(param("CRSR", "Créances couvertes par sûretés réelles", "credit_risk",
                "Les Créances Couvertes par Sûretés Réelles (CRSR) représentent les engagements "
                + "garantis par des actifs physiques (immobilier, nantissement). "
                + "Utilisé dans le calcul du Taux de Couverture par Sûretés (TCPS = CRSR / ENTENG). "
                + "Un niveau élevé de CRSR améliore le taux de récupération en cas de défaut "
                + "et réduit les exigences en fonds propres réglementaires.",
                "sûretés réelles", "crsr", "garanties", "tcps", "immobilier", "nantissement"));

        // ── A.3 Risque de Concentration ─────────────────────────────────────────
        p.add(param("ENT10", "Encours total des 10 plus gros expositions", "concentration_risk",
                "L'Encours des 10 Plus Grosses Expositions (ENT10) mesure la concentration "
                + "du portefeuille sur les 10 principales contreparties. "
                + "Il est rapporté à l'encours total pour calculer le ratio de concentration. "
                + "Un ratio ENT10/ENTENG > 35% signale un risque de concentration élevé : "
                + "la défaillance d'un seul débiteur majeur pourrait avoir un impact systémique "
                + "sur la qualité du portefeuille. Seuil d'appétence BFI : <= 35%.",
                "concentration", "ent10", "grandes expositions", "top 10", "risque portefeuille"));

        p.add(param("ENT20", "Encours total des 20 plus gros expositions", "concentration_risk",
                "L'Encours des 20 Plus Grosses Expositions (ENT20) complète l'analyse "
                + "de concentration du portefeuille. "
                + "Seuil d'appétence BFI : <= 50% de l'encours total. "
                + "Un niveau ENT20 > 50% indique que la moitié du portefeuille est concentrée "
                + "sur 20 contreparties seulement, amplifiant le risque systémique interne.",
                "concentration", "ent20", "top 20", "risque portefeuille", "grandes expositions"));

        p.add(param("ENT50", "Encours total des 50 plus gros expositions", "concentration_risk",
                "L'Encours des 50 Plus Grosses Expositions (ENT50) permet d'analyser "
                + "la concentration élargie du portefeuille. "
                + "Seuil d'appétence BFI : <= 65%. "
                + "Utilisé conjointement avec ENT10 et ENT20 pour cartographier "
                + "la granularité et la dispersion du portefeuille de crédit.",
                "concentration", "ent50", "top 50", "risque portefeuille", "dispersion"));

        return p;
    }

    // ─── PART B — Ratios ───────────────────────────────────────────────────────

    private List<RagDocumentEntity> buildRatioChunks() {
        List<RagDocumentEntity> r = new ArrayList<>();

        // ── B.1 Normes de Solvabilité ────────────────────────────────────────────

        // RS — Ratio de Solvabilité
        r.add(ratioDef("RS", "Ratio de Solvabilité", "Normes de solvabilité", "solvency",
                "Le Ratio de Solvabilité (RS) mesure la capacité de l'établissement bancaire "
                + "à absorber des pertes financières et à maintenir un niveau de capital réglementaire "
                + "suffisant par rapport à ses actifs pondérés par les risques (APR). "
                + "Formule : RS = Fonds Propres Effectifs (FPE) / (Risque Crédit (RCR) + Risque Marché (RM) + Risque Opérationnel (RO)). "
                + "Objectif : disposer de fonds propres suffisants pour couvrir les différents risques "
                + "et protéger l'entité contre le risque d'insolvabilité. "
                + "Norme réglementaire minimale Bâle III : 11,5%.",
                "rs", "solvabilité", "fonds propres", "actifs pondérés", "apr", "bâle iii", "capital réglementaire"));

        r.add(ratioThreshold("RS", "Ratio de Solvabilité",
                ">= 15,5%", "= 14,5% (sur 3 mois consécutifs)", ">= 13,5%", "NR = 11,5%",
                "Grille de seuils du Ratio de Solvabilité (RS) — Source : Grille d'Appétence 2024 BFI. "
                + "Zone saine (vert) : RS >= 15,5%. Marge de 4% au-dessus de la norme réglementaire. "
                + "Seuil d'alerte (orange) : RS = 14,5% sur 3 mois consécutifs. Marge de 3% au-dessus de la NR. "
                + "Seuil d'appétence (rouge) : RS >= 13,5%. Marge minimale de 2% au-dessus de la NR. "
                + "Norme réglementaire minimale (NR) : 11,5%. "
                + "Un RS inférieur à 13,5% indique une situation critique nécessitant des actions immédiates.",
                "rs", "seuil solvabilité", "appétence", "alerte", "tolérance", "norme réglementaire"));

        r.add(ratioRecommendation("RS", "Ratio de Solvabilité",
                "Actions de mitigation recommandées lorsque le Ratio de Solvabilité (RS) atteint "
                + "le seuil d'alerte (= 14,5%) : "
                + "1. Dynamiser les actions de recouvrement sur les impayés de moins de 90 jours "
                + "et mettre en place un plan d'action défini. "
                + "2. Envisager l'assainissement du bilan, y compris par recours aux sociétés de défaisance. "
                + "3. Envisager un renforcement des fonds propres via des conventions de comptes courants "
                + "d'associés bloqués, après autorisation du Régulateur. "
                + "4. Envisager une opération de titrisation de créances pour réduire le niveau des APR "
                + "et orienter les nouvelles facilités CT vers les secteurs les moins sinistrés. "
                + "5. Améliorer la couverture des engagements directs et du Hors-Bilan.",
                "rs", "mitigation", "recouvrement", "titrisation", "recapitalisation",
                "solvabilité", "actions correctives"));

        // RCET1 — Ratio CET1
        r.add(ratioDef("RCET1", "Ratio CET1", "Normes de solvabilité", "solvency",
                "Le Ratio CET1 (Common Equity Tier 1) représente la composante des fonds propres "
                + "de base de la meilleure qualité, permettant la continuité d'exploitation. "
                + "Il mesure la solidité du capital bancaire de premier ordre. "
                + "Formule : RCET1 = Fonds Propres de Base Tier 1 (FPBT1) / Actifs Pondérés par les Risques (APR). "
                + "Rôle : détermine la capacité de distribution des dividendes en N+1 par rapport "
                + "au ratio minimal de conservation des fonds propres. "
                + "Norme réglementaire minimale Bâle III : 7,5%.",
                "rcet1", "cet1", "common equity tier 1", "capital dur", "dividendes", "bâle iii", "solvabilité"));

        r.add(ratioThreshold("RCET1", "Ratio CET1",
                ">= 11,5%", "= 10,5% (sur 3 mois consécutifs)", ">= 9,5%", "NR = 7,5%",
                "Grille de seuils du Ratio CET1 (RCET1) — Source : Grille d'Appétence 2024 BFI. "
                + "Zone saine : RCET1 >= 11,5% (tolérance = NR + 4%). "
                + "Seuil d'alerte : RCET1 = 10,5% sur 3 mois consécutifs (alerte = NR + 3%). "
                + "Seuil d'appétence : RCET1 >= 9,5% (appétence = NR + 2%). "
                + "Norme réglementaire minimale : 7,5%. "
                + "Un RCET1 < 9,5% déclenche des restrictions sur la distribution de dividendes "
                + "et l'obligation d'un plan de recapitalisation immédiat.",
                "rcet1", "cet1", "seuil", "appétence", "dividendes", "recapitalisation"));

        r.add(ratioRecommendation("RCET1", "Ratio CET1",
                "Actions de mitigation recommandées lorsque le Ratio CET1 (RCET1) atteint le seuil d'alerte : "
                + "1. (Priorité 1) Procéder à la prise en compte du résultat intermédiaire "
                + "après avis favorable de la Commission Bancaire. "
                + "2. (Priorité 2) Procéder à une recapitalisation de la filiale "
                + "par incorporation des réserves disponibles. "
                + "3. Geler la distribution de dividendes jusqu'au rétablissement du ratio "
                + "au-dessus du seuil d'appétence de 9,5%.",
                "rcet1", "cet1", "recapitalisation", "dividendes", "réserves", "commission bancaire"));

        // RT1 — Ratio Tier 1
        r.add(ratioDef("RT1", "Ratio Tier 1", "Normes de solvabilité", "solvency",
                "Le Ratio T1 (RT1) mesure le rapport entre les fonds propres Tier 1 (CET1 + AT1) "
                + "et le total des actifs pondérés par le risque. "
                + "Formule : RT1 = FPBT1 / APR. "
                + "Il reflète la capacité d'une banque à absorber les pertes sans faire appel "
                + "aux créanciers ou aux autorités de régulation. "
                + "Norme réglementaire minimale Bâle III : 8,5%.",
                "rt1", "tier 1", "at1", "capital", "solvabilité", "bâle iii"));

        r.add(ratioThreshold("RT1", "Ratio Tier 1",
                ">= 12,5%", "= 11,5% (sur 3 mois consécutifs)", ">= 10,5%", "NR = 8,5%",
                "Grille de seuils RT1 : Zone saine >= 12,5% (NR+4%). "
                + "Seuil d'alerte = 11,5% sur 3 mois consécutifs (NR+3%). "
                + "Seuil d'appétence >= 10,5% (NR+2%). "
                + "Norme réglementaire Bâle III : 8,5%.",
                "rt1", "tier 1", "seuil", "appétence", "alerte"));

        r.add(ratioRecommendation("RT1", "Ratio Tier 1",
                "Actions correctives RT1 : "
                + "1. Réinvestir les bénéfices en renonçant à la distribution de dividendes. "
                + "2. Prise en compte du résultat intermédiaire après avis de la Commission Bancaire. "
                + "3. Recapitalisation par apports nouveaux des actionnaires.",
                "rt1", "recapitalisation", "dividendes", "fonds propres", "actions correctives"));

        // RL — Ratio de Levier
        r.add(ratioDef("RL", "Ratio de Levier", "Ratio de levier", "leverage",
                "Le Ratio de Levier (RL) vise à maîtriser la croissance du bilan par rapport "
                + "aux fonds propres et à limiter l'accumulation de l'effet de levier dans le secteur bancaire. "
                + "Formule : RL = Fonds Propres Tier 1 (FPBT1) / Total Expositions (TOEXP). "
                + "Ce ratio contribue à prévenir les processus d'inversion du levier "
                + "et sert de filet de sécurité complémentaire aux exigences fondées sur le risque. "
                + "Norme réglementaire minimale Bâle III : 3%.",
                "rl", "levier", "leverage", "exposition", "fpbt1", "toexp", "bâle iii"));

        r.add(ratioThreshold("RL", "Ratio de Levier",
                ">= 6%", "= 5% (sur 3 mois consécutifs)", ">= 4%", "NR = 3%",
                "Grille de seuils RL : Zone saine >= 6% (NR+3%). "
                + "Seuil d'alerte = 5% sur 3 mois consécutifs (NR+2%). "
                + "Seuil d'appétence >= 4% (NR+1%). "
                + "Norme réglementaire minimale Bâle III : 3%.",
                "rl", "levier", "seuil", "appétence", "alerte", "bilan"));

        r.add(ratioRecommendation("RL", "Ratio de Levier",
                "Actions correctives RL : Réduire l'exposition totale par : "
                + "1. Cession de titres pour remboursement des dettes. "
                + "2. Cession de créances clientèle. "
                + "3. Titrisation de créances. "
                + "Objectif : ramener le ratio RL au-dessus du seuil d'appétence de 4%.",
                "rl", "cession", "titrisation", "exposition", "levier", "bilan"));

        // ── B.2 Ratios de Liquidité ──────────────────────────────────────────────

        // RLCT — Ratio de Liquidité Court Terme
        r.add(ratioDef("RLCT", "Ratio de Liquidité Court Terme (LCR)", "Ratios de liquidité", "liquidity_risk",
                "Le Ratio de Liquidité à Court Terme (RLCT / LCR) s'assure que l'établissement "
                + "dispose d'un encours suffisant d'actifs liquides de haute qualité (HQLA) "
                + "pour surmonter une crise grave de liquidité de 30 jours. "
                + "Formule : RLCT = Encours d'Actifs Liquides de Haute Qualité (ENCACTL) / "
                + "Sorties Nettes de Trésorerie sur 30 jours (SNT). "
                + "Norme réglementaire minimale Bâle III : 100%.",
                "rlct", "lcr", "liquidité court terme", "hqla", "30 jours", "stress liquidité", "bâle iii"));

        r.add(ratioThreshold("RLCT", "Ratio de Liquidité Court Terme",
                ">= 140%", "= 130%", ">= 120%", "NR = 100%",
                "Grille de seuils RLCT : Zone saine >= 140% (NR+40%). "
                + "Seuil d'alerte = 130% (NR+30%). "
                + "Seuil d'appétence >= 120% (NR+20%). "
                + "Norme réglementaire Bâle III : 100%. "
                + "Un RLCT < 100% indique une incapacité à absorber les chocs de liquidité sur 30 jours.",
                "rlct", "lcr", "seuil liquidité", "appétence", "alerte", "crise liquidité"));

        r.add(ratioRecommendation("RLCT", "Ratio de Liquidité Court Terme",
                "Actions correctives RLCT : Dès l'atteinte du seuil d'alerte (= 130%), "
                + "la filiale doit immédiatement déclencher le Plan de Financement d'Urgence (PFU). "
                + "Actions complémentaires : "
                + "1. Augmenter le stock d'actifs liquides de haute qualité (HQLA). "
                + "2. Réduire les sorties de trésorerie nettes à 30 jours (SNT). "
                + "3. Diversifier les sources de financement pour réduire la dépendance interbancaire. "
                + "4. Alerter immédiatement la Direction des Risques et la Direction Générale.",
                "rlct", "lcr", "plan financement urgence", "pfu", "hqla", "liquidité", "actions correctives"));

        // RLLT — Ratio de Liquidité Long Terme
        r.add(ratioDef("RLLT", "Ratio de Liquidité Long Terme (NSFR)", "Ratios de liquidité", "liquidity_risk",
                "Le Ratio de Liquidité Long Terme (RLLT / NSFR) limite la transformation financière "
                + "(financements longs accordés grâce à des ressources courtes) en s'assurant que "
                + "les ressources stables sont au moins égales aux besoins de financement stables. "
                + "Formule : RLLT = Montant du Financement Stable Disponible (MFS) / "
                + "Encours Ressources Clientèle (ERC). "
                + "Norme réglementaire minimale Bâle III : 100%.",
                "rllt", "nsfr", "liquidité long terme", "financement stable", "transformation", "bâle iii"));

        r.add(ratioThreshold("RLLT", "Ratio de Liquidité Long Terme",
                ">= 140%", "= 130%", ">= 120%", "NR = 100%",
                "Grille de seuils RLLT : Zone saine >= 140% (NR+40%). "
                + "Seuil d'alerte = 130% (NR+30%). "
                + "Seuil d'appétence >= 120% (NR+20%). "
                + "Norme réglementaire Bâle III : 100%.",
                "rllt", "nsfr", "seuil liquidité", "appétence", "financement stable"));

        r.add(ratioRecommendation("RLLT", "Ratio de Liquidité Long Terme",
                "Actions correctives RLLT : Dès l'atteinte du seuil d'alerte (= 130%), "
                + "déclencher le Plan de Financement d'Urgence (PFU). "
                + "Actions spécifiques : "
                + "1. Collecter des ressources stables à long terme. "
                + "2. Réduire les actifs illiquides à long terme. "
                + "3. Diversifier les ressources stables pour réduire la dépendance court terme.",
                "rllt", "nsfr", "plan financement urgence", "liquidité long terme", "ressources stables"));

        // COEL — Coefficient de Liquidité
        r.add(ratioDef("COEL", "Coefficient de Liquidité", "Ratios de liquidité", "liquidity_risk",
                "Le Coefficient de Liquidité (COEL) mesure la proportion des actifs liquides "
                + "par rapport aux dettes à court terme (passif exigible). "
                + "Formule : COEL = Actifs Liquides (ACTL) / Passif Exigible (PAEX). "
                + "Il mesure la capacité immédiate de l'établissement à faire face "
                + "à ses obligations à court terme sans recours à des financements d'urgence. "
                + "Norme réglementaire minimale : 75%.",
                "coel", "coefficient liquidité", "actl", "paex", "liquidité", "passif exigible"));

        r.add(ratioThreshold("COEL", "Coefficient de Liquidité",
                ">= 110%", "= 100%", ">= 90%", "NR = 75%",
                "Grille de seuils COEL : Zone saine >= 110% (NR+35%). "
                + "Seuil d'alerte = 100% (NR+25%). "
                + "Seuil d'appétence >= 90% (NR+15%). "
                + "Norme réglementaire minimale : 75%.",
                "coel", "seuil", "appétence", "liquidité", "actifs liquides", "passif exigible"));

        r.add(ratioRecommendation("COEL", "Coefficient de Liquidité",
                "Actions correctives COEL : Déclencher le Plan de Financement d'Urgence. "
                + "1. Augmenter les actifs liquides (ACTL). "
                + "2. Réduire le passif exigible (PAEX). "
                + "3. Négocier des lignes de crédit confirmées avec les correspondants bancaires.",
                "coel", "plan financement urgence", "liquidité", "actifs liquides"));

        // ── B.3 Autres Indicateurs de Solidité (Rentabilité) ────────────────────

        // ROE — Rentabilité Financière
        r.add(ratioDef("ROE", "Rentabilité Financière (ROE)", "Autres indicateurs de solidité", "profitability",
                "Le Return on Equity (ROE) mesure la rentabilité des fonds propres de l'établissement. "
                + "Formule : ROE = Résultat Net (RNET) / Fonds Propres Effectifs (FPE). "
                + "Il indique le rendement généré par chaque unité de capital investi par les actionnaires. "
                + "Un ROE élevé signale une bonne efficacité dans l'utilisation des fonds propres. "
                + "Norme interne (NI) minimale BFI : 15%.",
                "roe", "rentabilité financière", "fonds propres", "résultat net", "actionnaires", "performance"));

        r.add(ratioThreshold("ROE", "Rentabilité Financière",
                ">= 20%", "= 18%", ">= 15%", "NI Min = 15%",
                "Grille de seuils ROE : Zone saine >= 20% (NI+5%). "
                + "Seuil d'alerte = 18% (NI+3%). "
                + "Seuil d'appétence >= 15%. "
                + "Norme interne minimale BFI : 15%.",
                "roe", "seuil rentabilité", "appétence", "actionnaires", "performance"));

        r.add(ratioRecommendation("ROE", "Rentabilité Financière",
                "Actions correctives ROE : "
                + "1. Vérifier si le ROE faible n'est pas lié à une sous-capitalisation (ratio FP/Total actif). "
                + "2. Identifier les points de faiblesse : commissions, marge nette d'intérêt, frais généraux. "
                + "3. Analyser les frais généraux et identifier les leviers d'optimisation. "
                + "4. Vérifier la correcte comptabilisation des amortissements d'exploitation.",
                "roe", "rentabilité", "frais généraux", "marge intérêt", "sous-capitalisation"));

        // ROA — Rentabilité des Actifs
        r.add(ratioDef("ROA", "Rentabilité des Actifs (ROA)", "Autres indicateurs de solidité", "profitability",
                "Le Return on Assets (ROA) mesure la rentabilité des actifs de l'établissement. "
                + "Formule : ROA = Résultat Net (RNET) / Total Actif (TACT). "
                + "Il indique l'efficacité avec laquelle l'établissement utilise son bilan "
                + "pour générer des bénéfices. "
                + "Norme interne minimale BFI : 1,5%.",
                "roa", "rentabilité actifs", "total actif", "résultat net", "efficacité bilan", "performance"));

        r.add(ratioThreshold("ROA", "Rentabilité des Actifs",
                ">= 3,5%", ">= 2%", ">= 1,5%", "NI Min = 1,5%",
                "Grille de seuils ROA : Zone saine >= 3,5% (NI+2%). "
                + "Seuil d'alerte >= 2% (NI+0,5%). "
                + "Seuil d'appétence >= 1,5%. "
                + "Norme interne minimale BFI : 1,5%.",
                "roa", "seuil", "appétence", "rentabilité", "total actif"));

        r.add(ratioRecommendation("ROA", "Rentabilité des Actifs",
                "Actions correctives ROA : "
                + "1. Réduire les actifs non essentiels (actifs ne contribuant pas à la rentabilité). "
                + "2. Améliorer l'efficacité des actifs existants. "
                + "3. Identifier les faiblesses de la marge nette d'intérêt et des commissions. "
                + "4. Analyser et optimiser les frais généraux.",
                "roa", "actifs", "rentabilité", "optimisation", "marge intérêt"));

        // COEEXP — Coefficient d'Exploitation
        r.add(ratioDef("COEEXP", "Coefficient d'Exploitation", "Autres indicateurs de solidité", "profitability",
                "Le Coefficient d'Exploitation (COEEXP) mesure la proportion des frais généraux "
                + "par rapport au Produit Net Bancaire. "
                + "Formule : COEEXP = Charges d'Exploitation (CHEXP) / Produit Net Bancaire (PNB). "
                + "Un ratio faible indique une meilleure efficacité opérationnelle. "
                + "Norme interne maximale BFI : 65%. "
                + "Attention : c'est un ratio inverse — plus bas est mieux.",
                "coeexp", "coefficient exploitation", "frais généraux", "pnb", "efficacité", "cost to income"));

        r.add(ratioThreshold("COEEXP", "Coefficient d'Exploitation",
                "<= 55%", "= 60%", "<= 65%", "NI Max = 65%",
                "Grille de seuils COEEXP (ratio inverse — plus bas = mieux) : "
                + "Zone saine : COEEXP <= 55% (NI-10%). "
                + "Seuil d'alerte = 60% (NI-5%). "
                + "Seuil d'appétence <= 65%. "
                + "Norme interne maximale BFI : 65%. "
                + "Un COEEXP > 65% signale une structure de coûts dégradée nécessitant un plan d'action.",
                "coeexp", "cost to income", "seuil exploitation", "efficacité", "frais généraux"));

        r.add(ratioRecommendation("COEEXP", "Coefficient d'Exploitation",
                "Actions correctives COEEXP : "
                + "Analyser les frais généraux et identifier les points d'optimisation : "
                + "1. Réduire les charges de personnel non productives. "
                + "2. Optimiser les charges informatiques et immobilières. "
                + "3. Améliorer la productivité commerciale pour augmenter le PNB (numérateur).",
                "coeexp", "frais généraux", "pnb", "optimisation", "cost to income"));

        // ── B.4 Risque Crédit ────────────────────────────────────────────────────

        // RNPL — Taux NPL
        r.add(ratioDef("RNPL", "Taux de Créances Douteuses et Litigieuses (NPL)", "Risque Crédit", "credit_risk",
                "Le Ratio de NPL (RNPL) suit le niveau de dégradation du portefeuille crédit. "
                + "Formule : RNPL = Encours CDL brutes (ENCCLD) / Encours total engagements bruts (ENTENG). "
                + "Un ratio élevé signale une détérioration de la qualité du portefeuille "
                + "nécessitant des actions correctives immédiates. "
                + "Norme interne maximale BFI : 8%.",
                "rnpl", "npl", "cdl", "créances douteuses", "qualité portefeuille", "risque crédit"));

        r.add(ratioThreshold("RNPL", "Taux NPL",
                "<= 6%", "= 7%", "<= 8%", "NI Max = 8%",
                "Grille de seuils RNPL (ratio inverse — plus bas = mieux) : "
                + "Zone saine : RNPL <= 6% (NI-2%). "
                + "Seuil d'alerte = 7% (NI-1%). "
                + "Seuil d'appétence <= 8%. "
                + "Norme interne maximale BFI : 8%.",
                "rnpl", "npl", "seuil", "cdl", "créances douteuses"));

        // TECH — Taux d'Échéances Impayées
        r.add(ratioDef("TECH", "Taux d'Échéances Impayées", "Risque Crédit", "credit_risk",
                "Le Taux d'Échéances Impayées (TECH) maintient la proportion des impayés "
                + "et comptes irréguliers à un niveau raisonnable. "
                + "Formule : TECH = Montant total des impayés et soldes irréguliers > 1 mois / "
                + "Encours total des engagements bruts (ENTENG). "
                + "Seuil d'appétence BFI : <= 2%. "
                + "Un TECH élevé est un indicateur avancé de détérioration du portefeuille.",
                "tech", "impayés", "soldes irréguliers", "créances", "portefeuille", "risque crédit"));

        r.add(ratioThreshold("TECH", "Taux d'Échéances Impayées",
                "<= 1%", "= 1,5%", "<= 2%", "NI Max = 2%",
                "Grille de seuils TECH : Zone saine <= 1% (NI-1%). "
                + "Seuil d'alerte = 1,5% (NI-0,5%). "
                + "Seuil d'appétence <= 2%. "
                + "Norme interne maximale BFI : 2%.",
                "tech", "impayés", "seuil", "alerte", "portefeuille"));

        // TCR — Taux des Créances Restructurées
        r.add(ratioDef("TCR", "Taux des Créances Restructurées", "Risque Crédit", "credit_risk",
                "Le Taux des Créances Restructurées (TCR) assure un suivi rapproché des créances "
                + "ayant fait l'objet d'une modification des conditions contractuelles. "
                + "Formule : TCR = Encours créances restructurées brutes / Encours total engagements bruts. "
                + "Seuil d'appétence BFI : <= 1,5%. "
                + "Un TCR élevé peut masquer une dégradation sous-jacente du portefeuille.",
                "tcr", "restructuration", "forborne", "créances restructurées", "risque crédit"));

        r.add(ratioThreshold("TCR", "Taux des Créances Restructurées",
                "<= 0,5%", "= 1%", "<= 1,5%", "NI Max = 1,5%",
                "Grille de seuils TCR : Zone saine <= 0,5% (NI-1%). "
                + "Seuil d'alerte = 1% (NI-0,5%). "
                + "Seuil d'appétence <= 1,5%. "
                + "Norme interne maximale BFI : 1,5%.",
                "tcr", "restructuration", "seuil", "forborne"));

        // TCS — Taux de Créances en Souffrance
        r.add(ratioDef("TCS", "Taux de Créances en Souffrance", "Risque Crédit", "credit_risk",
                "Le Taux de Créances en Souffrance (TCS) maintient la proportion des créances "
                + "en souffrance dans le portefeuille des crédits directs à un niveau raisonnable. "
                + "Formule : TCS = Encours créances en souffrance directs / "
                + "Encours total engagements directs bruts. "
                + "Seuil d'appétence BFI : <= 8%.",
                "tcs", "créances souffrance", "souffrance", "portefeuille", "risque crédit"));

        r.add(ratioThreshold("TCS", "Taux de Créances en Souffrance",
                "<= 6%", "= 7%", "<= 8%", "NI Max = 8%",
                "Grille de seuils TCS : Zone saine <= 6% (NI-2%). "
                + "Seuil d'alerte = 7% (NI-1%). "
                + "Seuil d'appétence <= 8%. "
                + "Norme interne maximale BFI : 8%.",
                "tcs", "créances souffrance", "seuil", "portefeuille"));

        // TCPS — Taux de Couverture par Sûretés
        r.add(ratioDef("TCPS", "Taux de Couverture par Sûretés", "Risque Crédit", "credit_risk",
                "Le Taux de Couverture par Sûretés (TCPS) s'assure de la couverture minimale "
                + "des crédits octroyés par des sûretés. "
                + "Formule : TCPS = Encours crédits couverts par sûretés réelles / "
                + "Encours total crédits. "
                + "Seuil d'appétence BFI : >= 80%. "
                + "Un niveau élevé de TCPS améliore le taux de récupération en cas de défaut.",
                "tcps", "sûretés", "garanties", "couverture", "risque crédit"));

        r.add(ratioThreshold("TCPS", "Taux de Couverture par Sûretés",
                ">= 90%", "= 85%", ">= 80%", "NI Min = 80%",
                "Grille de seuils TCPS : Zone saine >= 90% (NI+10%). "
                + "Seuil d'alerte = 85% (NI+5%). "
                + "Seuil d'appétence >= 80%. "
                + "Norme interne minimale BFI : 80%.",
                "tcps", "sûretés", "couverture", "garanties", "seuil"));

        // TCGAR — Taux de Couverture par Garanties Éligibles
        r.add(ratioDef("TCGAR", "Taux de Couverture par Garanties Éligibles", "Risque Crédit", "credit_risk",
                "Le Taux de Couverture par Garanties Éligibles et/ou Hypothèque (TCGAR) s'assure "
                + "de la prise de garanties éligibles pour l'optimisation des fonds propres réglementaires (TARC). "
                + "Formule : TCGAR = Encours crédits couverts par garanties éligibles et hypothèques / "
                + "Encours total engagements. "
                + "Seuil d'appétence BFI : >= 45%.",
                "tcgar", "garanties éligibles", "hypothèque", "tarc", "couverture", "risque crédit"));

        r.add(ratioThreshold("TCGAR", "Taux de Couverture par Garanties Éligibles",
                ">= 55%", "= 50%", ">= 45%", "NI Min = 45%",
                "Grille de seuils TCGAR : Zone saine >= 55% (NI+10%). "
                + "Seuil d'alerte = 50% (NI+5%). "
                + "Seuil d'appétence >= 45%. "
                + "Norme interne minimale BFI : 45%.",
                "tcgar", "garanties éligibles", "hypothèque", "seuil", "tarc"));

        // ── B.5 Limites sur Participations ───────────────────────────────────────

        // LGPARTCOM
        r.add(ratioDef("LGPARTCOM", "Limite Globale sur Participations Commerciales",
                "Autres normes prudentielles", "solvency",
                "La Limite Globale sur les Participations dans les Entités Commerciales (LGPARTCOM) "
                + "évite à la banque de se livrer à des activités industrielles, commerciales, "
                + "agricoles ou de services pour son propre compte. "
                + "Formule : LGPARTCOM = Montant total participations directes/indirectes entités commerciales / "
                + "Fonds Propres Tier 1 (FPT1). "
                + "Norme réglementaire maximale : 60% des FPE. "
                + "Seuil d'appétence BFI : <= 40%.",
                "lgpartcom", "participations", "entités commerciales", "limite globale", "fpt1",
                "normes prudentielles"));

        r.add(ratioThreshold("LGPARTCOM", "Limite Globale sur Participations Commerciales",
                "<= 20%", "= 30%", "<= 40%", "NR Max = 60%",
                "Grille de seuils LGPARTCOM (ratio inverse) : Zone saine <= 20% (NR-40%). "
                + "Seuil d'alerte = 30% (NR-30%). "
                + "Seuil d'appétence <= 40% (NR-20%). "
                + "Norme réglementaire maximale : 60%.",
                "lgpartcom", "participations commerciales", "seuil", "appétence"));

        r.add(ratioRecommendation("LGPARTCOM", "Limite Globale sur Participations Commerciales",
                "Actions correctives LGPARTCOM : "
                + "La filiale doit soit renoncer soit réduire ses participations "
                + "dans les entités commerciales pour revenir sous le seuil d'appétence de 40%. "
                + "Options : cession partielle, transfert à une holding, ou réduction progressive.",
                "lgpartcom", "participations", "cession", "réduction", "fonds propres"));

        return r;
    }

    // ─── PART C — Cross-domain knowledge ───────────────────────────────────────

    private List<RagDocumentEntity> buildCrossDomainChunks() {
        return List.of(

            RagDocumentEntity.builder()
                .id(UUID.randomUUID())
                .title("Cadre réglementaire Bâle III — Vue d'ensemble BFI")
                .content("Le cadre réglementaire Bâle III définit les exigences minimales en fonds propres "
                        + "pour les banques afin de renforcer la résilience du secteur bancaire. "
                        + "Exigences BFI (supérieures aux minimums Bâle III pour intégrer une marge de sécurité) : "
                        + "- Ratio de Solvabilité (RS) : appétence >= 13,5%, norme réglementaire 11,5%. "
                        + "- Ratio CET1 (RCET1) : appétence >= 9,5%, norme réglementaire 7,5%. "
                        + "- Ratio Tier 1 (RT1) : appétence >= 10,5%, norme réglementaire 8,5%. "
                        + "- Ratio de Levier (RL) : appétence >= 4%, norme réglementaire 3%. "
                        + "- Ratio LCR / RLCT : appétence >= 120%, norme réglementaire 100%. "
                        + "- Ratio NSFR / RLLT : appétence >= 120%, norme réglementaire 100%. "
                        + "Les seuils internes BFI ajoutent une marge de 2% à 5% au-dessus des normes "
                        + "réglementaires minimales pour anticiper les situations de stress.")
                .documentType("REGULATION")
                .category("Réglementation bancaire")
                .regulation("Basel_III")
                .domain("solvency")
                .source(SOURCE)
                .keywords(new String[]{"bâle iii", "réglementation", "fonds propres", "lcr", "nsfr",
                        "solvabilité", "capital réglementaire", "appétence"})
                .language(LANGUAGE)
                .createdAt(Instant.now())
                .build(),

            RagDocumentEntity.builder()
                .id(UUID.randomUUID())
                .title("Différence entre ROE et ROA — Comparaison des indicateurs de rentabilité")
                .content("ROE (Return on Equity) et ROA (Return on Assets) sont deux indicateurs "
                        + "complémentaires de rentabilité bancaire. "
                        + "ROE = Résultat Net (RNET) / Fonds Propres Effectifs (FPE) : "
                        + "mesure le rendement du capital apporté par les actionnaires. "
                        + "Norme interne BFI minimale : ROE >= 15%. "
                        + "ROA = Résultat Net (RNET) / Total Actif (TACT) : "
                        + "mesure l'efficacité globale d'utilisation du bilan. "
                        + "Norme interne BFI minimale : ROA >= 1,5%. "
                        + "Relation clé : ROE = ROA × (Total Actif / Fonds Propres) = ROA × Levier financier. "
                        + "Un ROE élevé combiné à un ROA faible signale un levier financier excessif, "
                        + "ce qui doit déclencher une analyse de la structure du bilan et du ratio RL.")
                .documentType("RATIO_RELATIONSHIP")
                .category("Analyse de rentabilité")
                .domain("profitability")
                .source(SOURCE)
                .keywords(new String[]{"roe", "roa", "rentabilité", "levier", "fonds propres",
                        "total actif", "comparaison", "performance"})
                .language(LANGUAGE)
                .createdAt(Instant.now())
                .build(),

            RagDocumentEntity.builder()
                .id(UUID.randomUUID())
                .title("Interprétation de la dégradation de la solvabilité bancaire")
                .content("La dégradation des ratios de solvabilité (RS, RCET1, RT1) peut résulter "
                        + "de plusieurs causes : "
                        + "1. Augmentation des actifs pondérés par les risques (RCR, RM, RO) "
                        + "due à une croissance rapide du portefeuille de crédit sans sélection adéquate. "
                        + "2. Érosion des fonds propres via des pertes nettes (RNET négatif). "
                        + "3. Distribution excessive de dividendes réduisant les réserves. "
                        + "4. Insuffisance du provisionnement sur les créances en souffrance (ENCCLD élevé). "
                        + "5. Exposition accrue aux risques de marché (RM) non couverts. "
                        + "Actions immédiates recommandées : renforcer les provisions (MTPROV), "
                        + "geler les dividendes, et envisager une recapitalisation (FPE, FPBT1).")
                .documentType("RISK_INTERPRETATION")
                .category("Risque prudentiel")
                .domain("solvency")
                .source(SOURCE)
                .keywords(new String[]{"solvabilité", "dégradation", "apr", "fonds propres", "pertes",
                        "dividendes", "provisionnement", "rcet1", "rs"})
                .language(LANGUAGE)
                .createdAt(Instant.now())
                .build(),

            RagDocumentEntity.builder()
                .id(UUID.randomUUID())
                .title("Interprétation du stress de liquidité — RLCT et COEL en zone d'alerte")
                .content("Un stress de liquidité se manifeste par la détérioration simultanée "
                        + "de plusieurs ratios : RLCT, RLLT et/ou COEL. "
                        + "Causes principales : "
                        + "1. Augmentation rapide des sorties nettes de trésorerie (SNT) "
                        + "sans compensation par les actifs liquides (ENCACTL). "
                        + "2. Réduction du stock de titres souverains éligibles (HQLA de Niveau 1). "
                        + "3. Concentration excessive des dépôts à vue ou à court terme (PAEX élevé). "
                        + "4. Dépendance au financement interbancaire à court terme. "
                        + "5. Décollecte accélérée de dépôts clientèle (baisse ERC). "
                        + "Protocole d'urgence : activation du Plan de Financement d'Urgence (PFU), "
                        + "notification immédiate à la Direction Générale et au Régulateur "
                        + "si RLCT < 100% ou COEL < 75%.")
                .documentType("RISK_INTERPRETATION")
                .category("Risque de liquidité")
                .domain("liquidity_risk")
                .source(SOURCE)
                .keywords(new String[]{"liquidité", "stress", "rlct", "coel", "hqla",
                        "plan financement urgence", "pfu", "trésorerie", "snc", "encactl"})
                .language(LANGUAGE)
                .createdAt(Instant.now())
                .build()
        );
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ENTITY BUILDER HELPERS
    // ═══════════════════════════════════════════════════════════════════════════

    private RagDocumentEntity param(String code, String label, String domain,
                                    String content, String... keywords) {
        return RagDocumentEntity.builder()
                .id(UUID.randomUUID())
                .title("Paramètre " + code + " — " + label)
                .content(content)
                .documentType("PARAMETER_DEFINITION")
                .category("Paramètre bancaire")
                .parameterCode(code)
                .ratioCode(null)
                .ratioFamily(null)
                .domain(domain)
                .regulation("interne")
                .source(SOURCE)
                .keywords(keywords)
                .language(LANGUAGE)
                .createdAt(Instant.now())
                .build();
    }

    private RagDocumentEntity ratioDef(String code, String label, String family,
                                       String domain, String content, String... keywords) {
        return RagDocumentEntity.builder()
                .id(UUID.randomUUID())
                .title("Définition " + code + " — " + label)
                .content(content)
                .documentType("RATIO_DEFINITION")
                .category("Définition du ratio")
                .ratioCode(code)
                .parameterCode(null)
                .ratioFamily(family)
                .domain(domain)
                .regulation("Basel_III")
                .source(SOURCE)
                .keywords(keywords)
                .language(LANGUAGE)
                .createdAt(Instant.now())
                .build();
    }

    private RagDocumentEntity ratioThreshold(String code, String label,
                                             String toleranceStr, String alerteStr,
                                             String appetenceStr, String regulatoryNorm,
                                             String content, String... keywords) {
        String title = "Seuils " + code + " — " + label
                + " | Tolérance: " + toleranceStr
                + " | Alerte: " + alerteStr
                + " | Appétence: " + appetenceStr
                + " | " + regulatoryNorm;
        return RagDocumentEntity.builder()
                .id(UUID.randomUUID())
                .title(title)
                .content(content)
                .documentType("THRESHOLD_INTERPRETATION")
                .category("Grille d'appétence")
                .ratioCode(code)
                .parameterCode(null)
                .ratioFamily(label)
                .domain(null)
                .regulation("Basel_III")
                .source(SOURCE)
                .keywords(keywords)
                .language(LANGUAGE)
                .createdAt(Instant.now())
                .build();
    }

    private RagDocumentEntity ratioRecommendation(String code, String label,
                                                  String content, String... keywords) {
        return RagDocumentEntity.builder()
                .id(UUID.randomUUID())
                .title("Actions correctives " + code + " — " + label)
                .content(content)
                .documentType("RECOMMENDATION")
                .category("Plan d'action correctif")
                .ratioCode(code)
                .parameterCode(null)
                .ratioFamily(label)
                .domain(null)
                .regulation("interne")
                .source(SOURCE)
                .keywords(keywords)
                .language(LANGUAGE)
                .createdAt(Instant.now())
                .build();
    }
}
