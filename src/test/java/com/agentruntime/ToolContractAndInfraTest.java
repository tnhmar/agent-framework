package com.agentruntime;

import com.agentruntime.core.enums.AgentStatus;
import com.agentruntime.core.enums.ToolCategory;
import com.agentruntime.core.valueobjects.*;
import com.agentruntime.memory.checkpoint.*;
import com.agentruntime.modelclient.StubModelClient;
import com.agentruntime.observability.ObservabilityBus;
import com.agentruntime.orchestrator.*;
import com.agentruntime.orchestrator.action.*;
import com.agentruntime.orchestrator.action.validation.ActionValidationPipeline;
import com.agentruntime.orchestrator.reasoning.ReasoningResult;
import com.agentruntime.runtime.builder.AgentRuntimeBuilder;
import com.agentruntime.security.SecurityEnforcer;
import com.agentruntime.security.SecurityPolicy;
import com.agentruntime.statemanager.StateManager;
import com.agentruntime.tooling.registry.*;
import org.junit.jupiter.api.*;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for:
 *   G-09  ToolDefinition full contract (idempotent, outputSchema, errorTaxonomy, freshnessProfile)
 *   G-12  StateManager emits PHASE_TRANSITION audit events
 *   G-13  DefaultActionModule triggers pre-action checkpoint for irreversible actions
 */
class ToolContractAndInfraTest {

    // ── G-09: ToolDefinition full contract ────────────────────────────────────

    @Test void toolDefinition_fullConstructor_allFieldsStored() {
        ToolDefinition def = new ToolDefinition(
                "webSearch", "webSearch", "Search the web",
                ToolCategory.DATA_RETRIEVAL,
                Map.of("query", "string"),
                Map.of("results", "List<Map>", "count", "int"),
                List.of("RATE_LIMITED", "TIMEOUT", "QUOTA_EXCEEDED"),
                true, "real-time", false);

        assertEquals("webSearch",                        def.toolId());
        assertEquals(Map.of("results", "List<Map>", "count", "int"), def.outputSchema());
        assertEquals(List.of("RATE_LIMITED", "TIMEOUT", "QUOTA_EXCEEDED"), def.errorTaxonomy());
        assertTrue(def.idempotent());
        assertEquals("real-time",                        def.freshnessProfile());
    }

    @Test void toolDefinition_of_factory_setsDefaults() {
        ToolDefinition def = ToolDefinition.of(
                "myTool", "myTool", "desc",
                ToolCategory.COMPUTATION, Map.of("x", "int"), true);

        assertTrue(def.outputSchema().isEmpty());
        assertTrue(def.errorTaxonomy().isEmpty());
        assertFalse(def.idempotent());
        assertEquals("unknown", def.freshnessProfile());
    }

    @Test void toolDefinition_idempotentFlagDistinguishesTools() {
        ToolDefinition idempotentTool = new ToolDefinition(
                "read-data", "ReadData", "Read only",
                ToolCategory.DATA_RETRIEVAL, Map.of(), Map.of(),
                List.of(), true, "cached-1m", false);
        ToolDefinition destructiveTool = new ToolDefinition(
                "delete-record", "DeleteRecord", "Delete a record",
                ToolCategory.ENTERPRISE, Map.of("id", "string"), Map.of(),
                List.of("NOT_FOUND", "PERMISSION_DENIED"), false, "real-time", false);

        assertTrue(idempotentTool.idempotent(),    "Read tool must be idempotent");
        assertFalse(destructiveTool.idempotent(),  "Delete tool must not be idempotent");
    }

    @Test void toolDefinition_errorTaxonomy_isImmutable() {
        ToolDefinition def = new ToolDefinition(
                "t1", "t1", "d", ToolCategory.DATA_RETRIEVAL,
                Map.of(), Map.of(), List.of("ERR1", "ERR2"), true, "real-time", false);
        assertThrows(UnsupportedOperationException.class,
                () -> def.errorTaxonomy().add("ERR3"));
    }

    @Test void toolDefinition_freshnessProfiles_representTimeCharacteristics() {
        ToolDefinition realTime  = new ToolDefinition("t1", "t1", "d", ToolCategory.DATA_RETRIEVAL,
                Map.of(), Map.of(), List.of(), true, "real-time", false);
        ToolDefinition cached5m  = new ToolDefinition("t2", "t2", "d", ToolCategory.DATA_RETRIEVAL,
                Map.of(), Map.of(), List.of(), true, "cached-5m", false);
        ToolDefinition dailyBatch = new ToolDefinition("t3", "t3", "d", ToolCategory.DATA_RETRIEVAL,
                Map.of(), Map.of(), List.of(), true, "daily-batch", false);

        assertEquals("real-time",   realTime.freshnessProfile());
        assertEquals("cached-5m",   cached5m.freshnessProfile());
        assertEquals("daily-batch", dailyBatch.freshnessProfile());
    }

