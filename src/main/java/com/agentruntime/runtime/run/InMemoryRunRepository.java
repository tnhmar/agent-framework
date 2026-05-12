package com.agentruntime.runtime.run;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * In-memory RunRepository.
 *
 * Ported from agent-framework support.InMemoryRunRepository; namespace-adapted.
 * Thread-safe; suitable for tests and single-node deployments.
 * Replace with a durable implementation (JDBC, Redis) for production resilience.
 */
public final class InMemoryRunRepository implements RunRepository {

    private final ConcurrentHashMap<RunId, Run>           runs  = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<RunId, List<RunStep>> steps = new ConcurrentHashMap<>();

    @Override
    public Run save(Run run) {
        runs.put(run.runId(), run);
        return run;
    }

    @Override
    public Optional<Run> findById(RunId runId) {
        return Optional.ofNullable(runs.get(runId));
    }

    @Override
    public void saveStep(RunId runId, RunStep step) {
        steps.computeIfAbsent(runId, k -> new CopyOnWriteArrayList<>()).add(step);
    }

    @Override
    public List<RunStep> listSteps(RunId runId) {
        return List.copyOf(steps.getOrDefault(runId, List.of()));
    }
}
