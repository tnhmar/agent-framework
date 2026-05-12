package com.agentruntime.configuration;
public record OrchestratorConfig(
    int defaultMaxIterations,
    long actionTimeoutMs,
    boolean enableFailureDetection,
    boolean enableReflection,
    int suspendCheckIntervalMs,
    int maxDelegationDepth
) {
    public static OrchestratorConfig defaults() {
        return new OrchestratorConfig(10, 30_000L, true, true, 100, 5);
    }
}