    @Test void toolRegistry_storesAndRetrievesFullContract() {
        ToolRegistry registry = ToolRegistry.empty();
        ToolDefinition def = new ToolDefinition(
                "portfolioFetch", "PortfolioFetch", "Fetch portfolio data",
                ToolCategory.ENTERPRISE,
                Map.of("portfolioId", "string", "asOf", "date"),
                Map.of("positions", "List<Position>", "nav", "decimal"),
                List.of("NOT_FOUND", "ACCESS_DENIED", "STALE_DATA"),
                true, "cached-5m", false);

        registry.register(def);

        ToolDefinition found = registry.find("portfolioFetch").get();
        assertEquals(List.of("NOT_FOUND", "ACCESS_DENIED", "STALE_DATA"),
                found.errorTaxonomy());
        assertEquals("cached-5m", found.freshnessProfile());
        assertTrue(found.idempotent());
    }

    // ── G-12: StateManager PHASE_TRANSITION audit events ─────────────────────

    @Test void stateManager_emitsPhaseTransitionOnUpdateStatus() {
        ObservabilityBus bus = new ObservabilityBus();
        StateManager sm = new StateManager(bus);
        AgentIdentity agent = new AgentIdentity("agent-1", "Agent", "orchestrator", "default");
        sm.initialize("exec-1", agent);
        sm.updateStatus("exec-1", AgentStatus.PERCEIVING);

        assertFalse(bus.auditLog().isEmpty(), "Phase transition must emit audit event");
        boolean hasTransition = bus.auditLog().stream()
                .anyMatch(e -> "PHASE_TRANSITION".equals(e.eventType()));
        assertTrue(hasTransition, "Audit log must contain PHASE_TRANSITION event");
    }

    @Test void stateManager_phaseTransitionContainsFromAndTo() {
        ObservabilityBus bus = new ObservabilityBus();
        StateManager sm = new StateManager(bus);
        AgentIdentity agent = new AgentIdentity("agent-2", "A", "orchestrator", "default");
        sm.initialize("exec-2", agent);
        sm.updateStatus("exec-2", AgentStatus.REASONING);

        var events = bus.auditLog().stream()
                .filter(e -> "PHASE_TRANSITION".equals(e.eventType()))
                .filter(e -> "REASONING".equals(e.details().get("toPhase")))
                .toList();
        assertFalse(events.isEmpty(), "Must have PHASE_TRANSITION event to REASONING");
        assertEquals("IDLE", events.get(0).details().get("fromPhase"));
    }

    @Test void stateManager_multipleTransitionsAllEmitted() {
        ObservabilityBus bus = new ObservabilityBus();
        StateManager sm = new StateManager(bus);
        AgentIdentity agent = new AgentIdentity("a3", "A", "orchestrator", "default");
        sm.initialize("exec-3", agent);
        sm.updateStatus("exec-3", AgentStatus.PERCEIVING);
        sm.updateStatus("exec-3", AgentStatus.REASONING);
        sm.updateStatus("exec-3", AgentStatus.ACTING);
        sm.updateStatus("exec-3", AgentStatus.REFLECTING);
        sm.updateStatus("exec-3", AgentStatus.TERMINATING);

        long transitions = bus.auditLog().stream()
                .filter(e -> "PHASE_TRANSITION".equals(e.eventType())).count();
        assertEquals(5, transitions, "5 updateStatus calls → 5 PHASE_TRANSITION events");
    }

    @Test void stateManager_noArgConstructor_doesNotThrow() {
        // No-arg constructor creates its own ObservabilityBus — no NPE
        StateManager sm = new StateManager();
        AgentIdentity agent = new AgentIdentity("a4", "A", "orchestrator", "default");
        assertDoesNotThrow(() -> {
            sm.initialize("exec-4", agent);
            sm.updateStatus("exec-4", AgentStatus.PERCEIVING);
        });
    }

    @Test void stateManager_orchestratorEmitsPhaseTransitions() {
        // When orchestrator runs, StateManager emits phase transitions
        ObservabilityBus bus = new ObservabilityBus();
        StateManager sm = new StateManager(bus);

        DefaultAgentOrchestrator orch = new AgentRuntimeBuilder()
                .modelClient(StubModelClient.direct("done"))
                .stateManager(sm)
                .observabilityBus(bus)
                .build();

        AgentIdentity agent = AgentIdentity.of("test-agent", "orchestrator");
        ExecutionContext ctx = ExecutionContext.of(agent);
        AgentTask task = new AgentTask("t1", "test", Map.of(), 2);
        orch.run(task, ctx);

        long transitions = bus.auditLog().stream()
                .filter(e -> "PHASE_TRANSITION".equals(e.eventType())).count();
        assertTrue(transitions >= 5,
                "Full orchestrator run must emit >= 5 PHASE_TRANSITION events, got: " + transitions);
    }

