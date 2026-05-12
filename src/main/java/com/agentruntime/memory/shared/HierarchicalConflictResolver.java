package com.agentruntime.memory.shared;

import com.agentruntime.core.valueobjects.ConflictingRecord;
import com.agentruntime.core.valueobjects.ConflictResolution;
import java.util.List;

/**
 * Backward-compatible alias delegating to SingleStoreConflictResolver (Ch.12 policies).
 * For multi-agent conflict resolution use MultiAgentConflictResolver (Ch.13 policies).
 */
public class HierarchicalConflictResolver implements ConflictResolver {
    private final SingleStoreConflictResolver delegate;

    public HierarchicalConflictResolver(HumanArbitrationQueue arbitrationQueue) {
        this.delegate = new SingleStoreConflictResolver(arbitrationQueue);
    }

    @Override
    public ConflictResolution resolve(List<ConflictingRecord> conflicts) {
        return delegate.resolve(conflicts);
    }
}
