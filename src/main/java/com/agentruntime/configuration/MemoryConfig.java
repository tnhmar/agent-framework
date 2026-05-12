package com.agentruntime.configuration;
import com.agentruntime.core.enums.ArbitrationPriority;
import com.agentruntime.core.enums.ConsistencyModel;
import com.agentruntime.core.enums.SourceTrustLabel;
import java.util.Map;
public record MemoryConfig(
    int workingMemoryCapacity,
    int episodicRetentionDays,
    ConsistencyModel sharedConsistencyModel,
    Map<String, ConsistencyModel> namespaceConsistencyOverrides,
    boolean enableConflictArbitration,
    ArbitrationPriority defaultArbitrationPriority,
    SourceTrustLabel[] trustedSources
) {
    public static MemoryConfig defaults() {
        return new MemoryConfig(
            1000, 30,
            ConsistencyModel.EVENTUAL,
            Map.of(),
            true,
            ArbitrationPriority.HIGH,
            new SourceTrustLabel[]{SourceTrustLabel.VERIFIED, SourceTrustLabel.TRUSTED_TOOL}
        );
    }
}
