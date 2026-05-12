package com.agentruntime.orchestrator.termination;
import com.agentruntime.core.valueobjects.ExecutionContext;
import com.agentruntime.orchestrator.reflection.ReflectionResult;
public class DefaultTerminationModule implements TerminationModule {
    @Override
    public TerminationDecision evaluate(ReflectionResult reflection, int currentIteration, int maxIterations, ExecutionContext ctx) {
        if (reflection.goalAchieved()) return new TerminationDecision(true, "Goal achieved", true);
        if (currentIteration >= maxIterations) return new TerminationDecision(true, "Max iterations reached", false);
        if (reflection.shouldTerminate()) return new TerminationDecision(true, "Reflection triggered termination", false);
        return new TerminationDecision(false, "Continue", false);
    }
}
