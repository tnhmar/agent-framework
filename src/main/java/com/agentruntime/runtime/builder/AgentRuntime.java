package com.agentruntime.runtime.builder;

import com.agentruntime.memory.checkpoint.CheckpointStore;
import com.agentruntime.observability.ObservabilityBus;
import com.agentruntime.orchestrator.AgentTask;
import com.agentruntime.orchestrator.DefaultAgentOrchestrator;
import com.agentruntime.orchestrator.ExecutionResult;
import com.agentruntime.core.valueobjects.ExecutionContext;
import com.agentruntime.runtime.run.RunRepository;
import com.agentruntime.security.TenantPolicyEngine;
import com.agentruntime.tooling.registry.ToolRegistry;

public record AgentRuntime(
        DefaultAgentOrchestrator orchestrator,
        RunRepository            runRepository,
        CheckpointStore          checkpointStore,
        ObservabilityBus         observabilityBus,
        ToolRegistry             toolRegistry,
        TenantPolicyEngine       tenantPolicyEngine) {

    public ExecutionResult run(AgentTask task, ExecutionContext ctx) {
        return orchestrator.run(task, ctx);
    }
}
