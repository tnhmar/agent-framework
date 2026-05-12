package com.agentruntime.orchestrator.termination;
public record TerminationDecision(boolean shouldTerminate, String reason, boolean isSuccess) {}
