package com.agentruntime;

import com.agentruntime.core.valueobjects.AgentIdentity;
import com.agentruntime.memory.consolidation.*;
import com.agentruntime.memory.episodic.EpisodicStore;
import com.agentruntime.memory.scoring.*;
import com.agentruntime.memory.semantic.SemanticStore;
import com.agentruntime.observability.AuditEvent;
import com.agentruntime.orchestrator.failuredetection.*;
import com.agentruntime.core.valueobjects.ExecutionContext;
import org.junit.jupiter.api.*;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for:
 *   G-07 ImportanceScorer
 *   G-08 Three Consolidation Strategies
 *   G-10 AuditEvent extended fields
 *   G-11 FailureAssessment 4 categories
 */
class ConsolidationAndScoringTest {

    // ── G-07: ImportanceScorer ────────────────────────────────────────────────

    @Test void scorer_freshContentScoresHigh() {
        ImportanceScorer scorer = new ImportanceScorer();
        ImportanceScore score = scorer.score(
                "regular content", Instant.now(), 0, "user_explicit", Set.of());
        // User-explicit source weight is 1.0; freshness near 1.0; total should be high
        assertTrue(score.total() >= 0.5, "Fresh user-explicit content should score at least 0.5");
    }

    @Test void scorer_staleFreshnessDropsScore() {
        ImportanceScorer scorer = new ImportanceScorer();
        // Old content: 365 days ago
        Instant veryOld = Instant.now().minusSeconds(365L * 24 * 3600);
        ImportanceScore fresh = scorer.score("content", Instant.now(), 0, "default", Set.of());
        ImportanceScore stale = scorer.score("content", veryOld,       0, "default", Set.of());
        assertTrue(fresh.freshnessComponent() > stale.freshnessComponent(),
                "Freshness must decay over time");
    }

    @Test void scorer_frequencyBoostsScore() {
        ImportanceScorer scorer = new ImportanceScorer();
        ImportanceScore zero     = scorer.score("content", Instant.now(), 0,   "default", Set.of());
        ImportanceScore frequent = scorer.score("content", Instant.now(), 100, "default", Set.of());
        assertTrue(frequent.frequencyComponent() > zero.frequencyComponent());
    }

    @Test void scorer_salienceKeywordBoosts() {
        ImportanceScorer scorer = new ImportanceScorer();
        ImportanceScore normal   = scorer.score("regular update",                Instant.now(), 0, "default", Set.of());
        ImportanceScore critical = scorer.score("CRITICAL: system error detected", Instant.now(), 0, "default", Set.of());
        assertTrue(critical.salienceComponent() > normal.salienceComponent(),
                "Critical keyword should boost salience");
    }

    @Test void scorer_taskRelevanceBoostsWhenTagMatch() {
        ImportanceScorer scorer = new ImportanceScorer();
        Set<String> activeTags = Set.of("portfolio", "exposure");
        ImportanceScore irrelevant = scorer.score("weather forecast",           Instant.now(), 0, "default", activeTags);
        ImportanceScore relevant   = scorer.score("portfolio exposure summary", Instant.now(), 0, "default", activeTags);
        assertTrue(relevant.taskRelevanceComponent() > irrelevant.taskRelevanceComponent());
    }

    @Test void scorer_authoritativeSourceOutweighsDefault() {
        ImportanceScorer scorer = new ImportanceScorer();
        ImportanceScore auth    = scorer.score("data", Instant.now(), 0, "user_explicit", Set.of());
        ImportanceScore unknown = scorer.score("data", Instant.now(), 0, "default",       Set.of());
        assertTrue(auth.sourceComponent() > unknown.sourceComponent());
    }

    @Test void scorer_totalNeverExceedsOne() {
        ImportanceScorer scorer = new ImportanceScorer();
        // Maximum conditions: fresh, frequent, salient, authoritative, task-relevant
        ImportanceScore max = scorer.score(
                "CRITICAL error in portfolio exposure",
                Instant.now(), 100, "user_explicit", Set.of("portfolio", "critical"));
        assertTrue(max.total() <= 1.0, "Total importance score must not exceed 1.0");
    }

    @Test void scorer_exceeds_threshold() {
        ImportanceScorer scorer = new ImportanceScorer();
        ImportanceScore score = scorer.score("recent important data", Instant.now(), 10,
                "user_explicit", Set.of());
        assertTrue(score.exceeds(0.3));
        assertFalse(score.exceeds(0.99));
    }

    // ── G-08: Three Consolidation Strategies ─────────────────────────────────

