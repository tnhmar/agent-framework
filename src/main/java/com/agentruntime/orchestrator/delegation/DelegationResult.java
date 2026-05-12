package com.agentruntime.orchestrator.delegation;

import java.util.Map;

/** Result returned from a delegated sub-agent execution (Vol1 Ch.7 §"Delegation to Subagents"). */
public record DelegationResult(
    String contractId,
    boolean success,
    Map<String, Object> outputs,
    String failureReason,
    int delegationDepthUsed
) {
    public static DelegationResult success(String contractId, Map<String, Object> outputs, int depth) {
        return new DelegationResult(contractId, true, outputs, null, depth);
    }
    public static DelegationResult failure(String contractId, String reason, int depth) {
        return new DelegationResult(contractId, false, Map.of(), reason, depth);
    }
}
