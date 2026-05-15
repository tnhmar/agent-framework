package com.agentruntime.orchestrator.action;

import com.agentruntime.core.valueobjects.ExecutionContext;
import com.agentruntime.core.valueobjects.ValidationResult;
import com.agentruntime.memory.checkpoint.CheckpointStore;
import com.agentruntime.memory.checkpoint.TaskCheckpoint;
import com.agentruntime.orchestrator.action.validation.*;
import com.agentruntime.orchestrator.reasoning.ReasoningResult;

import java.util.*;

/**
 * Default action execution module.
 *
 * Vol.1 Ch.7: validate before dispatch via 4-layer pipeline.
 * Vol.1 Ch.8 §"State Serialization and Checkpointing":
 *   "Before dispatching any irreversible action — checkpoint."
 *
 * G-13: non-idempotent actions trigger a checkpoint before dispatch.
 */
public class DefaultActionModule implements ActionModule {

    private static final Set<String> NON_IDEMPOTENT_SIGNALS = Set.of(
            "delete", "send", "publish", "pay", "refund",
            "write", "create", "submit", "commit", "issue");

    private final ActionValidationPipeline validationPipeline;
    private final CheckpointStore          checkpointStore;

    public DefaultActionModule(ActionValidationPipeline validationPipeline,
                               CheckpointStore checkpointStore) {
        this.validationPipeline = Objects.requireNonNull(validationPipeline);
        this.checkpointStore    = checkpointStore;
    }

    public DefaultActionModule(ActionValidationPipeline validationPipeline) {
        this(validationPipeline, null);
    }

    @Override
    public ActionResult execute(ReasoningResult reasoning, ExecutionContext ctx) {
        List<String> validationOutcomes = new ArrayList<>();
        try {
            ValidationResult result = validationPipeline.validate(reasoning, ctx);
            validationOutcomes.add("ActionValidationPipeline:PASS");

            // Pre-action checkpoint for irreversible actions
            if (checkpointStore != null) {
                for (String action : reasoning.selectedActions()) {
                    if (isIrreversible(action)) {
                        checkpoint(ctx, reasoning, action);
                        validationOutcomes.add("PRE_ACTION_CHECKPOINT:" + action);
                    }
                }
            }
        } catch (ActionValidationException e) {
            ValidationResult vr = e.result();
            validationOutcomes.add(vr.validatorName() + ":FAIL:" + vr.reason());
            return ActionResult.failure("Validation failed [" + vr.validatorName() + "]: " + vr.reason(),
                    validationOutcomes);
        }

        return ActionResult.success(
                Map.of("actions", reasoning.selectedActions(), "plan", reasoning.plan()),
                validationOutcomes);
    }

    private boolean isIrreversible(String actionName) {
        String lower = actionName.toLowerCase();
        return NON_IDEMPOTENT_SIGNALS.stream().anyMatch(lower::contains);
    }

    private void checkpoint(ExecutionContext ctx, ReasoningResult reasoning, String actionName) {
        try {
            String taskId = ctx.executionId() + ":" + actionName;
            TaskCheckpoint cp = TaskCheckpoint.initial(
                    taskId,
                    ctx.agentIdentity() != null ? ctx.agentIdentity().agentId() : "unknown",
                    ctx.conversationId(),
                    reasoning.plan(),
                    reasoning.selectedActions(),
                    "Pre-action checkpoint before irreversible action: " + actionName);
            checkpointStore.save(cp);
        } catch (Exception e) {
            // Never block action dispatch on checkpoint failure — but log it
            java.util.logging.Logger.getLogger(DefaultActionModule.class.getName())
                    .log(java.util.logging.Level.WARNING,
                         "Pre-action checkpoint failed for action: " + actionName, e);
        }
    }
}