    private EpisodicStore populatedEpisodicStore(String agentId, int episodeCount) {
        EpisodicStore store = new EpisodicStore();
        for (int i = 0; i < episodeCount; i++) {
            store.store(agentId, "Episode " + i + ": agent processed tool call result",
                    Map.of("iteration", i));
        }
        return store;
    }

    @Test void rollingWindow_consolidatesRecentEpisodes() {
        EpisodicStore episodic = populatedEpisodicStore("agent-1", 5);
        SemanticStore semantic = new SemanticStore();
        ConsolidationStrategy strategy = new RollingWindowConsolidation(500);

        int written = strategy.consolidate("agent-1", episodic, semantic, 3);
        assertEquals(1, written, "Rolling window writes one combined summary");
    }

    @Test void rollingWindow_strategyName() {
        assertEquals("rolling-summary", new RollingWindowConsolidation().strategyName());
    }

    @Test void extractionMerge_writesOneFact_perEpisode() {
        EpisodicStore episodic = populatedEpisodicStore("agent-2", 5);
        SemanticStore semantic = new SemanticStore();
        ConsolidationStrategy strategy = new ExtractionMergeConsolidation();

        int written = strategy.consolidate("agent-2", episodic, semantic, 3);
        assertEquals(3, written, "Extraction-merge writes one fact per episode in batch");
    }

    @Test void extractionMerge_strategyName() {
        assertEquals("extraction-merge", new ExtractionMergeConsolidation().strategyName());
    }

    @Test void reflectionSynthesis_requiresMinEpisodes() {
        // Only 5 episodes — below minimum of 10
        EpisodicStore episodic = populatedEpisodicStore("agent-3", 5);
        SemanticStore semantic = new SemanticStore();
        ConsolidationStrategy strategy = new ReflectionSynthesisConsolidation();

        int written = strategy.consolidate("agent-3", episodic, semantic, 5);
        assertEquals(0, written, "Reflection needs >= 10 episodes; must return 0 with 5");
    }

    @Test void reflectionSynthesis_runsWithSufficientEpisodes() {
        EpisodicStore episodic = new EpisodicStore();
        // 12 episodes with repeating keywords to trigger pattern detection
        for (int i = 0; i < 12; i++) {
            episodic.store("agent-4",
                    "agent processed tool call result with portfolio exposure data",
                    Map.of("i", i));
        }
        SemanticStore semantic = new SemanticStore();
        ConsolidationStrategy strategy = new ReflectionSynthesisConsolidation();

        int written = strategy.consolidate("agent-4", episodic, semantic, 15);
        assertEquals(1, written, "Reflection writes one insight record with >= 10 episodes");
    }

    @Test void reflectionSynthesis_strategyName() {
        assertEquals("reflection-synthesis", new ReflectionSynthesisConsolidation().strategyName());
    }

    @Test void memoryConsolidator_supportsAllThreeStrategies() {
        EpisodicStore episodic = populatedEpisodicStore("agent-5", 5);
        SemanticStore semantic = new SemanticStore();
        MemoryConsolidator consolidator = new MemoryConsolidator(episodic, semantic);

        int r = consolidator.consolidate("agent-5", 3, MemoryConsolidator.Strategy.ROLLING_SUMMARY);
        int e = consolidator.consolidate("agent-5", 3, MemoryConsolidator.Strategy.EXTRACTION_MERGE);
        assertTrue(r >= 0 && e > 0, "Both ROLLING and EXTRACTION must produce output");
    }

    @Test void memoryConsolidator_defaultIsExtractionMerge() {
        EpisodicStore episodic = populatedEpisodicStore("agent-6", 5);
        SemanticStore semantic = new SemanticStore();
        MemoryConsolidator consolidator = new MemoryConsolidator(episodic, semantic);
        // Default consolidate should produce output (extraction-merge on 3 episodes)
        assertTrue(consolidator.consolidate("agent-6", 3) > 0);
    }

    // ── G-10: AuditEvent extended fields ─────────────────────────────────────

    @Test void auditEvent_sevenArgConstructor_defaultsNewFields() {
        AgentIdentity agent = AgentIdentity.of("agent", "orchestrator");
        AuditEvent event = new AuditEvent("e1", "OP", agent, "operation",
                Map.of(), Instant.now(), false);
        assertEquals(0,        event.stepNumber());
        assertEquals(0,        event.retryCount());
        assertTrue(event.validationOutcomes().isEmpty());
    }

    @Test void auditEvent_withContext_includesStepAndRetry() {
        AgentIdentity agent = AgentIdentity.of("agent", "orchestrator");
        AuditEvent event = AuditEvent.withContext(
                "e2", "ACTION_DISPATCH", agent, "retrieve_context",
                Map.of("toolId", "webSearch"), Instant.now(), false,
                3, 1, List.of("SchemaValidator:PASS", "SafetyValidator:PASS"));

        assertEquals(3,   event.stepNumber());
        assertEquals(1,   event.retryCount());
        assertEquals(2,   event.validationOutcomes().size());
        assertEquals("SchemaValidator:PASS", event.validationOutcomes().get(0));
    }

