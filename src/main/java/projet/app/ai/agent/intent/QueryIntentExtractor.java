package projet.app.ai.agent.intent;

import org.springframework.stereotype.Component;
import projet.app.ai.shared.util.DateExtractor;
import projet.app.ai.shared.util.Tokenizer;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Pure rule-based extractor: pulls ratio codes, parameter codes, dates, financial
 * domain hint and a coarse intent classification from a user query.
 *
 * <p>Deliberately deterministic — no LLM round-trip — because we want consistent
 * RAG metadata filtering and predictable behaviour even when the model is offline.
 */
@Component
public class QueryIntentExtractor {

    private static final Set<String> RATIO_CODES = Set.of(
            "RS", "RCET1", "RT1", "RL", "RLCT", "RLLT", "COEL",
            "ROE", "ROA", "COEEXP", "RNPL", "TECH", "TCR", "TCS",
            "TCPS", "TCGAR", "LGPARTCOM"
    );

    private static final Set<String> PARAMETER_CODES = Set.of(
            "FPE", "RCR", "RM", "RO", "FPT1", "TOEXP", "ENCACTL",
            "SNT", "ACTL", "PAEX", "RNET", "TACT", "PNB", "ENCTENG",
            "FPBT1", "FPBT2", "ENTENG", "ENTRES"
    );

    /** Domain keywords: French / Arabic-transliterated / English variants → canonical domain. */
    private static final Map<String, String> DOMAIN_KEYWORDS = Map.ofEntries(
            Map.entry("solvabilité", "solvency"),
            Map.entry("solvabilite", "solvency"),
            Map.entry("solvency", "solvency"),
            Map.entry("liquidité", "liquidity"),
            Map.entry("liquidite", "liquidity"),
            Map.entry("liquidity", "liquidity"),
            Map.entry("rentabilité", "profitability"),
            Map.entry("rentabilite", "profitability"),
            Map.entry("profitability", "profitability"),
            Map.entry("levier", "leverage"),
            Map.entry("leverage", "leverage"),
            Map.entry("risque crédit", "credit_risk"),
            Map.entry("risque credit", "credit_risk"),
            Map.entry("credit risk", "credit_risk"),
            Map.entry("risque opérationnel", "op_risk"),
            Map.entry("risque operationnel", "op_risk"),
            Map.entry("operational risk", "op_risk"),
            Map.entry("risque marché", "market_risk"),
            Map.entry("risque marche", "market_risk"),
            Map.entry("market risk", "market_risk")
    );

    public QueryIntent extract(String query) {
        if (query == null) {
            query = "";
        }
        String upper = query.toUpperCase(Locale.ROOT);
        String lower = query.toLowerCase(Locale.ROOT);

        List<String> ratioCodes = RATIO_CODES.stream()
                .filter(code -> containsAsToken(upper, code))
                .toList();

        List<String> paramCodes = PARAMETER_CODES.stream()
                .filter(code -> containsAsToken(upper, code))
                .toList();

        List<String> dates = DateExtractor.extractDates(query);

        String domain = DOMAIN_KEYWORDS.entrySet().stream()
                .filter(e -> lower.contains(e.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);

        IntentType intentType = classifyIntent(lower, ratioCodes, paramCodes);

        List<String> keywords = Tokenizer.normalize(query);

        return new QueryIntent(query, ratioCodes, paramCodes, dates, domain, intentType, keywords);
    }

    private IntentType classifyIntent(String lower, List<String> ratios, List<String> params) {
        if (lower.contains("stress") || lower.contains("simulation")
                || lower.contains("simuler") || lower.contains("what if")
                || lower.contains("si on") || lower.contains("scenario") || lower.contains("scénario")) {
            return IntentType.STRESS_TEST;
        }
        if (lower.contains("évolution") || lower.contains("evolution")
                || lower.contains("trend") || lower.contains("tendance")
                || lower.contains("comparer") || lower.contains("compare")
                || lower.contains("over time") || lower.contains("au fil")) {
            return IntentType.TREND_ANALYSIS;
        }
        if (lower.contains("seuil") || lower.contains("threshold")
                || lower.contains("appétence") || lower.contains("appetence")
                || lower.contains("alerte") || lower.contains("tolérance")
                || lower.contains("breach") || lower.contains("breached")) {
            return IntentType.THRESHOLD_LOOKUP;
        }
        if (lower.contains("pourquoi") || lower.contains("why")
                || lower.contains("expliquer") || lower.contains("explain")
                || lower.contains("interpréter") || lower.contains("interpret")) {
            return IntentType.INTERPRETATION;
        }
        if (lower.contains("qu'est") || lower.contains("définition")
                || lower.contains("definition") || lower.contains("what is")
                || lower.contains("c'est quoi")) {
            return IntentType.DEFINITION;
        }
        if (lower.contains("dashboard") || lower.contains("situation")
                || lower.contains("état") || lower.contains("etat")
                || lower.contains("overview") || lower.contains("vue d'ensemble")) {
            return IntentType.DASHBOARD_OVERVIEW;
        }
        if (!ratios.isEmpty() || !params.isEmpty()) {
            return IntentType.RATIO_VALUE;
        }
        return IntentType.GENERAL;
    }

    /**
     * Match a CODE only when surrounded by non-letter/digit boundaries, so that
     * {@code "RS"} doesn't false-positive on "ANALYSIS".
     */
    private static boolean containsAsToken(String upperText, String code) {
        int idx = upperText.indexOf(code);
        while (idx >= 0) {
            boolean leftOk = idx == 0
                    || !Character.isLetterOrDigit(upperText.charAt(idx - 1));
            int end = idx + code.length();
            boolean rightOk = end >= upperText.length()
                    || !Character.isLetterOrDigit(upperText.charAt(end));
            if (leftOk && rightOk) {
                return true;
            }
            idx = upperText.indexOf(code, idx + 1);
        }
        return false;
    }
}
