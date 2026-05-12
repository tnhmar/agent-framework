package com.agentruntime.core.enums;
public enum SourceTrustLabel {
    VERIFIED(1.0),
    TRUSTED_TOOL(0.8),
    AGENT_INFERRED(0.6),
    UNVERIFIED(0.3),
    USER_GENERATED(0.2);

    public final double trustMultiplier;
    SourceTrustLabel(double trustMultiplier) { this.trustMultiplier = trustMultiplier; }
}
