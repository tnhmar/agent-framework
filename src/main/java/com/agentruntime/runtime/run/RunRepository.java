package com.agentruntime.runtime.run;

import java.util.List;
import java.util.Optional;

/**
 * SPI for durable run storage.
 *
 * Ported from agent-framework core.spi.RunRepository; namespace-adapted.
 * Satisfies Vol.1 Ch.4 §4.5 "durable state for retry resilience".
 *
 * Default implementation: InMemoryRunRepository (test / single-node).
 * Production implementations may use JDBC, Redis, or a document store.
 */
public interface RunRepository {

    /** Persist or update a Run aggregate. */
    Run save(Run run);

    /** Retrieve a Run by its identifier. */
    Optional<Run> findById(RunId runId);

    /** Append a step record to a run's history (for audit + replay). */
    void saveStep(RunId runId, RunStep step);

    /** List all recorded steps for a run in insertion order. */
    List<RunStep> listSteps(RunId runId);
}
