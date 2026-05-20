package com.agentruntime;

import com.agentruntime.core.enums.SourceTrustLabel;
import com.agentruntime.core.enums.ToolCategory;
import com.agentruntime.core.valueobjects.*;
import com.agentruntime.hitl.*;
import com.agentruntime.memory.procedural.ProceduralMemoryStore;
import com.agentruntime.tooling.async.AsyncToolExecutor;
import com.agentruntime.tooling.rag.*;
import com.agentruntime.tooling.registry.*;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests covering:
 * - HitlGateway / InMemoryHitlGateway (Vol.1 Ch.16-17)
 * - ToolRegistry
 * - AsyncToolExecutor with Java 21 virtual threads
 * - ProceduralMemoryStore (Vol.1 Ch.10)
 * - DefaultFederatedRetrievalCoordinator + TrustAwareFederatedMergePolicy (Vol.1 Ch.11)
 */
class InfrastructureTest {

    // ── HitlGateway ───────────────────────────────────────────────────────────

    @Test
    void hitlGateway_submitAndNotResolved() {
        InMemoryHitlGateway gateway = new InMemoryHitlGateway();
        AgentIdentity agent = AgentIdentity.of("orchestrator", "orchestrator");
        HitlRequest req = new HitlRequest("req-1", agent, "Is this valid?",
                Map.of(), HitlUrgency.HIGH, Instant.now());

        gateway.submit(req);
        assertFalse(gateway.isResolved("req-1"));
        assertTrue(gateway.poll("req-1").isEmpty());
        assertEquals(1, gateway.pendingCount());
    }

    @Test
    void hitlGateway_resolveAndPoll() {
        InMemoryHitlGateway gateway = new InMemoryHitlGateway();
        AgentIdentity agent = AgentIdentity.of("orch", "orchestrator");
        gateway.submit(new HitlRequest("req-2", agent, "Confirm?", Map.of(), HitlUrgency.NORMAL, Instant.now()));
        gateway.resolve("req-2", "Yes, confirmed.", "human-operator");

        assertTrue(gateway.isResolved("req-2"));
        assertTrue(gateway.poll("req-2").isPresent());
        assertEquals("Yes, confirmed.", gateway.poll("req-2").get().humanAnswer());
        assertEquals("human-operator", gateway.poll("req-2").get().responderId());
    }

    @Test
    void hitlGateway_multipleRequestsAreIndependent() {
        InMemoryHitlGateway gateway = new InMemoryHitlGateway();
        AgentIdentity agent = AgentIdentity.of("a", "orchestrator");
        gateway.submit(new HitlRequest("r1", agent, "Q1?", Map.of(), HitlUrgency.LOW, Instant.now()));
        gateway.submit(new HitlRequest("r2", agent, "Q2?", Map.of(), HitlUrgency.HIGH, Instant.now()));

        assertEquals(2, gateway.pendingCount());
        gateway.resolve("r1", "A1", "op");
        assertEquals(1, gateway.pendingCount()); // r1 removed from pending after resolution
        assertTrue(gateway.isResolved("r1"));
        assertFalse(gateway.isResolved("r2"));
    }

    @Test
    void hitlGateway_allPendingReturnsSnapshot() {
        InMemoryHitlGateway gateway = new InMemoryHitlGateway();
        AgentIdentity agent = AgentIdentity.of("a", "orchestrator");
        gateway.submit(new HitlRequest("r1", agent, "Q?", Map.of(), HitlUrgency.LOW, Instant.now()));
        List<HitlRequest> pending = gateway.allPending();
        assertEquals(1, pending.size());
        assertEquals("r1", pending.get(0).requestId());
    }

    @Test
    void hitlGateway_blockingUrgencyIsHighestPriority() {
        // Just verify enum value exists and resolves correctly
        InMemoryHitlGateway gateway = new InMemoryHitlGateway();
        AgentIdentity agent = AgentIdentity.of("a", "orchestrator");
        gateway.submit(new HitlRequest("r-block", agent, "URGENT",
                Map.of(), HitlUrgency.BLOCKING, Instant.now()));
        assertEquals(1, gateway.pendingCount());
        assertEquals(HitlUrgency.BLOCKING, gateway.allPending().get(0).urgency());
    }

