package com.agentruntime.orchestrator.reflection;

import com.agentruntime.core.valueobjects.ExecutionContext;
import com.agentruntime.orchestrator.action.ActionResult;

import java.util.List;
import java.util.Objects;

/**
 * Default reflection module — assesses action quality and signals plan adaptation.
 *
 * S-09 FIX: Evaluates validation coverage, output completeness,
 * checkpoint presence, and error semantics — not just the boolean success flag.
 *
 * Vol.1 Ch.6 §"Reflection Module".
 */
public class DefaultReflectionModule implements ReflectionModule {

    @Override
    public ReflectionResult reflect(ActionResult actionResult, ExecutionContext ctx) {
        Objects.requireNonNull(actionResult, "actionResult must not be null");
        Objects.requireNonNull(ctx,          "ctx must not be null");

        if (!actionResult.success()) {
            String errorMsg = actionResult.errorMessage() != null ? actionResult.errorMessage() : "";
            boolean isTransient = errorMsg.toLowerCase().contains("timeout")
                    || errorMsg.toLowerCase().contains("rate limit");
            String lesson = isTransient ? "Retry recommended — transient failure detected"
                    : "Action failed: " + errorMsg + ". Reassess plan.";
            return new ReflectionResult(false,
                    List.of(lesson), "Replan or retry", true);
        }

        long passCount = actionResult.validationOutcomes().stream()
                .filter(o -> o.endsWith(":PASS")).count();
        double validationScore = actionResult.validationOutcomes().isEmpty() ? 0.5
                : (double) passCount / actionResult.validationOutcomes().size();

        boolean hasOutputs = !actionResult.outputs().isEmpty()
                && !actionResult.executedActions().isEmpty();
        double quality = (validationScore * 0.6) + (hasOutputs ? 0.4 : 0.0);

        boolean checkpointed = actionResult.validationOutcomes().stream()
                .anyMatch(o -> o.startsWith("PRE_ACTION_CHECKPOINT"));

        boolean goalAchieved   = quality >= 0.7;
        boolean shouldTerminate = quality < 0.3;

        List<String> lessons = List.of(
                "Validation coverage: " + String.format("%.0f%%", validationScore * 100),
                hasOutputs   ? "Outputs produced: " + actionResult.outputs().size() : "No outputs produced",
                checkpointed ? "Irreversible action checkpointed" : "No checkpoint required"
        );

        String suggestion = shouldTerminate ? "Terminate and report failure"
                : goalAchieved             ? "Continue to next goal"
                : "Retry or adjust plan";

        return new ReflectionResult(goalAchieved, lessons, suggestion, shouldTerminate);
    }
}
