package com.agentruntime;

import com.agentruntime.runtime.run.*;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for InMemoryRunRepository.
 * Ported from agent-framework InMemoryRunRepositoryTest; namespace-adapted.
 */
class InMemoryRunRepositoryTest {

    private final Clock clock = Clock.systemUTC();

    private Run newRun() {
        return new Run(RunId.generate(), "session-1", "agent-1", clock);
    }

    @Test
    void saveAndFindById() {
        InMemoryRunRepository repo = new InMemoryRunRepository();
        Run run = newRun();
        repo.save(run);

        Optional<Run> found = repo.findById(run.runId());
        assertTrue(found.isPresent());
        assertEquals(run.runId(), found.get().runId());
    }

    @Test
    void findByIdReturnsEmptyForUnknownId() {
        InMemoryRunRepository repo = new InMemoryRunRepository();
        assertTrue(repo.findById(RunId.of("does-not-exist")).isEmpty());
    }

    @Test
    void saveStepAndListSteps() {
        InMemoryRunRepository repo = new InMemoryRunRepository();
        Run run = newRun();
        repo.save(run);

        RunStep step = new RunStep(run.runId(), RunState.CREATED, RunState.VALIDATING,
                Instant.now(), "Transition to VALIDATING", Map.of());
        repo.saveStep(run.runId(), step);

        var steps = repo.listSteps(run.runId());
        assertEquals(1, steps.size());
        assertEquals(RunState.VALIDATING, steps.get(0).toState());
    }

    @Test
    void listStepsReturnsEmptyForUnknownRun() {
        InMemoryRunRepository repo = new InMemoryRunRepository();
        assertTrue(repo.listSteps(RunId.of("ghost")).isEmpty());
    }

    @Test
    void multipleStepsPreserveInsertionOrder() {
        InMemoryRunRepository repo = new InMemoryRunRepository();
        Run run = newRun();
        repo.save(run);

        repo.saveStep(run.runId(), new RunStep(run.runId(), RunState.CREATED,     RunState.VALIDATING,     Instant.now(), "s1", Map.of()));
        repo.saveStep(run.runId(), new RunStep(run.runId(), RunState.VALIDATING,  RunState.PLANNING,       Instant.now(), "s2", Map.of()));
        repo.saveStep(run.runId(), new RunStep(run.runId(), RunState.PLANNING,    RunState.MODEL_CALL,     Instant.now(), "s3", Map.of()));

        var steps = repo.listSteps(run.runId());
        assertEquals(3, steps.size());
        assertEquals(RunState.VALIDATING, steps.get(0).toState());
        assertEquals(RunState.MODEL_CALL, steps.get(2).toState());
    }
}
