package com.agentruntime.core.valueobjects;
import com.agentruntime.core.enums.ResolutionPolicy;
import com.agentruntime.core.enums.ValueProvenance;
import java.time.Instant;
import java.util.List;
public record ConflictResolution(
    String entityKey,
    String attribute,
    CanonicalValue canonicalValue,
    ResolutionPolicy policyApplied,
    List<MemoryRecordId> suppressedRecords,
    Instant resolutionTimestamp
) {
    public static ConflictResolution pendingArbitration(List<ConflictingRecord> conflicts) {
        var ids = conflicts.stream().map(ConflictingRecord::recordId).toList();
        return new ConflictResolution(
            conflicts.isEmpty() ? "unknown" : conflicts.get(0).entityKey(),
            conflicts.isEmpty() ? "unknown" : conflicts.get(0).attribute(),
            new CanonicalValue("PENDING", ValueProvenance.SYNTHESIZED, 0.0),
            ResolutionPolicy.HUMAN_ARBITRATION, ids, Instant.now());
    }
}
