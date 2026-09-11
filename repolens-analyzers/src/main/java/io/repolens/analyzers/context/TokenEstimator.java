package io.repolens.analyzers.context;

/**
 * Pluggable token estimator. v1.6 ships a deterministic character approximation.
 */
public interface TokenEstimator {

    /**
     * Estimate tokens for the given text. Must be deterministic and non-negative.
     */
    int estimate(String text);

    /**
     * Approx heuristic: ~4 characters per token (common rough estimate).
     * Not an exact LLM tokenizer count.
     */
    final class ApproxCharsPerToken implements TokenEstimator {
        public static final ApproxCharsPerToken INSTANCE = new ApproxCharsPerToken();
        private static final int CHARS_PER_TOKEN = 4;

        private ApproxCharsPerToken() {
        }

        @Override
        public int estimate(String text) {
            if (text == null || text.isEmpty()) {
                return 0;
            }
            return Math.max(1, (text.length() + CHARS_PER_TOKEN - 1) / CHARS_PER_TOKEN);
        }
    }
}
