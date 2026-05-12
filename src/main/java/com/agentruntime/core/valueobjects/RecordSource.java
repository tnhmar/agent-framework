package com.agentruntime.core.valueobjects;
import com.agentruntime.core.enums.SourceTrustLabel;

/**
 * V-15 fix: sourceAuthorityRank() implements the 4-tier authority ordering from Vol1 Ch.12:
 * stated explicit(4) > tool-verified(3) > agent-inferred(2) > user-generated(1).
 * isAuthoritative() is deprecated and kept only for backward compatibility.
 */
public record RecordSource(String sourceId, String sourceName, SourceTrustLabel trustLabel, boolean authoritative) {

    /** @deprecated Use sourceAuthorityRank() for the 4-tier Ch.12 ordering. */
    @Deprecated
    public boolean isAuthoritative() { return authoritative; }

    /**
     * 4-tier authority rank per Vol1 Ch.12 §"Source Authority":
     * VERIFIED=4, TRUSTED_TOOL=3, AGENT_INFERRED=2, USER_GENERATED=1, UNVERIFIED=0.
     */
    public int sourceAuthorityRank() {
        return switch (trustLabel) {
            case VERIFIED      -> 4;
            case TRUSTED_TOOL  -> 3;
            case AGENT_INFERRED -> 2;
            case USER_GENERATED -> 1;
            case UNVERIFIED    -> 0;
        };
    }

    public static RecordSource authoritative(String id, String name) {
        return new RecordSource(id, name, SourceTrustLabel.VERIFIED, true);
    }
    public static RecordSource trusted(String id, String name) {
        return new RecordSource(id, name, SourceTrustLabel.TRUSTED_TOOL, false);
    }
}
