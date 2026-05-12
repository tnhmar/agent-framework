package com.agentruntime;

import com.agentruntime.memory.policy.TokenWindowMemoryPolicy;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for TokenWindowMemoryPolicy (ported from agent-framework).
 */
class TokenWindowMemoryPolicyTest {

    @Test
    void emptyInputReturnsEmpty() {
        TokenWindowMemoryPolicy policy = new TokenWindowMemoryPolicy();
        assertTrue(policy.selectWindow(List.of()).isEmpty());
        assertTrue(policy.selectWindow(null).isEmpty());
    }

    @Test
    void allEntriesFitWithinBudget() {
        TokenWindowMemoryPolicy policy = new TokenWindowMemoryPolicy(10_000);
        List<String> entries = List.of("entry one", "entry two", "entry three");
        List<String> result = policy.selectWindow(entries);
        assertEquals(3, result.size());
    }

    @Test
    void oldestEntriesAreDroppedWhenBudgetExceeded() {
        // Budget of 6 tokens — fits 1 entry (~5 tokens each), drops 3 oldest.
        // Each entry is ~5 tokens (22 chars / 4 = 5). Budget 6 fits exactly 1.
        TokenWindowMemoryPolicy policy = new TokenWindowMemoryPolicy(6);
        List<String> entries = List.of(
            "first-long-entry-one",
            "second-long-entry-two",
            "third-long-entry-three",
            "fourth-long-entry-four"
        );
        List<String> result = policy.selectWindow(entries);

        // Window is non-empty (at least the newest entry fits)
        assertFalse(result.isEmpty());
        // Not all entries fit — oldest are dropped
        assertTrue(result.size() < entries.size(), "Budget should force some entries to be dropped");
        // The most-recent entry must always be present
        assertEquals("fourth-long-entry-four", result.get(result.size() - 1));
    }

    @Test
    void windowPreservesChronologicalOrder() {
        TokenWindowMemoryPolicy policy = new TokenWindowMemoryPolicy(10_000);
        List<String> entries = List.of("first", "second", "third");
        List<String> result = policy.selectWindow(entries);
        assertEquals(List.of("first", "second", "third"), result);
    }

    @Test
    void buildContextStringJoinsWithNewlines() {
        TokenWindowMemoryPolicy policy = new TokenWindowMemoryPolicy(10_000);
        String result = policy.buildContextString(List.of("a", "b", "c"));
        assertEquals("a\nb\nc", result);
    }
}
