package projet.app.ai.agent.intent;

import java.util.List;

/**
 * Deterministic, code-extracted view of a user query. Drives RAG metadata filtering
 * and (optionally) tool selection hints. Built without any LLM call.
 */
public record QueryIntent(
        String rawText,
        List<String> ratioCodes,
        List<String> parameterCodes,
        List<String> dates,
        String domain,
        IntentType intentType,
        List<String> keywords
) {
    public boolean hasCodes() {
        return !ratioCodes.isEmpty() || !parameterCodes.isEmpty();
    }
}
