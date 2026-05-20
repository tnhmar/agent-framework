package com.agentruntime.orchestrator;

import com.agentruntime.core.valueobjects.ExecutionContext;

/**
 * Minimal interface for executing a single task.
 * V-ISP-01: Clients that only need run() depend on this, not the full AgentOrchestrator.
 */
public interface AgentRunner {
    ExecutionResult run(AgentTask task, ExecutionContext ctx);
}
