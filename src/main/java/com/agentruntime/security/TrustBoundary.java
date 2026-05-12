package com.agentruntime.security;
import com.agentruntime.core.enums.SourceTrustLabel;
public record TrustBoundary(String boundaryId, String name, SourceTrustLabel minimumTrustLevel, boolean enforceLeastAuthority) {
    public boolean allows(SourceTrustLabel label) { return label.trustMultiplier >= minimumTrustLevel.trustMultiplier; }
}