    // ── ToolRegistry ──────────────────────────────────────────────────────────

    @Test
    void toolRegistry_emptyHasNoTools() {
        ToolRegistry registry = ToolRegistry.empty();
        assertEquals(0, registry.count());
        assertTrue(registry.all().isEmpty());
    }

    @Test
    void toolRegistry_registerAndFind() {
        ToolRegistry registry = ToolRegistry.empty();
        ToolDefinition tool = ToolDefinition.of("web-search", "webSearch", "Search the web", ToolCategory.DATA_RETRIEVAL, Map.of("query", "string"), false);
        registry.register(tool);
        assertEquals(1, registry.count());
        assertTrue(registry.find("web-search").isPresent());
        assertEquals("webSearch", registry.find("web-search").get().name());
    }

    @Test
    void toolRegistry_findByCategory() {
        ToolRegistry registry = ToolRegistry.empty();
        registry.register(ToolDefinition.of("t1", "tool1", "d", ToolCategory.DATA_RETRIEVAL, Map.of(), false));
        registry.register(ToolDefinition.of("t2", "tool2", "d", ToolCategory.COMPUTATION, Map.of(), false));
        registry.register(ToolDefinition.of("t3", "tool3", "d", ToolCategory.DATA_RETRIEVAL, Map.of(), false));

        assertEquals(2, registry.byCategory(ToolCategory.DATA_RETRIEVAL).size());
        assertEquals(1, registry.byCategory(ToolCategory.COMPUTATION).size());
        assertEquals(0, registry.byCategory(ToolCategory.COMMUNICATION).size());
    }

    @Test
    void toolRegistry_deregister() {
        ToolRegistry registry = ToolRegistry.empty();
        registry.register(ToolDefinition.of("t1", "tool1", "d", ToolCategory.DATA_RETRIEVAL, Map.of(), false));
        registry.deregister("t1");
        assertEquals(0, registry.count());
        assertTrue(registry.find("t1").isEmpty());
    }

    @Test
    void toolRegistry_unknownIdReturnsEmpty() {
        assertTrue(ToolRegistry.empty().find("nonexistent").isEmpty());
    }

    // ── AsyncToolExecutor (Java 21 virtual threads) ───────────────────────────

    @Test
    void asyncToolExecutor_completesSuccessfully() throws Exception {
        AsyncToolExecutor executor = new AsyncToolExecutor();
        ExecutionContext ctx = ExecutionContext.of(AgentIdentity.of("agent", "orchestrator"));
        Map<String, Object> result = executor.executeAsync("webSearch", Map.of("query", "test"), ctx)
                .get(5, TimeUnit.SECONDS);

        assertNotNull(result);
        assertEquals("webSearch", result.get("toolId"));
        assertEquals("completed",  result.get("status"));
    }

    @Test
    void asyncToolExecutor_preservesInputParams() throws Exception {
        AsyncToolExecutor executor = new AsyncToolExecutor();
        ExecutionContext ctx = ExecutionContext.of(AgentIdentity.of("a", "orchestrator"));
        Map<String, Object> result = executor.executeAsync("tool-x",
                Map.of("key1", "value1", "key2", 42), ctx).get(5, TimeUnit.SECONDS);
        assertEquals("value1", result.get("key1"));
        assertEquals(42, result.get("key2"));
    }

    @Test
    void asyncToolExecutor_concurrentExecutionsAreIndependent() throws Exception {
        AsyncToolExecutor executor = new AsyncToolExecutor();
        ExecutionContext ctx = ExecutionContext.of(AgentIdentity.of("a", "orchestrator"));
        var f1 = executor.executeAsync("tool-1", Map.of(), ctx);
        var f2 = executor.executeAsync("tool-2", Map.of(), ctx);
        var f3 = executor.executeAsync("tool-3", Map.of(), ctx);

        assertEquals("tool-1", f1.get(5, TimeUnit.SECONDS).get("toolId"));
        assertEquals("tool-2", f2.get(5, TimeUnit.SECONDS).get("toolId"));
        assertEquals("tool-3", f3.get(5, TimeUnit.SECONDS).get("toolId"));
    }

