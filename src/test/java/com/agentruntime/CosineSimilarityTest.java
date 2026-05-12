package com.agentruntime;

import com.agentruntime.tooling.rag.CosineSimilarity;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/** Tests for Drift-01: real TF cosine similarity (not character overlap). */
class CosineSimilarityTest {

    private final CosineSimilarity sim = new CosineSimilarity();

    @Test
    void identicalTexts_scoreOne() {
        assertEquals(1.0, sim.similarity("hello world", "hello world"), 1e-9);
    }

    @Test
    void disjointTexts_scoreZero() {
        double score = sim.similarity("alpha beta gamma", "delta epsilon zeta");
        assertEquals(0.0, score, 1e-9);
    }

    @Test
    void partialOverlap_scoreBetweenZeroAndOne() {
        double score = sim.similarity("the quick brown fox", "the slow brown dog");
        assertTrue(score > 0.0 && score < 1.0, "Partial overlap should score between 0 and 1, got " + score);
    }

    @Test
    void similarity_isSymmetric() {
        double ab = sim.similarity("hello world foo", "hello bar baz");
        double ba = sim.similarity("hello bar baz", "hello world foo");
        assertEquals(ab, ba, 1e-9);
    }

    @Test
    void nullInput_returnsZero() {
        assertEquals(0.0, sim.similarity(null, "text"), 1e-9);
        assertEquals(0.0, sim.similarity("text", null), 1e-9);
        assertEquals(0.0, sim.similarity(null, null), 1e-9);
    }

    @Test
    void emptyInput_returnsZero() {
        assertEquals(0.0, sim.similarity("", "text"), 1e-9);
        assertEquals(0.0, sim.similarity("text", ""), 1e-9);
    }

    @Test
    void highlyRelatedTexts_scoreAboveThreshold() {
        // These share most tokens so should score > 0.85
        double score = sim.similarity("the cat sat on the mat", "the cat sat on the mat today");
        assertTrue(score > 0.85, "Highly related texts should score > 0.85, got " + score);
    }

    @Test
    void unrelatedTexts_scoreBelowThreshold() {
        double score = sim.similarity("quantum physics equations", "bakery recipes chocolate cake");
        assertTrue(score < 0.5, "Unrelated texts should score below 0.5, got " + score);
    }

    @Test
    void notCharacterOverlap_catchesFormerBug() {
        // "cat" and "catch" share all chars of "cat" — old heuristic would score ~1.0
        // real cosine similarity should be much lower (different token sets)
        double score = sim.similarity("cat", "catch");
        // TF-cosine: "cat" vector = {cat:1}, "catch" vector = {catch:1} — dot=0, score=0
        assertEquals(0.0, score, 1e-9, "TF-cosine must score 0 for 'cat' vs 'catch' (different tokens)");
    }
}
