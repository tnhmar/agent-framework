package com.agentruntime.core.enums;
/** Four-policy conflict resolution hierarchy (Vol1 Ch.12 and Ch.13). CONSENSUS added for multi-agent Ch.13 Policy 3. */
public enum ResolutionPolicy {
    SOURCE_AUTHORITY,
    RECENCY,
    CONSENSUS,
    CONFIDENCE,
    HUMAN_ARBITRATION
}