    @Test
    void asyncToolExecutor_capturesAgentId() throws Exception {
        AsyncToolExecutor executor = new AsyncToolExecutor();
        AgentIdentity agent = new AgentIdentity("my-agent-007", "myAgent", "orchestrator", "default");
        ExecutionContext ctx = ExecutionContext.of(agent);
        Map<String, Object> result = executor.executeAsync("tool", Map.of(), ctx).get(5, TimeUnit.SECONDS);
        assertEquals("my-agent-007", result.get("executedBy"));
    }

    // ── ProceduralMemoryStore ─────────────────────────────────────────────────

    @Test
    void proceduralMemory_storeAndRecall() {
        ProceduralMemoryStore store = new ProceduralMemoryStore();
        MemoryRecordId id = store.storeProcedure("generate-report",
                List.of("fetch data", "normalize", "format", "export"));
        assertNotNull(id);
        assertTrue(store.recall("generate-report").isPresent());
        assertEquals(4, store.recall("generate-report").get().steps().size());
    }

    @Test
    void proceduralMemory_unknownProcedureIsEmpty() {
        assertTrue(new ProceduralMemoryStore().recall("non-existent").isEmpty());
    }

    @Test
    void proceduralMemory_updateSuccessRate() {
        ProceduralMemoryStore store = new ProceduralMemoryStore();
        store.storeProcedure("do-thing", List.of("s1", "s2"));
        store.updateSuccessRate("do-thing", 0.75);
        assertEquals(0.75, store.recall("do-thing").get().successRate(), 0.001);
    }

    @Test
    void proceduralMemory_initialSuccessRateIsOne() {
        ProceduralMemoryStore store = new ProceduralMemoryStore();
        store.storeProcedure("proc", List.of("step"));
        assertEquals(1.0, store.recall("proc").get().successRate(), 0.001);
    }

    @Test
    void proceduralMemory_multipleProceduresIndependent() {
        ProceduralMemoryStore store = new ProceduralMemoryStore();
        store.storeProcedure("proc-a", List.of("a1", "a2"));
        store.storeProcedure("proc-b", List.of("b1", "b2", "b3"));
        assertEquals(2, store.recall("proc-a").get().steps().size());
        assertEquals(3, store.recall("proc-b").get().steps().size());
    }

    // ── FederatedRetrievalCoordinator ─────────────────────────────────────────

    @Test
    void federatedRetrieval_returnsMergedResults() {
        CosineSimilarity cosine = new CosineSimilarity();
        TrustAwareFederatedMergePolicy policy = new TrustAwareFederatedMergePolicy(cosine);
        com.agentruntime.tooling.rag.MemoryStoreRegistry registry =
                new com.agentruntime.tooling.rag.MemoryStoreRegistry();

        // Register real memory stores (S-01 fix: no simulated data)
        com.agentruntime.memory.semantic.SemanticStore semStore =
                new com.agentruntime.memory.semantic.SemanticStore();
        semStore.store("orchestration-patterns",
                "ReAct (Reason+Act) and event-driven orchestration are the dominant agent patterns.",
                Map.<String,Object>of("source", "knowledge-base"));
        registry.registerSemantic("semantic", semStore);

        com.agentruntime.memory.episodic.EpisodicStore epiStore =
                new com.agentruntime.memory.episodic.EpisodicStore();
        registry.registerEpisodic("episodic", epiStore);

        DefaultFederatedRetrievalCoordinator coordinator =
                new DefaultFederatedRetrievalCoordinator(policy, registry);

        SourceDescriptor src1 = new SourceDescriptor("semantic", "memory", SourceTrustLabel.VERIFIED, "Semantic Memory");
        SourceDescriptor src2 = new SourceDescriptor("episodic", "memory", SourceTrustLabel.TRUSTED_TOOL, "Episodic Memory");

        FederatedRetrievalRequest request = new FederatedRetrievalRequest(
                "orchestration", List.of(src1, src2), 3, false);
        ExecutionContext ctx = ExecutionContext.of(AgentIdentity.of("rag-agent", "orchestrator"));
        FederatedRetrievalResult result = coordinator.retrieve(request, ctx);

        assertNotNull(result);
        assertFalse(result.hits().isEmpty(), "Semantic store has relevant data; hits must not be empty");
        assertEquals(SourceTrustLabel.VERIFIED, result.trustLabel());
    }

