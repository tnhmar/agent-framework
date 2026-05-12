package com.agentruntime.orchestrator.failuredetection;
import com.agentruntime.core.valueobjects.ExecutionContext;
public interface FailureDetectionModule { FailureAssessment assess(Throwable error, String phase, ExecutionContext ctx); }
