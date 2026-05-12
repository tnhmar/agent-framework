package com.agentruntime.orchestrator.perception;
import com.agentruntime.core.valueobjects.ExecutionContext;
public interface PerceptionModule { PerceptionResult perceive(RawInput input, ExecutionContext ctx); }