    // ── G-13: Pre-action checkpoint for irreversible actions ─────────────────

    @Test void actionModule_checkpointsBeforeIrreversibleAction() {
        CheckpointStore store = new CheckpointStore();
        SecurityEnforcer enforcer = new SecurityEnforcer(SecurityPolicy.allowAll());
        DefaultActionModule module = new DefaultActionModule(
                ActionValidationPipeline.defaultPipeline(enforcer), store);

        AgentIdentity agent = AgentIdentity.of("agent", "orchestrator");
        ExecutionContext ctx = ExecutionContext.of(agent);
        ReasoningResult reasoning = new ReasoningResult(
                "Send monthly report",
                List.of("send_notification"),  // "send" is in NON_IDEMPOTENT_SIGNALS
                "Notification step required",
                0.9, false);

        module.execute(reasoning, ctx);

        // CheckpointStore should have been triggered
        String taskId = ctx.executionId() + ":send_notification";
        assertTrue(store.latest(taskId).isPresent(),
                "Pre-action checkpoint must be saved before irreversible 'send' action");
    }

    @Test void actionModule_noCheckpointForIdempotentAction() {
        CheckpointStore store = new CheckpointStore();
        SecurityEnforcer enforcer = new SecurityEnforcer(SecurityPolicy.allowAll());
        DefaultActionModule module = new DefaultActionModule(
                ActionValidationPipeline.defaultPipeline(enforcer), store);

        AgentIdentity agent = AgentIdentity.of("agent", "orchestrator");
        ExecutionContext ctx = ExecutionContext.of(agent);
        ReasoningResult reasoning = new ReasoningResult(
                "Retrieve context",
                List.of("retrieve_context"),   // "retrieve" has no irreversible signal
                "Read-only operation",
                0.9, false);

        module.execute(reasoning, ctx);

        // No checkpoint expected for read-only action
        String taskId = ctx.executionId() + ":retrieve_context";
        assertTrue(store.latest(taskId).isEmpty(),
                "No pre-action checkpoint for idempotent 'retrieve_context' action");
    }

    @Test void actionModule_checksMultipleNonIdempotentSignals() {
        // All these should trigger checkpointing
        String[] irreversible = { "delete_record", "send_email", "publish_event",
                                  "create_order",  "submit_form", "commit_changes",
                                  "pay_invoice",   "issue_cert",  "write_file" };

        CheckpointStore store = new CheckpointStore();
        SecurityEnforcer enforcer = new SecurityEnforcer(SecurityPolicy.allowAll());
        DefaultActionModule module = new DefaultActionModule(
                ActionValidationPipeline.defaultPipeline(enforcer), store);
        AgentIdentity agent = AgentIdentity.of("agent", "orchestrator");
        ExecutionContext ctx = ExecutionContext.of(agent);

        for (String action : irreversible) {
            ReasoningResult r = new ReasoningResult("plan", List.of(action), "r", 0.9, false);
            module.execute(r, ctx);
            String taskId = ctx.executionId() + ":" + action;
            assertTrue(store.latest(taskId).isPresent(),
                    "Expected checkpoint for irreversible action: " + action);
        }
    }

    @Test void actionModule_nullCheckpointStore_doesNotThrow() {
        SecurityEnforcer enforcer = new SecurityEnforcer(SecurityPolicy.allowAll());
        DefaultActionModule module = new DefaultActionModule(
                ActionValidationPipeline.defaultPipeline(enforcer));  // no checkpointStore

        AgentIdentity agent = AgentIdentity.of("agent", "orchestrator");
        ExecutionContext ctx = ExecutionContext.of(agent);
        ReasoningResult reasoning = new ReasoningResult(
                "Delete all", List.of("delete_all"), "cleanup", 0.9, false);

        // Must not throw even without a checkpoint store
        assertDoesNotThrow(() -> module.execute(reasoning, ctx));
    }

    @Test void checkpointStore_savedBeforeAction_containsCorrectPlan() {
        CheckpointStore store = new CheckpointStore();
        SecurityEnforcer enforcer = new SecurityEnforcer(SecurityPolicy.allowAll());
        DefaultActionModule module = new DefaultActionModule(
                ActionValidationPipeline.defaultPipeline(enforcer), store);

        AgentIdentity agent = AgentIdentity.of("agent", "orchestrator");
        ExecutionContext ctx = ExecutionContext.of(agent);
        ReasoningResult reasoning = new ReasoningResult(
                "Publish final research report",
                List.of("publish_report"),
                "Dissemination phase", 0.85, false);

        module.execute(reasoning, ctx);

        String taskId = ctx.executionId() + ":publish_report";
        TaskCheckpoint cp = store.latest(taskId).get();
        assertTrue(cp.contextSummary().contains("publish_report"),
                "Checkpoint summary must mention the action name");
        assertEquals("Publish final research report", cp.goal());
    }
}
