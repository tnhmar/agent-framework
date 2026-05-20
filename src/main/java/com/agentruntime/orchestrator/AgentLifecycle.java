package com.agentruntime.orchestrator;

/**
 * Lifecycle control for a running agent.
 * V-ISP-01: Clients that only need lifecycle control depend on this.
 */
public interface AgentLifecycle {
    void suspend(String executionId);
    void resume(String executionId);
    void terminate(String executionId, String reason);
}
