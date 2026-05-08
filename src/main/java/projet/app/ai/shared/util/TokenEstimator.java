package projet.app.ai.shared.util;

/**
 * Cheap, dependency-free token estimator used by guards that need a rough idea
 * of prompt size before issuing an LLM call. Approximates the OpenAI BPE rule
 * of ≈ 4 characters per token for Latin-script text, with a small bias upward
 * so we err on the side of trimming too aggressively.
 *
 * <p>Use this for budget checks only — for billing or precise truncation use
 * a real BPE tokenizer (jtokkit, tiktoken-java, etc.).
 */
public final class TokenEstimator {

    private static final double CHARS_PER_TOKEN = 3.7;

    private TokenEstimator() {}

    public static int estimate(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return (int) Math.ceil(text.length() / CHARS_PER_TOKEN);
    }

    /** Returns true iff the estimated token count of {@code text} fits within {@code maxTokens}. */
    public static boolean fits(String text, int maxTokens) {
        return estimate(text) <= maxTokens;
    }
}
