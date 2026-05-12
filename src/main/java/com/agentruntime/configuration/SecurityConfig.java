package com.agentruntime.configuration;
import com.agentruntime.core.enums.SourceTrustLabel;
import java.util.Set;
public record SecurityConfig(
    boolean taintTrackingEnabled,
    boolean safeFailureEnabled,
    boolean rbacEnabled,
    SourceTrustLabel minimumGlobalTrust,
    Set<String> restrictedOperations
) {
    public static SecurityConfig defaults() {
        return new SecurityConfig(true, true, true, SourceTrustLabel.AGENT_INFERRED, Set.of("admin_delete", "purge_all"));
    }
}
