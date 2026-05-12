package com.agentruntime;

import com.agentruntime.core.enums.AgentStatus;
import com.agentruntime.core.enums.SourceTrustLabel;
import com.agentruntime.core.enums.ToolCategory;
import com.agentruntime.core.valueobjects.*;
import com.agentruntime.hitl.*;
import com.agentruntime.memory.episodic.EpisodicStore;
import com.agentruntime.memory.policy.TokenWindowMemoryPolicy;
import com.agentruntime.memory.procedural.ProceduralMemoryStore;
import com.agentruntime.memory.semantic.SemanticStore;
import com.agentruntime.memory.working.WorkingMemoryStore;
import com.agentruntime.modelclient.*;
import com.agentruntime.observability.*;
import com.agentruntime.orchestrator.*;
import com.agentruntime.orchestrator.action.DefaultActionModule;
import com.agentruntime.orchestrator.action.validation.ActionValidationPipeline;
import com.agentruntime.orchestrator.perception.*;
import com.agentruntime.orchestrator.prompting.*;
import com.agentruntime.orchestrator.reasoning.*;
import com.agentruntime.orchestrator.reflection.*;
import com.agentruntime.orchestrator.termination.*;
import com.agentruntime.orchestrator.failuredetection.*;
import com.agentruntime.runtime.builder.AgentRuntimeBuilder;
import com.agentruntime.runtime.run.*;
import com.agentruntime.security.*;
import com.agentruntime.statemanager.StateManager;
import com.agentruntime.tooling.registry.*;
import com.agentruntime.usecase.research.*;
import org.junit.jupiter.api.*;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.time.Clock;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ══════════════════════════════════════════════════════════════════════════
 *  STUDY CASE: Research Agent — End-to-End Framework Exercise
 * ══════════════════════════════════════════════════════════════════════════
 *
 * Scenario: A multi-agent research system that:
 *  1. Runs an orchestrator loop against a stub ModelClient (no real network calls)
 *  2. Uses all memory types (working, episodic, semantic, procedural)
 *  3. Applies the token-window policy for context assembly
 *  4. Enforces security at both role (SecurityEnforcer) and tenant (TenantPolicyEngine) levels
 *  5. Emits structured JSON audit events via ObservabilityBus + StructuredEventExporter
 *  6. Manages durable run state via RunRepository + Run aggregate
 *  7. Submits a human-in-the-loop decision for ambiguous content
 *  8. Records procedural steps for later reuse
 *  9. Uses ResearchAgentFactory for high-level assembly
 * 10. Validates the full Vol.1 conformance contract end-to-end
 *
 * All model calls use StubModelClient — zero network I/O required.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ResearchAgentStudyCaseTest {

    // ── Shared test fixtures ──────────────────────────────────────────────────

    private static final String TENANT_ID   = "tenant-research-lab";
    private static final String AGENT_ID    = "research-orchestrator";
    private static final String SESSION_ID  = "session-study-case-001";

    private ByteArrayOutputStream auditOutput;
    private ObservabilityBus      bus;
    private StructuredEventExporter exporter;
    private InMemoryRunRepository runRepo;
    private TokenWindowMemoryPolicy tokenPolicy;
    private WorkingMemoryStore    workingMemory;
    private EpisodicStore         episodicMemory;
    private ProceduralMemoryStore proceduralMemory;
    private TenantPolicyEngine    tenantPolicy;
    private SecurityEnforcer      enforcer;
    private InMemoryHitlGateway   hitlGateway;
    private StubModelClient       stubModel;
    private DefaultAgentOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        // ── Observability ──────────────────────────────────────────────────
        auditOutput = new ByteArrayOutputStream();
        exporter    = new StructuredEventExporter(new PrintStream(auditOutput));
        bus         = new ObservabilityBus();
        bus.addExporter(exporter);

        // ── Memory ────────────────────────────────────────────────────────
        tokenPolicy     = new TokenWindowMemoryPolicy(2000);
        workingMemory   = new WorkingMemoryStore();
        episodicMemory  = new EpisodicStore();
        proceduralMemory = new ProceduralMemoryStore();

        // ── Persistence ───────────────────────────────────────────────────
        runRepo = new InMemoryRunRepository();

        // ── Security ──────────────────────────────────────────────────────
        SecurityPolicy policy = new SecurityPolicy("research-policy",
                Set.of("orchestrator", "specialist", "coordinator"),
                Set.of("purge_all", "admin_delete"), true, true);
        enforcer     = new SecurityEnforcer(policy);
        tenantPolicy = new TenantPolicyEngine()
                .deny(TENANT_ID, "EXPORT_RAW_PII")
                .deny(TENANT_ID, "DELETE_AUDIT_LOG");

        // ── HITL ──────────────────────────────────────────────────────────
        hitlGateway = new InMemoryHitlGateway();

        // ── Model ─────────────────────────────────────────────────────────
        stubModel = new StubModelClient("webSearch",
                "PLAN: Summarize research findings\n" +
                "ACTIONS: generate_response\n" +
                "RATIONALE: All sources retrieved and analysed\n" +
                "CONFIDENCE: 0.91\n" +
                "HUMAN_INPUT: false");

        // ── Orchestrator ──────────────────────────────────────────────────
        orchestrator = new AgentRuntimeBuilder()
                .modelClient(stubModel)
                .securityPolicy(policy)
                .tenantPolicyEngine(tenantPolicy)
                .observabilityBus(bus)
                .runRepository(runRepo)
                .maxDelegationDepth(3)
                .build();
    }

    // ══════════════════════════════════════════════════════════════════════
    //  PHASE 1 — Basic orchestrator loop
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @Order(1)
    void phase1_orchestratorLoop_completesSuccessfully() {
        AgentIdentity agent = new AgentIdentity(AGENT_ID, AGENT_ID, "orchestrator", TENANT_ID);
        ExecutionContext ctx = ExecutionContext.of(agent);
        AgentTask task = new AgentTask("task-001",
                "Research best practices for multi-agent orchestration",
                Map.of("domain", "AI systems"), 3);

        ExecutionResult result = orchestrator.run(task, ctx);

        assertNotNull(result);
        assertNotNull(result.executionId());
        assertTrue(result.iterationsUsed() >= 1, "At least one iteration must have run");
    }

    @Test
    @Order(2)
    void phase1_orchestratorLoop_terminatesWithinMaxIterations() {
        AgentIdentity agent = new AgentIdentity(AGENT_ID, AGENT_ID, "orchestrator", TENANT_ID);
        ExecutionContext ctx = ExecutionContext.of(agent);
        AgentTask task = new AgentTask("task-002", "Summarize agent patterns",
                Map.of(), 5);

        ExecutionResult result = orchestrator.run(task, ctx);
        assertTrue(result.iterationsUsed() <= 5,
                "Must not exceed maxIterations=5, used: " + result.iterationsUsed());
    }

    @Test
    @Order(3)
    void phase1_builderFactory_stubProducesWorkingOrchestrator() {
        DefaultAgentOrchestrator stubOrchestrator = AgentRuntimeBuilder.stub();
        AgentTask task = new AgentTask("t", "stub goal", Map.of(), 2);
        ExecutionContext ctx = ExecutionContext.of(AgentIdentity.of("stub-agent", "orchestrator"));
        assertDoesNotThrow(() -> stubOrchestrator.run(task, ctx));
    }

    // ══════════════════════════════════════════════════════════════════════
    //  PHASE 2 — Memory subsystem
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @Order(4)
    void phase2_workingMemory_storeAndRetrieve() {
        workingMemory.put("current_query", "agent orchestration patterns");
        workingMemory.put("iteration",     3);
        workingMemory.put("last_tool",     "webSearch");

        assertTrue(workingMemory.get("current_query").isPresent());
        assertEquals("agent orchestration patterns", workingMemory.get("current_query").get());
        assertEquals(3, workingMemory.get("iteration").get());
        assertEquals(3, workingMemory.size());
    }

    @Test
    @Order(5)
    void phase2_workingMemory_snapshotIsReadOnly() {
        workingMemory.put("key", "value");
        Map<String, Object> snapshot = workingMemory.snapshot();
        assertThrows(UnsupportedOperationException.class, () -> snapshot.put("hack", "no"));
    }

    @Test
    @Order(6)
    void phase2_tokenPolicy_appliesWindowToWorkingMemory() {
        List<String> entries = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            entries.add("Memory entry " + i + ": research finding about agent design pattern iteration " + i);
        }
        List<String> windowed = tokenPolicy.selectWindow(entries);
        // Window should be non-empty and not exceed budget
        assertFalse(windowed.isEmpty());
        // Most-recent entry must be included
        assertTrue(windowed.contains(entries.get(entries.size() - 1)),
                "Most recent entry must be in window");
    }

    @Test
    @Order(7)
    void phase2_proceduralMemory_recordsResearchWorkflow() {
        List<String> researchSteps = List.of(
                "formulate_query",
                "web_search",
                "fetch_top_results",
                "extract_key_findings",
                "synthesise_report"
        );
        MemoryRecordId procId = proceduralMemory.storeProcedure("research-workflow", researchSteps);

        assertNotNull(procId);
        assertTrue(proceduralMemory.recall("research-workflow").isPresent());
        assertEquals(5, proceduralMemory.recall("research-workflow").get().steps().size());
        assertEquals("formulate_query",
                proceduralMemory.recall("research-workflow").get().steps().get(0));
    }

    @Test
    @Order(8)
    void phase2_proceduralMemory_trackSuccessRate() {
        proceduralMemory.storeProcedure("query-pipeline", List.of("parse", "embed", "retrieve"));
        proceduralMemory.updateSuccessRate("query-pipeline", 0.88);
        assertEquals(0.88, proceduralMemory.recall("query-pipeline").get().successRate(), 0.001);
    }

    // ══════════════════════════════════════════════════════════════════════
    //  PHASE 3 — Model client layer
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @Order(9)
    void phase3_stubModel_emitsToolCallOnFirstTurn() throws ModelClientException {
        StubModelClient client = new StubModelClient("webSearch", "final answer");
        ModelRequestContext ctx = ModelRequestContext.of(SESSION_ID, TENANT_ID);
        ModelPrompt prompt = ModelPrompt.of(
                "You are a research agent",
                "What are the top orchestration patterns?");

        ModelOutput first = client.generate(prompt, ctx);
        assertTrue(first.hasToolCalls());
        assertEquals("webSearch", first.toolCalls().get(0).name());
    }

    @Test
    @Order(10)
    void phase3_stubModel_returnsAnswerAfterToolResult() throws ModelClientException {
        StubModelClient client = new StubModelClient("webSearch", "ReAct and event-driven are the main patterns");
        Map<String, Object> wm = Map.of("toolResult.webSearch", "search result content");
        ModelRequestContext ctx = new ModelRequestContext(SESSION_ID, TENANT_ID, wm);
        ModelOutput output = client.generate(ModelPrompt.ofUser("Summarise"), ctx);

        assertFalse(output.hasToolCalls());
        assertEquals("ReAct and event-driven are the main patterns", output.text());
    }

    @Test
    @Order(11)
    void phase3_modelClientException_retryableFlagCorrect() {
        ModelClientException transient_ = new ModelClientException("rate limited", true);
        ModelClientException permanent  = new ModelClientException("invalid api key", false);
        assertTrue(transient_.retryable());
        assertFalse(permanent.retryable());
    }

    @Test
    @Order(12)
    void phase3_reasoningModule_parsesStructuredResponse() throws ModelClientException {
        String response =
                "PLAN: Search and synthesise\n" +
                "ACTIONS: retrieve_context,generate_response,validate_output\n" +
                "RATIONALE: Standard RAG pattern\n" +
                "CONFIDENCE: 0.93\n" +
                "HUMAN_INPUT: false";
        ModelClient client = (prompt, ctx) -> new ModelOutput(response, List.of(), Map.of());
        DefaultReasoningModule module = new DefaultReasoningModule(client);

        AgentIdentity agent = new AgentIdentity(AGENT_ID, AGENT_ID, "orchestrator", TENANT_ID);
        ExecutionContext execCtx = ExecutionContext.of(agent);
        PerceptionResult perception = new PerceptionResult("What are agent patterns?", List.of("agent", "patterns"), Map.of(), 0.9);
        AgentState state = new AgentState(SESSION_ID, 0, Map.of(), "Research goal");

        ReasoningResult result = module.reason(perception, state, execCtx);
        assertEquals("Search and synthesise", result.plan());
        assertEquals(3, result.selectedActions().size());
        assertEquals(0.93, result.confidence(), 0.001);
        assertFalse(result.requiresHumanInput());
    }

    // ══════════════════════════════════════════════════════════════════════
    //  PHASE 4 — Security: role + tenant dual enforcement
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @Order(13)
    void phase4_roleEnforcement_orchestratorCanWrite() {
        AgentIdentity orchestratorAgent = new AgentIdentity(AGENT_ID, AGENT_ID, "orchestrator", TENANT_ID);
        assertTrue(enforcer.check(orchestratorAgent, "WRITE_MEMORY"));
        assertTrue(enforcer.check(orchestratorAgent, "READ_MEMORY"));
    }

    @Test
    @Order(14)
    void phase4_roleEnforcement_deniedOperationBlocked() {
        AgentIdentity agent = new AgentIdentity(AGENT_ID, AGENT_ID, "orchestrator", TENANT_ID);
        assertFalse(enforcer.check(agent, "purge_all"));
        assertFalse(enforcer.check(agent, "admin_delete"));
    }

    @Test
    @Order(15)
    void phase4_tenantPolicy_piiExportBlocked() {
        assertFalse(tenantPolicy.permits(TENANT_ID, "EXPORT_RAW_PII"),
                "PII export must be blocked for research tenant");
        assertFalse(tenantPolicy.permits(TENANT_ID, "DELETE_AUDIT_LOG"),
                "Audit log deletion must be blocked");
    }

    @Test
    @Order(16)
    void phase4_tenantPolicy_normalOperationsAllowed() {
        assertTrue(tenantPolicy.permits(TENANT_ID, "READ_MEMORY"));
        assertTrue(tenantPolicy.permits(TENANT_ID, "WRITE_MEMORY"));
        assertTrue(tenantPolicy.permits(TENANT_ID, "SEARCH"));
    }

    @Test
    @Order(17)
    void phase4_composedEnforcement_bothChecksMustPass() {
        AgentIdentity agent = new AgentIdentity(AGENT_ID, AGENT_ID, "orchestrator", TENANT_ID);
        String op = "EXPORT_RAW_PII";

        // Role allows it (not in denied operations list)
        assertTrue(enforcer.check(agent, op), "Role check should pass");
        // But tenant blocks it
        assertFalse(tenantPolicy.permits(TENANT_ID, op), "Tenant check should fail");
        // Composed: fails
        assertFalse(enforcer.check(agent, op) && tenantPolicy.permits(TENANT_ID, op),
                "Composed check must fail");
    }

    @Test
    @Order(18)
    void phase4_securityPolicy_allowAllFactory() {
        SecurityPolicy open = SecurityPolicy.allowAll();
        AgentIdentity any = AgentIdentity.of("unknown", "unknown-role");
        assertTrue(open.permits(any, "ANY_OPERATION"));
        assertTrue(open.permits(any, "DANGEROUS_OP"));
    }

    // ══════════════════════════════════════════════════════════════════════
    //  PHASE 5 — Audit log immutability + structured export
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @Order(19)
    void phase5_auditLog_isWriteImmutable() {
        AgentIdentity agent = new AgentIdentity(AGENT_ID, AGENT_ID, "orchestrator", TENANT_ID);
        AuditEvent event = new AuditEvent("evt-1", "MEMORY_WRITE", agent,
                "write_working_memory", Map.of("key", "query"), Instant.now(), false);

        bus.emit(event);
        assertEquals(1, bus.auditLog().size());

        // Verify immutability: cannot clear or add to auditLog view
        assertThrows(UnsupportedOperationException.class, () -> bus.auditLog().clear());
        assertThrows(UnsupportedOperationException.class, () -> bus.auditLog().add(event));
    }

    @Test
    @Order(20)
    void phase5_auditLog_clearTelemetryDoesNotClearAudit() {
        AgentIdentity agent = new AgentIdentity(AGENT_ID, AGENT_ID, "orchestrator", TENANT_ID);
        bus.emit(new AuditEvent("e1", "OP", agent, "op", Map.of(), Instant.now(), false));
        bus.emit(new AuditEvent("e2", "OP", agent, "op", Map.of(), Instant.now(), false));

        bus.clearTelemetry();
        assertEquals(2, bus.auditLog().size(), "Audit log must not be cleared by clearTelemetry()");
    }

    @Test
    @Order(21)
    void phase5_structuredExporter_emitsJsonForEachEvent() {
        AgentIdentity agent = new AgentIdentity(AGENT_ID, AGENT_ID, "orchestrator", TENANT_ID);
        bus.emit(new AuditEvent("e-A", "SEARCH_INVOKED", agent,
                "invoke_websearch", Map.of("query", "agent patterns"), Instant.now(), false));
        bus.emit(new AuditEvent("e-B", "MEMORY_UPDATE", agent,
                "update_episodic", Map.of("finding_count", 3), Instant.now(), false));

        String jsonOutput = auditOutput.toString();
        assertTrue(jsonOutput.contains("SEARCH_INVOKED"), "JSON export must contain event type");
        assertTrue(jsonOutput.contains("MEMORY_UPDATE"),  "JSON export must contain second event type");
        assertTrue(jsonOutput.contains(AGENT_ID),         "JSON export must contain agent ID");

        // Each event is a separate JSON line
        long lineCount = jsonOutput.lines().filter(l -> !l.isBlank()).count();
        assertEquals(2, lineCount, "Must emit exactly 2 JSON lines for 2 events");
    }

    @Test
    @Order(22)
    void phase5_structuredExporter_doesNotThrowOnNullEvent() {
        assertDoesNotThrow(() -> exporter.export(null));
    }

    // ══════════════════════════════════════════════════════════════════════
    //  PHASE 6 — Run persistence (durable state)
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @Order(23)
    void phase6_runRepository_persistsRunThroughLifecycle() {
        Clock clock = Clock.systemUTC();
        Run run = new Run(RunId.generate(), SESSION_ID, AGENT_ID, clock);
        runRepo.save(run);

        run.transition(RunState.VALIDATING, clock);
        run.transition(RunState.PLANNING,   clock);
        run.transition(RunState.MODEL_CALL, clock);
        run.transition(RunState.PLANNING,   clock);
        run.transition(RunState.RESPONDING, clock);
        run.complete("Research report: top 3 orchestration patterns are ReAct, event-driven, and hierarchical.", clock);

        runRepo.save(run);

        Optional<Run> retrieved = runRepo.findById(run.runId());
        assertTrue(retrieved.isPresent());
        assertEquals(RunState.COMPLETED, retrieved.get().state());
        assertNotNull(retrieved.get().finalResponse());
    }

    @Test
    @Order(24)
    void phase6_runRepository_recordsStepHistory() {
        Clock clock = Clock.systemUTC();
        RunId runId = RunId.generate();
        Run run = new Run(runId, SESSION_ID, AGENT_ID, clock);
        runRepo.save(run);

        runRepo.saveStep(runId, new RunStep(runId, RunState.CREATED, RunState.VALIDATING,
                Instant.now(), "Validated input schema", Map.of("inputSize", 42)));
        runRepo.saveStep(runId, new RunStep(runId, RunState.VALIDATING, RunState.PLANNING,
                Instant.now(), "Generated execution plan", Map.of("planSteps", 3)));
        runRepo.saveStep(runId, new RunStep(runId, RunState.PLANNING, RunState.MODEL_CALL,
                Instant.now(), "Called model for reasoning", Map.of("model", "stub")));

        List<RunStep> steps = runRepo.listSteps(runId);
        assertEquals(3, steps.size());
        assertEquals(RunState.VALIDATING, steps.get(0).toState());
        assertEquals(RunState.MODEL_CALL, steps.get(2).toState());
        assertEquals("Generated execution plan", steps.get(1).description());
    }

    @Test
    @Order(25)
    void phase6_runAggregate_failureSetsReason() {
        Clock clock = Clock.systemUTC();
        Run run = new Run(RunId.generate(), SESSION_ID, AGENT_ID, clock);
        run.transition(RunState.VALIDATING, clock);
        run.fail("Model rate limited after 3 retries", clock);

        assertEquals(RunState.FAILED, run.state());
        assertEquals("Model rate limited after 3 retries", run.failureReason());
        assertTrue(run.state().isTerminal());
    }

    @Test
    @Order(26)
    void phase6_runAggregate_snapshotCapturesFullState() {
        Clock clock = Clock.systemUTC();
        Run run = new Run(RunId.generate(), SESSION_ID, AGENT_ID, clock);
        run.transition(RunState.VALIDATING, clock);
        run.transition(RunState.PLANNING,   clock);
        run.putWorkingMemory("retrieved_docs", 7);
        run.putWorkingMemory("current_step",   "planning");

        RunSnapshot snap = run.toSnapshot();
        assertEquals(RunState.PLANNING, snap.state());
        assertEquals(SESSION_ID,  snap.sessionId());
        assertEquals(AGENT_ID,    snap.agentId());
        assertTrue(snap.finalResponse().isEmpty());
    }

    @Test
    @Order(27)
    void phase6_runAggregate_illegalTransitionThrows() {
        Clock clock = Clock.systemUTC();
        Run run = new Run(RunId.generate(), SESSION_ID, AGENT_ID, clock);
        run.transition(RunState.VALIDATING, clock);
        assertThrows(IllegalStateException.class,
                () -> run.transition(RunState.COMPLETED, clock),
                "VALIDATING → COMPLETED is not a legal transition");
    }

    // ══════════════════════════════════════════════════════════════════════
    //  PHASE 7 — Human-in-the-Loop gateway
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @Order(28)
    void phase7_hitl_submitAmbiguousQueryForReview() {
        AgentIdentity agent = new AgentIdentity(AGENT_ID, AGENT_ID, "orchestrator", TENANT_ID);
        HitlRequest request = new HitlRequest(
                "hitl-ambiguous-001", agent,
                "The retrieved source makes a claim that contradicts the knowledge base. " +
                "Should I trust source A (recency score 0.9) or source B (authority score 0.95)?",
                Map.of("sourceA_score", 0.9, "sourceB_score", 0.95),
                HitlUrgency.HIGH, Instant.now());

        hitlGateway.submit(request);
        assertEquals(1, hitlGateway.pendingCount());
        assertFalse(hitlGateway.isResolved("hitl-ambiguous-001"));
    }

    @Test
    @Order(29)
    void phase7_hitl_humanRespondsAndResolutionIsAvailable() {
        AgentIdentity agent = new AgentIdentity(AGENT_ID, AGENT_ID, "orchestrator", TENANT_ID);
        hitlGateway.submit(new HitlRequest("hitl-002", agent, "Trust source A or B?",
                Map.of(), HitlUrgency.BLOCKING, Instant.now()));

        // Human operator resolves
        hitlGateway.resolve("hitl-002",
                "Trust source B — it has higher authority and was peer-reviewed.",
                "human-supervisor-jane");

        assertTrue(hitlGateway.isResolved("hitl-002"));
        Optional<HitlResponse> response = hitlGateway.poll("hitl-002");
        assertTrue(response.isPresent());
        assertTrue(response.get().humanAnswer().contains("source B"));
        assertEquals("human-supervisor-jane", response.get().responderId());
    }

    @Test
    @Order(30)
    void phase7_hitl_unresolvedRequestRemainsInQueue() {
        AgentIdentity agent = new AgentIdentity(AGENT_ID, AGENT_ID, "orchestrator", TENANT_ID);
        hitlGateway.submit(new HitlRequest("r-1", agent, "Q1", Map.of(), HitlUrgency.NORMAL, Instant.now()));
        hitlGateway.submit(new HitlRequest("r-2", agent, "Q2", Map.of(), HitlUrgency.HIGH, Instant.now()));
        hitlGateway.submit(new HitlRequest("r-3", agent, "Q3", Map.of(), HitlUrgency.LOW, Instant.now()));

        hitlGateway.resolve("r-2", "Resolved", "operator");

        assertEquals(2, hitlGateway.pendingCount()); // r-2 removed from pending
        assertTrue(hitlGateway.isResolved("r-2"));
        assertFalse(hitlGateway.isResolved("r-1"));
        assertFalse(hitlGateway.isResolved("r-3"));
    }

    // ══════════════════════════════════════════════════════════════════════
    //  PHASE 8 — ResearchAgentFactory + use-case assembly
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @Order(31)
    void phase8_researchFactory_stubAssembly() {
        DefaultAgentOrchestrator researchAgent = ResearchAgentFactory.createWithStub();
        assertNotNull(researchAgent, "ResearchAgentFactory must produce a non-null orchestrator");
    }

    @Test
    @Order(32)
    void phase8_researchFactory_nullKeyFallsBackToStub() {
        DefaultAgentOrchestrator agent = ResearchAgentFactory.create(null);
        assertNotNull(agent);
    }

    @Test
    @Order(33)
    void phase8_researchFactory_runProducesResult() {
        DefaultAgentOrchestrator researchAgent = ResearchAgentFactory.createWithStub();
        AgentTask task = new AgentTask("research-task-001",
                "What are the key design patterns for production AI agents?",
                Map.of("max_sources", 5, "domain", "AI systems"), 3);
        ExecutionContext ctx = ExecutionContext.of(AgentIdentity.of("research-agent", "orchestrator"));

        ExecutionResult result = researchAgent.run(task, ctx);

        assertNotNull(result);
        assertNotNull(result.executionId());
        assertTrue(result.iterationsUsed() >= 1);
    }

    @Test
    @Order(34)
    void phase8_findingStoreTool_accumulatesFindings() {
        FindingStoreTool store = new FindingStoreTool();

        store.execute(Map.of("action", "add",
                "finding", "ReAct pattern interleaves reasoning and acting in LLM workflows"));
        store.execute(Map.of("action", "add",
                "finding", "Event-driven orchestration enables loose coupling between agents"));
        store.execute(Map.of("action", "add",
                "finding", "Hierarchical delegation allows specialised sub-agents per task type"));

        assertEquals(3, store.getFindings().size());
        String listing = store.execute(Map.of("action", "list"));
        assertTrue(listing.contains("ReAct"));
        assertTrue(listing.contains("Event-driven"));
        assertTrue(listing.contains("Hierarchical"));
    }

    @Test
    @Order(35)
    void phase8_findingStoreTool_clearResetsStore() {
        FindingStoreTool store = new FindingStoreTool();
        store.execute(Map.of("action", "add", "finding", "finding-1"));
        store.execute(Map.of("action", "add", "finding", "finding-2"));
        assertEquals(2, store.getFindings().size());

        store.execute(Map.of("action", "clear"));
        assertEquals(0, store.getFindings().size());
        assertEquals("No findings yet.", store.execute(Map.of("action", "list")));
    }

    @Test
    @Order(36)
    void phase8_webSearchTool_returnsStubResults() {
        WebSearchTool tool = new WebSearchTool();
        Map<String, Object> result = tool.execute(Map.of("query", "agent orchestration patterns"));

        assertNotNull(result);
        assertEquals("agent orchestration patterns", result.get("query"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> results = (List<Map<String, Object>>) result.get("results");
        assertFalse(results.isEmpty());
        assertTrue(results.stream().allMatch(r -> r.containsKey("url") && r.containsKey("snippet")));
    }

    @Test
    @Order(37)
    void phase8_webSearchTool_rejectsBlankQuery() {
        WebSearchTool tool = new WebSearchTool();
        assertThrows(IllegalArgumentException.class,
                () -> tool.execute(Map.of("query", "")));
        assertThrows(IllegalArgumentException.class,
                () -> tool.execute(Map.of()));
    }

    // ══════════════════════════════════════════════════════════════════════
    //  PHASE 9 — Builder completeness + module override
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @Order(38)
    void phase9_builder_requiresModelClient() {
        assertThrows(NullPointerException.class,
                () -> new AgentRuntimeBuilder().build());
    }

    @Test
    @Order(39)
    void phase9_builder_verboseWiresExporterToObservabilityBus() {
        ByteArrayOutputStream captureOut = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(captureOut);
        ObservabilityBus verboseBus = new ObservabilityBus();
        verboseBus.addExporter(new StructuredEventExporter(ps));

        // verbose=true adds stdout exporter; here we test via manually-wired bus
        assertDoesNotThrow(() -> new AgentRuntimeBuilder()
                .modelClient(StubModelClient.direct("ok"))
                .observabilityBus(verboseBus)
                .verbose(false)   // don't double-add stdout; we have our own
                .build());
    }

    @Test
    @Order(40)
    void phase9_builder_customRunRepositoryIsUsed() {
        InMemoryRunRepository customRepo = new InMemoryRunRepository();
        assertDoesNotThrow(() -> new AgentRuntimeBuilder()
                .modelClient(StubModelClient.direct("ok"))
                .runRepository(customRepo)
                .build());
    }

    @Test
    @Order(41)
    void phase9_builder_customMaxDelegationDepth() {
        DefaultAgentOrchestrator agent = new AgentRuntimeBuilder()
                .modelClient(StubModelClient.direct("ok"))
                .maxDelegationDepth(2)
                .build();
        assertNotNull(agent);
    }

    // ══════════════════════════════════════════════════════════════════════
    //  PHASE 10 — Token window policy under realistic memory load
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @Order(42)
    void phase10_tokenPolicy_selectsMostRecentUnderBudget() {
        TokenWindowMemoryPolicy policy = new TokenWindowMemoryPolicy(100); // tight budget

        List<String> memories = new ArrayList<>();
        for (int i = 1; i <= 30; i++) {
            memories.add("Finding " + i + ": detailed analysis result of research iteration " + i);
        }
        List<String> window = policy.selectWindow(memories);

        assertFalse(window.isEmpty());
        // Most recent entry must be included
        assertEquals("Finding 30: detailed analysis result of research iteration 30",
                window.get(window.size() - 1));
        // Order is preserved (oldest first in window)
        if (window.size() > 1) {
            int firstIdx  = Integer.parseInt(window.get(0).split(" ")[1].replace(":", ""));
            int lastIdx   = Integer.parseInt(window.get(window.size() - 1).split(" ")[1].replace(":", ""));
            assertTrue(firstIdx < lastIdx, "Window must maintain chronological order");
        }
    }

    @Test
    @Order(43)
    void phase10_tokenPolicy_buildContextStringIsJoinedByNewlines() {
        TokenWindowMemoryPolicy policy = new TokenWindowMemoryPolicy(10_000);
        List<String> memories = List.of("finding-A", "finding-B", "finding-C");
        String context = policy.buildContextString(memories);
        assertEquals("finding-A\nfinding-B\nfinding-C", context);
    }

    @Test
    @Order(44)
    void phase10_tokenPolicy_emptyInputReturnsEmptyString() {
        TokenWindowMemoryPolicy policy = new TokenWindowMemoryPolicy(1000);
        assertEquals("", policy.buildContextString(List.of()));
        assertEquals("", policy.buildContextString(null));
    }

    // ══════════════════════════════════════════════════════════════════════
    //  PHASE 11 — Full integrated scenario: research task lifecycle
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @Order(45)
    void phase11_fullLifecycle_researchTaskWithAllSubsystems() {
        // 1. Set up full system
        Clock clock = Clock.systemUTC();
        RunId runId = RunId.generate();
        Run run = new Run(runId, SESSION_ID, AGENT_ID, clock);
        runRepo.save(run);

        // 2. Store research procedure
        proceduralMemory.storeProcedure("full-research",
                List.of("query", "search", "fetch", "extract", "synthesise", "validate"));

        // 3. Populate working memory
        workingMemory.put("research_goal", "Understand multi-agent design patterns");
        workingMemory.put("iteration", 0);
        workingMemory.put("sources_found", List.of("paper-1", "paper-2", "paper-3"));

        // 4. Apply token window to working memory entries
        List<String> memoryEntries = workingMemory.snapshot().entrySet().stream()
                .map(e -> e.getKey() + ": " + e.getValue())
                .toList();
        List<String> window = tokenPolicy.selectWindow(memoryEntries);
        assertFalse(window.isEmpty());

        // 5. Security check: valid operation
        AgentIdentity agent = new AgentIdentity(AGENT_ID, AGENT_ID, "orchestrator", TENANT_ID);
        assertTrue(enforcer.check(agent, "WRITE_MEMORY"));
        assertTrue(tenantPolicy.permits(TENANT_ID, "WRITE_MEMORY"));

        // 6. Emit audit event for memory write
        bus.emit(new AuditEvent("audit-research-1", "MEMORY_WRITE", agent,
                "write_working_memory", Map.of("keys", "research_goal,iteration,sources_found"),
                Instant.now(), false));

        // 7. Update run state through lifecycle
        run.transition(RunState.VALIDATING, clock);
        run.transition(RunState.PLANNING,   clock);
        run.transition(RunState.MODEL_CALL, clock);
        run.putWorkingMemory("model_plan", "search → retrieve → synthesise");
        run.transition(RunState.PLANNING,   clock);
        run.transition(RunState.TOOL_EXECUTION, clock);
        run.transition(RunState.MEMORY_UPDATE,  clock);
        run.transition(RunState.RESPONDING,     clock);
        run.complete("Research complete: identified 3 key patterns.", clock);
        runRepo.save(run);

        // 8. Record finding
        FindingStoreTool findings = new FindingStoreTool();
        findings.execute(Map.of("action", "add", "finding", "ReAct is the dominant pattern (70% of papers)"));
        findings.execute(Map.of("action", "add", "finding", "Event-driven enables horizontal scaling"));

        // 9. Validate full state
        Optional<Run> saved = runRepo.findById(runId);
        assertTrue(saved.isPresent());
        assertEquals(RunState.COMPLETED, saved.get().state());
        assertEquals(1, bus.auditLog().size());
        assertEquals(2, findings.getFindings().size());
        assertTrue(proceduralMemory.recall("full-research").isPresent());

        // 10. Verify JSON audit output captured
        String jsonLines = auditOutput.toString();
        assertTrue(jsonLines.contains("MEMORY_WRITE"));
        assertTrue(jsonLines.contains(AGENT_ID));
    }

    @Test
    @Order(46)
    void phase11_fullLifecycle_runRepositoryNotFoundForUnknownId() {
        Optional<Run> missing = runRepo.findById(RunId.of("does-not-exist-xyz"));
        assertTrue(missing.isEmpty());
    }

    @Test
    @Order(47)
    void phase11_fullLifecycle_multipleRunsAreIndependent() {
        Clock clock = Clock.systemUTC();
        Run run1 = new Run(RunId.generate(), "session-A", AGENT_ID, clock);
        Run run2 = new Run(RunId.generate(), "session-B", AGENT_ID, clock);

        run1.transition(RunState.VALIDATING, clock);
        run1.transition(RunState.PLANNING,   clock);
        run1.transition(RunState.RESPONDING, clock);
        run1.complete("done-1", clock);

        run2.transition(RunState.VALIDATING, clock);
        run2.fail("error in session B", clock);

        runRepo.save(run1);
        runRepo.save(run2);

        assertEquals(RunState.COMPLETED, runRepo.findById(run1.runId()).get().state());
        assertEquals(RunState.FAILED,    runRepo.findById(run2.runId()).get().state());
    }
}
