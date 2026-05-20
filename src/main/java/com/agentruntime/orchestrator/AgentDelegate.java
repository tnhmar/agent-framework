package com.agentruntime.orchestrator;

import com.agentruntime.core.valueobjects.ExecutionContext;
import com.agentruntime.orchestrator.delegation.DelegationContract;
import com.agentruntime.orchestrator.delegation.DelegationResult;

/**
 * Delegation capability interface.
 * V-ISP-01: Clients that only need delegation depend on this.
 */
public interface AgentDelegate {
    DelegationResult delegate(DelegationContract contract, ExecutionContext ctx);
}
