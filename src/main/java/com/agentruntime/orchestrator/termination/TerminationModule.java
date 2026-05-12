package com.agentruntime.orchestrator.termination;
import com.agentruntime.core.valueobjects.ExecutionContext;
import com.agentruntime.orchestrator.reflection.ReflectionResult;
public interface TerminationModule { TerminationDecision evaluate(ReflectionResult reflection, int currentIteration, int maxIterations, ExecutionContext ctx); }
