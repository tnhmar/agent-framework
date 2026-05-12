package com.agentruntime.core.valueobjects;
import com.agentruntime.core.enums.ArbitrationPriority;
import java.time.Instant;
import java.util.List;
public record ArbitrationRequest(List<ConflictingRecord> conflicts, ArbitrationPriority priority, Instant submittedAt) {
    public ArbitrationRequest(List<ConflictingRecord> conflicts, ArbitrationPriority priority) {
        this(conflicts, priority, Instant.now());
    }
}
