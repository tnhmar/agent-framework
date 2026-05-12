package com.agentruntime.memory.policy;

/**
 * Approximate tokenizer.
 * Ported from agent-framework memory.Tokenizer; namespace-adapted.
 *
 * Uses a simple whitespace + punctuation split as an approximation.
 * Replace with a real BPE tokenizer for production accuracy.
 */
public final class ApproximateTokenizer {

    private static final ApproximateTokenizer INSTANCE = new ApproximateTokenizer();

    private ApproximateTokenizer() {}

    public static ApproximateTokenizer get() { return INSTANCE; }

    /**
     * Estimate the token count for a string.
     * Rule of thumb: ~4 characters per token (OpenAI BPE approximation).
     */
    public int countTokens(String text) {
        if (text == null || text.isBlank()) return 0;
        return Math.max(1, text.length() / 4);
    }
}
