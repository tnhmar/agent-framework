package com.agentruntime;

import com.agentruntime.core.enums.SourceTrustLabel;
import com.agentruntime.core.enums.ToolCategory;
import com.agentruntime.core.valueobjects.*;
import com.agentruntime.memory.episodic.EpisodicStore;
import com.agentruntime.memory.semantic.SemanticStore;
import com.agentruntime.memory.shared.*;
import com.agentruntime.memory.usermodel.*;
import com.agentruntime.memory.working.WorkingMemoryStore;
import com.agentruntime.modelclient.StubModelClient;
import com.agentruntime.orchestrator.*;
import com.agentruntime.orchestrator.action.validation.*;
import com.agentruntime.orchestrator.failuredetection.*;
import com.agentruntime.orchestrator.perception.*;
import com.agentruntime.orchestrator.prompting.*;
import com.agentruntime.orchestrator.reasoning.ReasoningResult;
import com.agentruntime.orchestrator.reflection.*;
import com.agentruntime.protocols.anp.*;
import com.agentruntime.protocols.gateway.*;
import com.agentruntime.protocols.mcp.*;
import com.agentruntime.runtime.builder.AgentRuntimeBuilder;
import com.agentruntime.security.*;
import com.agentruntime.tooling.enterprise.*;
import com.agentruntime.tooling.rag.*;
import com.agentruntime.tooling.registry.*;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Production-grade fixes test suite.
 * Covers: S-01 through S-12, V-OCP-01/02/03, V-ISP-01/02, V-SRP-01/02, V-LSP-01, V-DIP-02.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ProductionGradeFixesTest {

    // ═══════════════════════════════════════════════════════════════
    // S-01: FederatedRetrievalCoordinator — real memory data
    // ═══════════════════════════════════════════════════════════════

    @Test @Order(1)
    void s01_federatedRetrieval_failsLoudlyForUnregisteredSource() {
        MemoryStoreRegistry registry = new MemoryStoreRegistry();
        TrustAwareFederatedMergePolicy policy =
                new TrustAwareFederatedMergePolicy(new CosineSimilarity());
        DefaultFederatedRetrievalCoordinator coordinator =
                new DefaultFederatedRetrievalCoordinator(policy, registry);

        SourceDescriptor unregistered = new SourceDescriptor(
                "unknown-source", "memory", SourceTrustLabel.VERIFIED, "Unknown");
        FederatedRetrievalRequest req = new FederatedRetrievalRequest(
                "query", List.of(unregistered), 5, false);
        ExecutionContext ctx = ExecutionContext.of(AgentIdentity.of("agent", "orchestrator"));

        // Must NOT silently return fabricated data — must throw
        assertThrows(IllegalStateException.class, () -> coordinator.retrieve(req, ctx),
                "Unregistered source must throw, not return simulated data");
    }

    @Test @Order(2)
    void s01_federatedRetrieval_returnsRealSemanticData() {
        MemoryStoreRegistry registry = new MemoryStoreRegistry();
        SemanticStore store = new SemanticStore();
        store.store("react-pattern",
                "ReAct combines reasoning and acting in LLM agents.",
                Map.<String,Object>of("source", "kb"));
        registry.registerSemantic("kb", store);

        TrustAwareFederatedMergePolicy policy =
                new TrustAwareFederatedMergePolicy(new CosineSimilarity());
        DefaultFederatedRetrievalCoordinator coordinator =
                new DefaultFederatedRetrievalCoordinator(policy, registry);

        SourceDescriptor src = new SourceDescriptor(
                "kb", "memory", SourceTrustLabel.VERIFIED, "KB");
        FederatedRetrievalRequest req = new FederatedRetrievalRequest(
                "ReAct", List.of(src), 5, false);
        ExecutionContext ctx = ExecutionContext.of(AgentIdentity.of("agent", "orchestrator"));

        FederatedRetrievalResult result = coordinator.retrieve(req, ctx);
        assertNotNull(result);
        assertFalse(result.hits().isEmpty(), "Must return real data from SemanticStore");
        // Content must be the real stored definition, not "Sample content..."
        String content = result.hits().get(0).content();
        assertTrue(content.contains("ReAct") || content.contains("reasoning"),
                "Hit content must come from real SemanticStore, got: " + content);
    }

    @Test @Order(3)
    void s01_federatedRetrieval_topKRespectedWithRealData() {
        MemoryStoreRegistry registry = new MemoryStoreRegistry();
        SemanticStore store = new SemanticStore();
        for (int i = 0; i < 10; i++) {
            store.store("concept-" + i, "agent pattern " + i + " description text", Map.of());
        }
        registry.registerSemantic("sem", store);

        TrustAwareFederatedMergePolicy policy =
                new TrustAwareFederatedMergePolicy(new CosineSimilarity());
        DefaultFederatedRetrievalCoordinator coordinator =
                new DefaultFederatedRetrievalCoordinator(policy, registry);

        SourceDescriptor src = new SourceDescriptor(
                "sem", "memory", SourceTrustLabel.VERIFIED, "Semantic");
        FederatedRetrievalRequest req = new FederatedRetrievalRequest(
                "agent", List.of(src), 3, false);
        ExecutionContext ctx = ExecutionContext.of(AgentIdentity.of("a", "orchestrator"));

        FederatedRetrievalResult result = coordinator.retrieve(req, ctx);
        assertTrue(result.hits().size() <= 3, "topK=3 must cap results");
    }

    @Test @Order(4)
    void s01_memoryStoreRegistry_hasStores() {
        MemoryStoreRegistry registry = new MemoryStoreRegistry();
        assertFalse(registry.hasStores());
        registry.registerSemantic("s1", new SemanticStore());
        assertTrue(registry.hasStores());
    }

    // ═══════════════════════════════════════════════════════════════
    // S-03: McpClient — real handler dispatch
    // ═══════════════════════════════════════════════════════════════

    @Test @Order(5)
    void s03_mcpClient_failsLoudlyWithNoHandler() {
        McpClient client = new McpClient("client-test");
        client.initialize(Map.of());
        McpResponse response = client.toolsCall("unknown-tool", Map.of());
        // Must return an error response, not silently succeed
        assertFalse(response.isSuccess(), "Unregistered tool must return error, not silent success");
        assertNotNull(response.error());
        assertTrue(response.error().message().contains("not found")
                || response.error().message().contains("Tool not found"),
                "Error message must indicate missing handler, got: " + response.error().message());
    }

    @Test @Order(6)
    void s03_mcpClient_requiresInitialization() {
        McpClient client = new McpClient("client-2");
        // toolsCall before initialize must fail with clear error
        McpResponse response = client.toolsCall("tool", Map.of());
        assertFalse(response.isSuccess());
        assertNotNull(response.error());
        assertTrue(response.error().message().contains("not initialized"),
                "Must say client not initialized");
    }

    @Test @Order(7)
    void s03_mcpClient_registeredHandlerDispatchesCorrectly() {
        McpClient client = new McpClient("client-3");
        client.initialize(Map.of());
        client.registerHandler("search", (name, args) ->
                Map.of("results", List.of("result-1", "result-2"),
                       "query", args.getOrDefault("q", "")));

        McpResponse response = client.toolsCall("search", Map.of("q", "agent patterns"));
        assertTrue(response.isSuccess());
        assertNull(response.error());
        @SuppressWarnings("unchecked") var resultMap = (java.util.Map<String,Object>) response.result();
        assertEquals(2, ((List<?>) resultMap.get("results")).size());
    }

    @Test @Order(8)
    void s03_mcpClient_handlerExceptionReturnsErrorResponse() {
        McpClient client = new McpClient("client-4");
        client.initialize(Map.of());
        client.registerHandler("broken", (name, args) -> {
            throw new RuntimeException("Downstream service unavailable");
        });

        McpResponse response = client.toolsCall("broken", Map.of());
        assertFalse(response.isSuccess());
        assertNotNull(response.error());
        assertTrue(response.error().message().contains("Downstream"));
    }

    @Test @Order(9)
    void s03_mcpClient_hasHandlerChecks() {
        McpClient client = new McpClient("c");
        assertFalse(client.hasHandler("tool"));
        client.registerHandler("tool", (n, a) -> Map.of("ok", true));
        assertTrue(client.hasHandler("tool"));
        assertEquals(1, client.handlerCount());
    }

    // ═══════════════════════════════════════════════════════════════
    // S-04: DefaultProtocolGateway — fail loudly
    // ═══════════════════════════════════════════════════════════════

    @Test @Order(10)
    void s04_protocolGateway_throwsForUnregisteredProtocol() {
        DefaultProtocolGateway gateway = new DefaultProtocolGateway();
        ExecutionContext ctx = ExecutionContext.of(AgentIdentity.of("a", "orchestrator"));
        assertThrows(UnsupportedOperationException.class,
                () -> gateway.dispatch(
                        com.agentruntime.core.enums.ProtocolType.MCP,
                        "target", Map.of(), ctx),
                "Unregistered protocol must throw, not no-op");
    }

    @Test @Order(11)
    void s04_protocolGateway_registeredHandlerDispatches() {
        DefaultProtocolGateway gateway = new DefaultProtocolGateway();
        gateway.registerHandler(com.agentruntime.core.enums.ProtocolType.MCP,
                (target, payload, ctx) -> Map.of("dispatched", true, "target", target));

        ExecutionContext ctx = ExecutionContext.of(AgentIdentity.of("a", "orchestrator"));
        Map<String, Object> result = gateway.dispatch(
                com.agentruntime.core.enums.ProtocolType.MCP,
                "mcp-server", Map.of("tool", "search"), ctx);

        assertTrue((Boolean) result.get("dispatched"));
        assertEquals("mcp-server", result.get("target"));
    }

    // ═══════════════════════════════════════════════════════════════
    // S-05: DefaultEnterpriseAdapter — fail loudly
    // ═══════════════════════════════════════════════════════════════

    @Test @Order(12)
    void s05_enterpriseAdapter_throwsForUnregisteredOperation() {
        DefaultEnterpriseAdapter adapter = new DefaultEnterpriseAdapter();
        ExecutionContext ctx = ExecutionContext.of(AgentIdentity.of("a", "orchestrator"));
        assertThrows(UnsupportedOperationException.class,
                () -> adapter.invoke("send_email", Map.of("to", "user@example.com"), ctx),
                "Unregistered operation must throw, not silently succeed");
    }

    @Test @Order(13)
    void s05_enterpriseAdapter_registeredOperationExecutes() {
        DefaultEnterpriseAdapter adapter = new DefaultEnterpriseAdapter();
        adapter.registerOperation("send_notification",
                (input, ctx) -> Map.of("sent", true, "recipient", input.get("to")));

        ExecutionContext ctx = ExecutionContext.of(AgentIdentity.of("a", "orchestrator"));
        Map<String, Object> result = adapter.invoke(
                "send_notification", Map.of("to", "ops-team", "message", "Report ready"), ctx);

        assertTrue((Boolean) result.get("sent"));
        assertEquals("ops-team", result.get("recipient"));
    }

    // ═══════════════════════════════════════════════════════════════
    // V-LSP-01: SharedMemoryStore.read() returns Optional
    // ═══════════════════════════════════════════════════════════════

    @Test @Order(14)
    void vLsp01_sharedMemory_readReturnsEmptyForMissingRecord() {
        InMemorySharedMemoryStore store = new InMemorySharedMemoryStore();
        AgentIdentity reader = new AgentIdentity("r", "R", "observer", "t");
        // Must return Optional.empty(), not null
        Optional<VersionedRecord> result = store.read("nonexistent", reader);
        assertNotNull(result, "read() must never return null — use Optional.empty()");
        assertTrue(result.isEmpty());
    }

    @Test @Order(15)
    void vLsp01_sharedMemory_readReturnsPresentForExistingRecord() {
        InMemorySharedMemoryStore store = new InMemorySharedMemoryStore();
        AgentIdentity writer = new AgentIdentity("w", "W", "orchestrator", "t");
        AgentIdentity reader = new AgentIdentity("r", "R", "observer", "t");

        store.writeWithVersionCheck("rec-1", Map.of("data", "value"), 0, writer);

        Optional<VersionedRecord> result = store.read("rec-1", reader);
        assertTrue(result.isPresent(), "Existing record must be returned as Optional.of(...)");
        assertEquals("value", result.get().content().get("data"));
    }

    // ═══════════════════════════════════════════════════════════════
    // V-OCP-01: SafetyPolicy injectable
    // ═══════════════════════════════════════════════════════════════

    @Test @Order(16)
    void vOcp01_safetyPolicy_defaultBlocks() {
        SafetyPolicy policy = SafetyPolicy.defaults();
        assertTrue(policy.isBlocked("purge_all"));
        assertTrue(policy.isBlocked("PURGE_ALL"), "Must be case-insensitive");
        assertFalse(policy.isBlocked("retrieve_context"));
    }

    @Test @Order(17)
    void vOcp01_safetyPolicy_runtimeBlockAndUnblock() {
        SafetyPolicy policy = SafetyPolicy.defaults();
        assertFalse(policy.isBlocked("delete_user_data"));
        policy.block("delete_user_data");
        assertTrue(policy.isBlocked("delete_user_data"));
        policy.unblock("delete_user_data");
        assertFalse(policy.isBlocked("delete_user_data"));
    }

    @Test @Order(18)
    void vOcp01_safetyValidator_usesInjectedPolicy() {
        SafetyPolicy customPolicy = new SafetyPolicy(Set.of("custom_blocked_action"));
        SafetyValidator validator = new SafetyValidator(customPolicy);

        SecurityEnforcer enforcer = new SecurityEnforcer(SecurityPolicy.allowAll());
        ExecutionContext ctx = ExecutionContext.of(AgentIdentity.of("a", "orchestrator"));

        // Default blocked actions (purge_all, etc.) are NOT blocked in custom policy
        ReasoningResult safe = new ReasoningResult("p", List.of("purge_all"), "r", 0.9, false);
        com.agentruntime.core.valueobjects.ValidationResult r1 = validator.validate(safe, ctx);
        assertTrue(r1.isPassed(), "purge_all not blocked in custom policy");

        // Custom blocked action IS blocked
        ReasoningResult blocked = new ReasoningResult("p", List.of("custom_blocked_action"), "r", 0.9, false);
        com.agentruntime.core.valueobjects.ValidationResult r2 = validator.validate(blocked, ctx);
        assertFalse(r2.isPassed(), "custom_blocked_action must be rejected");
    }

    // ═══════════════════════════════════════════════════════════════
    // V-OCP-02: Pluggable FailureClassifier chain
    // ═══════════════════════════════════════════════════════════════

    @Test @Order(19)
    void vOcp02_failureDetection_customClassifierAdded() {
        // Custom classifier: RuntimeException with "quota" → POLICY
        FailureClassifier quotaClassifier = (e, phase, ctx) -> {
            if (e instanceof RuntimeException
                    && e.getMessage() != null
                    && e.getMessage().contains("quota"))
                return Optional.of(FailureCategory.POLICY);
            return Optional.empty();
        };

        DefaultFailureDetectionModule module = new DefaultFailureDetectionModule(
                List.of(
                        new DefaultFailureDetectionModule.TransientClassifier(),
                        quotaClassifier,  // inserted before default policy classifier
                        new DefaultFailureDetectionModule.PolicyClassifier(),
                        new DefaultFailureDetectionModule.SemanticClassifier()
                ));

        ExecutionContext ctx = ExecutionContext.of(AgentIdentity.of("a", "orchestrator"));
        FailureAssessment a = module.assess(
                new RuntimeException("quota limit reached"), "action", ctx);
        assertEquals(FailureCategory.POLICY, a.category(),
                "Custom quota classifier must fire before generic policy classifier");
    }

    @Test @Order(20)
    void vOcp02_failureDetection_defaultConstructorWorks() {
        DefaultFailureDetectionModule module = new DefaultFailureDetectionModule();
        ExecutionContext ctx = ExecutionContext.of(AgentIdentity.of("a", "orchestrator"));

        assertEquals(FailureCategory.TRANSIENT,
                module.assess(new RuntimeException("timeout"), "action", ctx).category());
        assertEquals(FailureCategory.POLICY,
                module.assess(new SecurityException("denied"), "action", ctx).category());
        assertEquals(FailureCategory.SEMANTIC,
                module.assess(new RuntimeException("off-topic"), "action", ctx).category());
        assertEquals(FailureCategory.DETERMINISTIC,
                module.assess(new RuntimeException("schema error"), "action", ctx).category());
    }

    @Test @Order(21)
    void vOcp02_failureDetection_rejectsEmptyClassifierList() {
        assertThrows(IllegalArgumentException.class,
                () -> new DefaultFailureDetectionModule(List.of()),
                "Empty classifier list must be rejected");
    }

    // ═══════════════════════════════════════════════════════════════
    // V-OCP-03: RolePolicy injectable into SharedMemoryStore
    // ═══════════════════════════════════════════════════════════════

    @Test @Order(22)
    void vOcp03_rolePolicy_defaultMatchesVol1Spec() {
        RolePolicy policy = RolePolicy.defaults();
        // Write roles per Vol.1 Ch.13
        assertTrue(policy.canWrite("orchestrator"));
        assertTrue(policy.canWrite("specialist"));
        assertTrue(policy.canWrite("coordinator"));
        assertFalse(policy.canWrite("observer"), "observer must not be in WRITE_ROLES");
        assertFalse(policy.canWrite("guest"),    "guest must not be in WRITE_ROLES");

        // Read roles
        assertTrue(policy.canRead("observer"));
        assertTrue(policy.canRead("agent"));
        assertFalse(policy.canRead("unknown_role"));
    }

    @Test @Order(23)
    void vOcp03_sharedMemory_customRolePolicyRespected() {
        // Custom policy: only "admin" can write
        RolePolicy custom = new RolePolicy(Set.of("admin"), Set.of("admin", "reader"));
        InMemorySharedMemoryStore store = new InMemorySharedMemoryStore(
                Map.of(), com.agentruntime.core.enums.ConsistencyModel.EVENTUAL,
                new com.agentruntime.observability.ObservabilityBus(), custom);

        AgentIdentity admin = new AgentIdentity("a", "A", "admin", "t");
        AgentIdentity orchestrator = new AgentIdentity("o", "O", "orchestrator", "t");

        // admin can write
        WriteResult ok = store.writeWithVersionCheck("rec", Map.of("k", "v"), 0, admin);
        assertTrue(ok.success());

        // orchestrator cannot write (not in custom WRITE_ROLES)
        assertThrows(SecurityException.class,
                () -> store.writeWithVersionCheck("rec2", Map.of("k", "v"), 0, orchestrator),
                "orchestrator not in custom write roles must throw");
    }

    // ═══════════════════════════════════════════════════════════════
    // V-ISP-01: Segregated AgentOrchestrator interfaces
    // ═══════════════════════════════════════════════════════════════

    @Test @Order(24)
    void vIsp01_agentRunner_isIndependentInterface() {
        // AgentRunner can be used without AgentOrchestrator/AgentDelegate/AgentLifecycle
        AgentRunner runner = new AgentRuntimeBuilder()
                .modelClient(StubModelClient.direct("done"))
                .build();
        assertNotNull(runner);
        // Must compile — AgentRunner.run() is sufficient
        AgentTask task = new AgentTask("t", "goal", Map.of(), 1);
        ExecutionContext ctx = ExecutionContext.of(AgentIdentity.of("a", "orchestrator"));
        ExecutionResult result = runner.run(task, ctx);
        assertNotNull(result);
    }

    @Test @Order(25)
    void vIsp01_defaultOrchestratorImplementsAllThreeInterfaces() {
        DefaultAgentOrchestrator orch = AgentRuntimeBuilder.stub();
        assertTrue(orch instanceof AgentRunner,     "must implement AgentRunner");
        assertTrue(orch instanceof AgentDelegate,   "must implement AgentDelegate");
        assertTrue(orch instanceof AgentLifecycle,  "must implement AgentLifecycle");
        assertTrue(orch instanceof AgentOrchestrator, "must implement AgentOrchestrator");
    }

    // ═══════════════════════════════════════════════════════════════
    // V-ISP-02: Segregated SharedMemoryStore interfaces
    // ═══════════════════════════════════════════════════════════════

    @Test @Order(26)
    void vIsp02_sharedMemoryReader_isIndependentInterface() {
        InMemorySharedMemoryStore store = new InMemorySharedMemoryStore();
        // SharedMemoryReader can be used without knowing about write methods
        SharedMemoryReader reader = store;
        assertNotNull(reader);
        AgentIdentity ag = new AgentIdentity("a", "A", "observer", "t");
        assertTrue(reader.read("nonexistent", ag).isEmpty());
    }

    @Test @Order(27)
    void vIsp02_sharedMemoryWriter_isIndependentInterface() {
        InMemorySharedMemoryStore store = new InMemorySharedMemoryStore();
        SharedMemoryWriter writer = store;
        AgentIdentity ag = new AgentIdentity("w", "W", "orchestrator", "t");
        WriteResult r = writer.writeWithVersionCheck("key", Map.of("v", 1), 0, ag);
        assertTrue(r.success());
    }

    @Test @Order(28)
    void vIsp02_storeImplementsBothInterfaces() {
        InMemorySharedMemoryStore store = new InMemorySharedMemoryStore();
        assertTrue(store instanceof SharedMemoryReader, "must implement SharedMemoryReader");
        assertTrue(store instanceof SharedMemoryWriter, "must implement SharedMemoryWriter");
        assertTrue(store instanceof SharedMemoryStore,  "must implement SharedMemoryStore");
    }

    // ═══════════════════════════════════════════════════════════════
    // V-SRP-01: UserModelInjector extracted from SessionLifecycleManager
    // ═══════════════════════════════════════════════════════════════

    @Test @Order(29)
    void vSrp01_userModelInjector_injectsAboveThreshold() {
        UserModelInjector injector = new UserModelInjector();
        UserModel model = new UserModel("user-1");
        model.merge(UserModelFact.create("f1", "user-1", "pref", "lang", "TypeScript",
                ConfidenceLevel.STATED, "user_explicit"));
        model.merge(UserModelFact.create("f2", "user-1", "pref", "ide", "VSCode",
                ConfidenceLevel.SPECULATIVE, "agent_inferred"));

        WorkingMemoryStore wm = new WorkingMemoryStore();
        int injected = injector.inject(model, wm, ConfidenceLevel.INFERRED.value);

        assertEquals(1, injected, "Only STATED fact meets INFERRED threshold");
        assertTrue(wm.get("userModel:pref:lang").isPresent());
        assertFalse(wm.get("userModel:pref:ide").isPresent(), "SPECULATIVE must be excluded");
    }

    @Test @Order(30)
    void vSrp01_userModelInjector_toleratesNullModel() {
        UserModelInjector injector = new UserModelInjector();
        WorkingMemoryStore wm = new WorkingMemoryStore();
        assertEquals(0, injector.inject(null, wm), "Null model → 0 injected, no exception");
    }

    // ═══════════════════════════════════════════════════════════════
    // V-SRP-02: DelegationEngine extracted from orchestrator
    // ═══════════════════════════════════════════════════════════════

    @Test @Order(31)
    void vSrp02_delegationEngine_enforcesDepthLimit() {
        DefaultAgentOrchestrator orch = new AgentRuntimeBuilder()
                .modelClient(StubModelClient.direct("done"))
                .maxDelegationDepth(2)
                .build();

        AgentIdentity delegator = new AgentIdentity("parent", "P", "orchestrator", "t");
        AgentIdentity target    = new AgentIdentity("child",  "C", "specialist",   "t");
        ExecutionContext ctx     = ExecutionContext.of(delegator);

        com.agentruntime.orchestrator.delegation.DelegationContract contract =
                new com.agentruntime.orchestrator.delegation.DelegationContract(
                        "c1", "sub-task", Map.of(),
                        java.time.Duration.ofSeconds(30), 1000L,
                        "result-contract", "fail-protocol",
                        delegator, target,
                        3, // delegationDepth >= maxDepth(2) → must reject
                        java.time.Instant.now());

        com.agentruntime.orchestrator.delegation.DelegationResult result =
                orch.delegate(contract, ctx);
        assertFalse(result.success(), "Depth limit must be enforced");
        assertTrue(result.failureReason().contains("depth limit") ||
                   result.failureReason().contains("Delegation depth"),
                "Must report depth limit: " + result.failureReason());
    }

    // ═══════════════════════════════════════════════════════════════
    // S-07: DefaultPerceptionModule — improved entity extraction
    // ═══════════════════════════════════════════════════════════════

    @Test @Order(32)
    void s07_perception_extractsQuotedEntities() {
        DefaultPerceptionModule module = new DefaultPerceptionModule();
        RawInput input = new RawInput(
                "The system uses \"ReAct\" and \"event-driven\" patterns.", "text", Map.of());
        ExecutionContext ctx = ExecutionContext.of(AgentIdentity.of("a", "orchestrator"));
        PerceptionResult result = module.perceive(input, ctx);

        assertTrue(result.extractedEntities().contains("ReAct"),
                "Quoted entity 'ReAct' must be extracted");
        assertTrue(result.extractedEntities().contains("event-driven"),
                "Quoted entity 'event-driven' must be extracted");
    }

    @Test @Order(33)
    void s07_perception_extractsNumbers() {
        DefaultPerceptionModule module = new DefaultPerceptionModule();
        RawInput input = new RawInput("Portfolio increased by 15% to 2.3B in Q4 2025.", "text", Map.of());
        ExecutionContext ctx = ExecutionContext.of(AgentIdentity.of("a", "orchestrator"));
        PerceptionResult result = module.perceive(input, ctx);

        assertTrue(result.extractedEntities().stream().anyMatch(e -> e.contains("15")),
                "Percentage must be extracted");
    }

    @Test @Order(34)
    void s07_perception_stopWordsExcluded() {
        DefaultPerceptionModule module = new DefaultPerceptionModule();
        RawInput input = new RawInput("The system processes With These That Such things.", "text", Map.of());
        ExecutionContext ctx = ExecutionContext.of(AgentIdentity.of("a", "orchestrator"));
        PerceptionResult result = module.perceive(input, ctx);

        List<String> entities = result.extractedEntities();
        assertFalse(entities.contains("The"),   "Stop word 'The' must be excluded");
        assertFalse(entities.contains("With"),  "Stop word 'With' must be excluded");
        assertFalse(entities.contains("These"), "Stop word 'These' must be excluded");
    }

    @Test @Order(35)
    void s07_perception_confidenceScalesByContent() {
        DefaultPerceptionModule module = new DefaultPerceptionModule();
        ExecutionContext ctx = ExecutionContext.of(AgentIdentity.of("a", "orchestrator"));

        PerceptionResult empty = module.perceive(new RawInput("", "text", Map.of()), ctx);
        PerceptionResult rich  = module.perceive(new RawInput(
                "Analyse the \"portfolio\" exposure to US equities: current is 45% vs 40% limit.",
                "text", Map.of()), ctx);

        assertTrue(rich.confidenceScore() > empty.confidenceScore(),
                "Rich content must score higher confidence than empty input");
    }

    // ═══════════════════════════════════════════════════════════════
    // S-08: DefaultPromptingModule — context window management
    // ═══════════════════════════════════════════════════════════════

    @Test @Order(36)
    void s08_prompting_includesGoalInSystemPrompt() {
        DefaultPromptingModule module = new DefaultPromptingModule();
        PerceptionResult perception = new PerceptionResult(
                "Analyse portfolio exposure", List.of("portfolio", "exposure"), Map.of(), 0.9);
        AgentIdentity namedAgent = new AgentIdentity("agent-1", "ResearchAgent", "orchestrator", "default");
        ExecutionContext ctx = ExecutionContext.of(namedAgent);

        com.agentruntime.orchestrator.prompting.PromptPackage pkg =
                module.build(perception, "Generate risk report", ctx);

        assertTrue(pkg.systemPrompt().contains("Generate risk report"),
                "System prompt must contain the goal");
        assertTrue(pkg.systemPrompt().contains("agent-1"),
                "System prompt must contain the agent ID");
    }

    @Test @Order(37)
    void s08_prompting_includesEntitiesWhenPresent() {
        DefaultPromptingModule module = new DefaultPromptingModule();
        PerceptionResult perception = new PerceptionResult(
                "Check portfolio", List.of("Apple", "Microsoft"), Map.of(), 0.9);
        ExecutionContext ctx = ExecutionContext.of(AgentIdentity.of("a", "orchestrator"));

        com.agentruntime.orchestrator.prompting.PromptPackage pkg =
                module.build(perception, "goal", ctx);
        assertTrue(pkg.systemPrompt().contains("Apple") || pkg.systemPrompt().contains("Microsoft"),
                "Entities must appear in system prompt");
    }

    // ═══════════════════════════════════════════════════════════════
    // S-09: DefaultReflectionModule — content-level assessment
    // ═══════════════════════════════════════════════════════════════

    @Test @Order(38)
    void s09_reflection_assessesValidationCoverage() {
        DefaultReflectionModule module = new DefaultReflectionModule();
        ExecutionContext ctx = ExecutionContext.of(AgentIdentity.of("a", "orchestrator"));

        com.agentruntime.orchestrator.action.ActionResult highCoverage =
                com.agentruntime.orchestrator.action.ActionResult.success(
                        Map.of("actions", List.of("retrieve_context"), "plan", "p"),
                        List.of("SchemaValidator:PASS", "SemanticValidator:PASS",
                                "PolicyValidator:PASS",  "SafetyValidator:PASS"));

        com.agentruntime.orchestrator.action.ActionResult lowCoverage =
                com.agentruntime.orchestrator.action.ActionResult.success(
                        Map.of("actions", List.of("generate_response"), "plan", "p"),
                        List.of());

        ReflectionResult rich = module.reflect(highCoverage, ctx);
        ReflectionResult thin = module.reflect(lowCoverage, ctx);

        // Both succeed but high coverage lesson mentions "100%"
        assertTrue(rich.lessons().stream().anyMatch(l -> l.contains("100%")),
                "100% validation coverage must appear in lessons");
    }

    @Test @Order(39)
    void s09_reflection_detectsTransientFailureForRetry() {
        DefaultReflectionModule module = new DefaultReflectionModule();
        ExecutionContext ctx = ExecutionContext.of(AgentIdentity.of("a", "orchestrator"));

        com.agentruntime.orchestrator.action.ActionResult transientFail =
                com.agentruntime.orchestrator.action.ActionResult.failure(
                        "Connection timeout after 30s", List.of());

        ReflectionResult result = module.reflect(transientFail, ctx);
        assertFalse(result.goalAchieved());
        assertTrue(result.lessons().stream().anyMatch(l ->
                l.toLowerCase().contains("transient") || l.toLowerCase().contains("retry")),
                "Transient failure must be identified for retry guidance");
    }

    // ═══════════════════════════════════════════════════════════════
    // S-12: AnpPublisher — injectable transport
    // ═══════════════════════════════════════════════════════════════

    @Test @Order(40)
    void s12_anpPublisher_inProcessModeDelivers() throws AnpTransportException {
        AnpIdentity identity = AnpIdentity.of("did:example:agent-1");
        AnpPublisher publisher = new AnpPublisher(identity); // in-process mode

        List<AnpEnvelope> received = new ArrayList<>();
        publisher.subscribe("test.topic", received::add);
        publisher.publish("test.topic", Map.of("msg", "hello"));

        assertEquals(1, received.size());
        assertEquals("hello", received.get(0).body().get("msg"));
        assertEquals("did:example:agent-1", received.get(0).sourceNode());
    }

    @Test @Order(41)
    void s12_anpPublisher_networkTransportUsedWhenInjected() {
        AnpIdentity identity = AnpIdentity.of("did:example:agent-2");

        boolean[] transportCalled = {false};
        AnpTransport transport = (topic, message, sender) -> {
            transportCalled[0] = true;
        };

        AnpPublisher publisher = new AnpPublisher(identity, transport);
        assertTrue(publisher.isNetworkEnabled());

        assertDoesNotThrow(() -> publisher.publish("remote.topic", Map.of("data", "value")));
        assertTrue(transportCalled[0], "Network transport must be called when injected");
    }

    @Test @Order(42)
    void s12_anpPublisher_transportExceptionPropagates() {
        AnpIdentity identity = AnpIdentity.of("did:example:a3");
        AnpTransport failingTransport = (topic, message, sender) -> {
            throw new AnpTransportException("Network unreachable");
        };

        AnpPublisher publisher = new AnpPublisher(identity, failingTransport);
        assertThrows(AnpTransportException.class,
                () -> publisher.publish("topic", Map.of("data", "value")),
                "Transport failure must propagate, not be swallowed");
    }
}
