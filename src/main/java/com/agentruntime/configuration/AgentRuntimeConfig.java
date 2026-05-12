package com.agentruntime.configuration;
public record AgentRuntimeConfig(
    OrchestratorConfig orchestrator,
    MemoryConfig memory,
    RagConfig rag,
    SecurityConfig security
) {
    public static AgentRuntimeConfig defaults() {
        return new AgentRuntimeConfig(
            OrchestratorConfig.defaults(),
            MemoryConfig.defaults(),
            RagConfig.defaults(),
            SecurityConfig.defaults()
        );
    }
}
