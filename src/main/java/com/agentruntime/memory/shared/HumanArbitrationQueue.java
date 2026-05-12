package com.agentruntime.memory.shared;
import com.agentruntime.core.valueobjects.ArbitrationRequest;
import com.agentruntime.core.valueobjects.ConflictResolution;
import java.util.Optional;
public interface HumanArbitrationQueue {
    void submit(ArbitrationRequest request);
    Optional<ConflictResolution> poll(String entityKey);
}