    @Test
    void federatedRetrieval_singleSourceReturnsHits() {
        CosineSimilarity cosine = new CosineSimilarity();
        TrustAwareFederatedMergePolicy policy = new TrustAwareFederatedMergePolicy(cosine);
        com.agentruntime.tooling.rag.MemoryStoreRegistry registry =
                new com.agentruntime.tooling.rag.MemoryStoreRegistry();
        com.agentruntime.memory.semantic.SemanticStore kbStore =
                new com.agentruntime.memory.semantic.SemanticStore();
        kbStore.store("agent-patterns",
                "Agent patterns include ReAct, event-driven, and hierarchical delegation.",
                Map.<String,Object>of());
        registry.registerSemantic("kb", kbStore);

        DefaultFederatedRetrievalCoordinator coordinator =
                new DefaultFederatedRetrievalCoordinator(policy, registry);
        SourceDescriptor src = new SourceDescriptor("kb", "memory", SourceTrustLabel.VERIFIED, "Knowledge Base");
        FederatedRetrievalRequest request = new FederatedRetrievalRequest(
                "agent", List.of(src), 5, false);
        ExecutionContext ctx = ExecutionContext.of(AgentIdentity.of("a", "orchestrator"));
        FederatedRetrievalResult result = coordinator.retrieve(request, ctx);

        assertNotNull(result);
        assertFalse(result.hits().isEmpty(), "Semantic store has data; hits must not be empty");
    }

    @Test
    void federatedRetrieval_topKLimitsResults() {
        CosineSimilarity cosine = new CosineSimilarity();
        TrustAwareFederatedMergePolicy policy = new TrustAwareFederatedMergePolicy(cosine);
        com.agentruntime.tooling.rag.MemoryStoreRegistry registry =
                new com.agentruntime.tooling.rag.MemoryStoreRegistry();

        // Register 3 semantic stores with multiple entries each
        for (String id : List.of("s1", "s2", "s3")) {
            com.agentruntime.memory.semantic.SemanticStore s =
                    new com.agentruntime.memory.semantic.SemanticStore();
            for (int i = 0; i < 3; i++) {
                s.store(id + "-concept-" + i, "agent pattern data " + id + " " + i,
                        Map.<String,Object>of());
            }
            registry.registerSemantic(id, s);
        }

        DefaultFederatedRetrievalCoordinator coordinator =
                new DefaultFederatedRetrievalCoordinator(policy, registry);

        List<SourceDescriptor> sources = List.of(
            new SourceDescriptor("s1", "m", SourceTrustLabel.VERIFIED,       "S1"),
            new SourceDescriptor("s2", "m", SourceTrustLabel.TRUSTED_TOOL,   "S2"),
            new SourceDescriptor("s3", "m", SourceTrustLabel.AGENT_INFERRED, "S3")
        );
        FederatedRetrievalRequest request = new FederatedRetrievalRequest("agent", sources, 5, false);
        ExecutionContext ctx = ExecutionContext.of(AgentIdentity.of("a", "orchestrator"));
        FederatedRetrievalResult result = coordinator.retrieve(request, ctx);

        assertTrue(result.hits().size() <= 5, "topK=5 must cap results");
    }
}
