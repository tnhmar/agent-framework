package com.agentruntime;

import com.agentruntime.runtime.run.*;
import org.junit.jupiter.api.Test;

import java.time.Clock;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the ported Run aggregate and RunState state machine.
 * Ported from agent-framework RunTest; adapted to agentruntime namespace.
 */
class RunAggregateTest {

    private final Clock clock = Clock.systemUTC();

    private Run newRun() {
        return new Run(RunId.generate(), "session-1", "agent-1", clock);
    }

    @Test
    void initialStateIsCreated() {
        assertEquals(RunState.CREATED, newRun().state());
    }

    @Test
    void happyPathCompletesSuccessfully() {
        Run run = newRun();
        run.transition(RunState.VALIDATING,     clock);
        run.transition(RunState.PLANNING,       clock);
        run.transition(RunState.MODEL_CALL,     clock);
        run.transition(RunState.PLANNING,       clock);
        run.transition(RunState.RESPONDING,     clock);
        run.complete("final answer", clock);

        assertEquals(RunState.COMPLETED, run.state());
        assertEquals("final answer", run.finalResponse());
        assertTrue(run.state().isTerminal());
    }

    @Test
    void toolExecutionPath() {
        Run run = newRun();
        run.transition(RunState.VALIDATING,     clock);
        run.transition(RunState.PLANNING,       clock);
        run.transition(RunState.TOOL_EXECUTION, clock);
        run.transition(RunState.MEMORY_UPDATE,  clock);
        run.transition(RunState.RESPONDING,     clock);
        run.complete("done", clock);

        assertEquals(RunState.COMPLETED, run.state());
    }

    @Test
    void illegalTransitionThrowsIllegalStateException() {
        Run run = newRun();
        run.transition(RunState.VALIDATING, clock);
        assertThrows(IllegalStateException.class, () -> run.transition(RunState.COMPLETED, clock));
    }

    @Test
    void transitionAfterTerminalStateThrows() {
        Run run = newRun();
        run.transition(RunState.VALIDATING, clock);
        run.transition(RunState.PLANNING,   clock);
        run.transition(RunState.RESPONDING, clock);
        run.complete("done", clock);

        assertThrows(IllegalStateException.class, () -> run.transition(RunState.PLANNING, clock));
    }

    @Test
    void cancelReachesTerminalState() {
        Run run = newRun();
        run.transition(RunState.VALIDATING, clock);
        run.cancel(clock);
        assertEquals(RunState.CANCELLED, run.state());
        assertTrue(run.state().isTerminal());
    }

    @Test
    void failSetsFailureReason() {
        Run run = newRun();
        run.transition(RunState.VALIDATING, clock);
        run.fail("network error", clock);
        assertEquals(RunState.FAILED, run.state());
        assertEquals("network error", run.failureReason());
    }

    @Test
    void workingMemoryPersistsAcrossTransitions() {
        Run run = newRun();
        run.putWorkingMemory("key", "value");
        run.transition(RunState.VALIDATING, clock);
        assertEquals("value", run.workingMemory().get("key"));
    }

    @Test
    void toSnapshotCapturesState() {
        Run run = newRun();
        run.transition(RunState.VALIDATING, clock);
        RunSnapshot snapshot = run.toSnapshot();

        assertEquals(run.runId(),    snapshot.runId());
        assertEquals(RunState.VALIDATING, snapshot.state());
        assertTrue(snapshot.finalResponse().isEmpty());
    }
}
