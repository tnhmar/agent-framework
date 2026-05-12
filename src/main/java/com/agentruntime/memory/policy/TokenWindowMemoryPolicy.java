package com.agentruntime.memory.policy;

import java.util.*;

/**
 * Token-budget-aware working memory window policy.
 *
 * Ported from agent-framework memory.TokenWindowMemoryPolicy; namespace-adapted.
 * Satisfies Vol.1 Ch.3.8 "token budget management as a first-class concern."
 *
 * Selects the most-recent entries from working memory that fit within
 * the configured token budget. This prevents context overflow on long
 * conversations while preserving recency-ordered relevance.
 *
 * Integration point: inject into DefaultPromptingModule so that
 * PromptPackage assembly is budget-aware.
 */
public final class TokenWindowMemoryPolicy {

    public static final int DEFAULT_MAX_TOKENS = 2000;

    private final ApproximateTokenizer tokenizer;
    private final int maxTokens;

    public TokenWindowMemoryPolicy(ApproximateTokenizer tokenizer, int maxTokens) {
        this.tokenizer = Objects.requireNonNull(tokenizer, "tokenizer must not be null");
        this.maxTokens = maxTokens;
    }

    public TokenWindowMemoryPolicy() {
        this(ApproximateTokenizer.get(), DEFAULT_MAX_TOKENS);
    }

    public TokenWindowMemoryPolicy(int maxTokens) {
        this(ApproximateTokenizer.get(), maxTokens);
    }

    /**
     * Select the most-recent entries from {@code memoryEntries} (ordered oldest → newest)
     * that fit within the token budget, preserving order.
     *
     * @param memoryEntries all entries, oldest first
     * @return subset that fits in budget, oldest-first, never null
     */
    public List<String> selectWindow(List<String> memoryEntries) {
        if (memoryEntries == null || memoryEntries.isEmpty()) return List.of();

        Deque<String> window = new ArrayDeque<>();
        int used = 0;

        // Walk newest → oldest, greedily adding until budget exhausted
        for (int i = memoryEntries.size() - 1; i >= 0; i--) {
            String entry = memoryEntries.get(i);
            int tokens = tokenizer.countTokens(entry);
            if (used + tokens > maxTokens) break;
            window.addFirst(entry);
            used += tokens;
        }

        return List.copyOf(window);
    }

    /**
     * Produce a single context string from the window, joined by newlines.
     */
    public String buildContextString(List<String> memoryEntries) {
        return String.join("\n", selectWindow(memoryEntries));
    }

    public int maxTokens() { return maxTokens; }
}
