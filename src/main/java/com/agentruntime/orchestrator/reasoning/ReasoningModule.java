package com.agentruntime.orchestrator.reasoning;
import com.agentruntime.core.valueobjects.ExecutionContext;
import com.agentruntime.orchestrator.perception.PerceptionResult;
public interface ReasoningModule { ReasoningResult reason(PerceptionResult perception, AgentState state, ExecutionContext ctx); }
