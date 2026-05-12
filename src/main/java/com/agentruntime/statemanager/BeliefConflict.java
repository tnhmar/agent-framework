package com.agentruntime.statemanager;

import java.time.Instant;

/**
 * Recorded when new evidence contradicts an existing belief.
 * Vol.1 Ch.8: "when new evidence contradicts an existing belief, the state
 * manager surfaces the conflict explicitly rather than silently overwriting."
 */
public record BeliefConflict(
        String  beliefId,
        String  key,
        String  existingValue,
        String  conflictingValue,
        String  conflictingSourceId,
        Instant detectedAt
) {}
