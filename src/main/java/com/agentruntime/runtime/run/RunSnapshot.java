package com.agentruntime.runtime.run;

import java.time.Instant;
import java.util.Optional;

/**
 * Immutable snapshot of a Run aggregate — safe to serialize and store.
 * Ported from agent-framework model.RunSnapshot; namespace-adapted.
 */
public record RunSnapshot(
        RunId runId,
        String sessionId,
        String agentId,
        RunState state,
        Instant startedAt,
        Instant updatedAt,
        Optional<String> finalResponse,
        Optional<String> failureReason
) {}
