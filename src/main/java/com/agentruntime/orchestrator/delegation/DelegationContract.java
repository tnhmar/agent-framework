package com.agentruntime.orchestrator.delegation;

import com.agentruntime.core.valueobjects.AgentIdentity;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Formal delegation contract per Vol1 Ch.7 §"Delegation to Subagents".
 * Mandatory fields: task specification, context payload, timeout and budget,
 * result contract, failure protocol.
 */
public record DelegationContract(
    String contractId,
    String taskSpecification,
    Map<String, Object> contextPayload,
    Duration timeout,
    long budgetTokens,
    String resultContract,
    String failureProtocol,
    AgentIdentity delegatingAgent,
    AgentIdentity targetAgent,
    int delegationDepth,
    Instant issuedAt
) {
    public DelegationContract {
        if (taskSpecification == null || taskSpecification.isBlank())
            throw new IllegalArgumentException("taskSpecification is required (Vol1 Ch.7)");
        if (resultContract == null || resultContract.isBlank())
            throw new IllegalArgumentException("resultContract is required (Vol1 Ch.7)");
        if (failureProtocol == null || failureProtocol.isBlank())
            throw new IllegalArgumentException("failureProtocol is required (Vol1 Ch.7)");
        if (timeout == null)
            throw new IllegalArgumentException("timeout is required (Vol1 Ch.7)");
        if (delegationDepth < 0)
            throw new IllegalArgumentException("delegationDepth must be >= 0");
    }

    public static DelegationContract of(
        String taskSpec,
        Map<String, Object> context,
        Duration timeout,
        long budgetTokens,
        String resultContract,
        String failureProtocol,
        AgentIdentity delegator,
        AgentIdentity target,
        int depth
    ) {
        return new DelegationContract(
            UUID.randomUUID().toString(),
            taskSpec, context, timeout, budgetTokens,
            resultContract, failureProtocol,
            delegator, target, depth, Instant.now()
        );
    }
}
