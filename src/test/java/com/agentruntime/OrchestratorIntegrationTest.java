package com.agentruntime;

import com.agentruntime.core.enums.AgentStatus;
import com.agentruntime.core.valueobjects.*;
import com.agentruntime.orchestrator.*;
import com.agentruntime.orchestrator.action.DefaultActionModule;
import com.agentruntime.orchestrator.action.validation.ActionValidationPipeline;
import com.agentruntime.orchestrator.failuredetection.DefaultFailureDetectionModule;
import com.agentruntime.orchestrator.perception.DefaultPerceptionModule;
import com.agentruntime.orchestrator.prompting.DefaultPromptingModule;
import com.agentruntime.orchestrator.reasoning.DefaultReasoningModule;
import com.agentruntime.orchestrator.reflection.DefaultReflectionModule;
import com.agentruntime.orchestrator.termination.DefaultTerminationModule;
import com.agentruntime.security.*;
import com.agentruntime.statemanager.StateManager;
import org.junit.jupiter.api.*;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

/** Integration test: orchestrator run with StateManager wired (Drift-03 fix). */
class OrchestratorIntegrationTest {

    private StateManager stateManager;
    private DefaultAgentOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        stateManager = new StateManager();
        var policy = new SecurityPolicy("test", Set.of("orchestrator","specialist","coordinator"), Set.of(), true, true);
        var enforcer = new SecurityEnforcer(policy);
        var pipeline = ActionValidationPipeline.defaultPipeline(enforcer);
        orchestrator = new DefaultAgentOrchestrator(
            new DefaultPerceptionModule(), new DefaultPromptingModule(),
            new DefaultReasoningModule(), new DefaultActionModule(pipeline),
            new DefaultReflectionModule(), new DefaultTerminationModule(),
            new DefaultFailureDetectionModule(), stateManager, 5
        );
    }

    @Test
    void run_stateManagerTracksExecution() {
        var agent = AgentIdentity.of("bot", "orchestrator");
        var ctx = ExecutionContext.of(agent);
        var task = new AgentTask("t1", "Do something useful", Map.of(), 2);
        orchestrator.run(task, ctx);
        // After completion, state manager must have a record
        assertTrue(stateManager.get(ctx.executionId()).isPresent(), "StateManager must track the execution (Drift-03)");
    }

    @Test
    void run_producesExecutionResult() {
        var agent = AgentIdentity.of("bot", "orchestrator");
        var ctx = ExecutionContext.of(agent);
        var task = new AgentTask("t2", "Generate report", Map.of("portfolio", "A"), 3);
        var result = orchestrator.run(task, ctx);
        assertNotNull(result);
        assertNotNull(result.finalStatus());
        assertNotNull(result.executionId());
    }

    @Test
    void suspend_changesStatusToSuspended() {
        var agent = AgentIdentity.of("bot", "orchestrator");
        var ctx = ExecutionContext.of(agent);
        stateManager.initialize(ctx.executionId(), agent);
        stateManager.updateStatus(ctx.executionId(), AgentStatus.ACTING);
        orchestrator.suspend(ctx.executionId());
        var state = stateManager.get(ctx.executionId());
        assertTrue(state.isPresent());
        assertEquals(AgentStatus.SUSPENDED, state.get().status());
    }

    @Test
    void terminate_changesStatusToTerminating() {
        var agent = AgentIdentity.of("bot", "orchestrator");
        var ctx = ExecutionContext.of(agent);
        stateManager.initialize(ctx.executionId(), agent);
        stateManager.updateStatus(ctx.executionId(), AgentStatus.ACTING);
        orchestrator.terminate(ctx.executionId(), "user request");
        var state = stateManager.get(ctx.executionId());
        assertTrue(state.isPresent());
        assertEquals(AgentStatus.TERMINATING, state.get().status());
    }
}