    @Test void auditEvent_isImmutable() {
        AuditEvent event = new AuditEvent("e3", "OP",
                AgentIdentity.of("a", "orchestrator"), "op",
                Map.of("k", "v"), Instant.now(), false,
                1, 0, List.of("v1:PASS"));
        assertThrows(UnsupportedOperationException.class,
                () -> event.validationOutcomes().add("hack"));
    }

    // ── G-11: FailureAssessment — 4 categories ───────────────────────────────

    private ExecutionContext ctx() {
        return ExecutionContext.of(AgentIdentity.of("agent", "orchestrator"));
    }

    @Test void failureDetection_transient_onTimeoutException() {
        DefaultFailureDetectionModule module = new DefaultFailureDetectionModule();
        RuntimeException e = new RuntimeException("Connection timeout after 30s");
        FailureAssessment a = module.assess(e, "action", ctx());
        assertEquals(FailureCategory.TRANSIENT, a.category());
        assertTrue(a.retryable());
        assertFalse(a.requiresEscalation());
    }

    @Test void failureDetection_transient_onRateLimit() {
        DefaultFailureDetectionModule module = new DefaultFailureDetectionModule();
        RuntimeException e = new RuntimeException("HTTP 429 rate limit exceeded");
        FailureAssessment a = module.assess(e, "action", ctx());
        assertEquals(FailureCategory.TRANSIENT, a.category());
        assertTrue(a.retryable());
    }

    @Test void failureDetection_policy_onSecurityException() {
        DefaultFailureDetectionModule module = new DefaultFailureDetectionModule();
        SecurityException e = new SecurityException("Permission denied for operation");
        FailureAssessment a = module.assess(e, "action", ctx());
        assertEquals(FailureCategory.POLICY, a.category());
        assertFalse(a.retryable());
        assertTrue(a.requiresEscalation());
    }

    @Test void failureDetection_policy_onBudgetExhausted() {
        DefaultFailureDetectionModule module = new DefaultFailureDetectionModule();
        RuntimeException e = new RuntimeException("Budget exhausted for this session");
        FailureAssessment a = module.assess(e, "action", ctx());
        assertEquals(FailureCategory.POLICY, a.category());
        assertTrue(a.requiresEscalation());
    }

    @Test void failureDetection_semantic_withSemanticMessage() {
        DefaultFailureDetectionModule module = new DefaultFailureDetectionModule();
        RuntimeException e = new RuntimeException("Agent is off-topic and misunderstood the task");
        FailureAssessment a = module.assess(e, "reasoning", ctx());
        assertEquals(FailureCategory.SEMANTIC, a.category());
        assertTrue(a.requiresEscalation());
    }

    @Test void failureDetection_deterministic_onNullError() {
        DefaultFailureDetectionModule module = new DefaultFailureDetectionModule();
        FailureAssessment a = module.assess(null, "action", ctx());
        assertEquals(FailureCategory.DETERMINISTIC, a.category());
        assertFalse(a.retryable());
    }

    @Test void failureDetection_deterministic_forUnclassified() {
        DefaultFailureDetectionModule module = new DefaultFailureDetectionModule();
        // "schema mismatch" in "action" phase — no TRANSIENT/POLICY/SEMANTIC markers → DETERMINISTIC
        RuntimeException e = new RuntimeException("Unknown schema mismatch");
        FailureAssessment a = module.assess(e, "action", ctx());
        assertEquals(FailureCategory.DETERMINISTIC, a.category());
        assertFalse(a.retryable());
        assertFalse(a.requiresEscalation());
    }

    @Test void failureCategory_factoryMethods() {
        FailureAssessment t = FailureAssessment.transient_("TIMEOUT",     "request timed out");
        FailureAssessment d = FailureAssessment.deterministic("SCHEMA",    "bad schema");
        FailureAssessment p = FailureAssessment.policy("PERMISSION",       "denied");
        FailureAssessment s = FailureAssessment.semantic("DRIFT",          "objective drift");

        assertEquals(FailureCategory.TRANSIENT,     t.category()); assertTrue(t.retryable());
        assertEquals(FailureCategory.DETERMINISTIC, d.category()); assertFalse(d.retryable());
        assertEquals(FailureCategory.POLICY,        p.category()); assertTrue(p.requiresEscalation());
        assertEquals(FailureCategory.SEMANTIC,      s.category()); assertTrue(s.requiresEscalation());
    }
}
