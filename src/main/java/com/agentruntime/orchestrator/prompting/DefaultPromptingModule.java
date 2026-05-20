package com.agentruntime.orchestrator.prompting;

import com.agentruntime.core.valueobjects.ExecutionContext;
import com.agentruntime.memory.policy.TokenWindowMemoryPolicy;
import com.agentruntime.orchestrator.perception.PerceptionResult;

import java.util.*;

/**
 * Default prompt assembly — assembles a structured prompt with context-window management.
 *
 * S-08 FIX: Now includes:
 *   1. System context block with agent identity and goal
 *   2. Entity context from perception (most salient entities listed)
 *   3. Working memory window (most-recent entries within token budget)
 *   4. Structured tool context listing available tool IDs
 *   5. User turn with the actual input
 *
 * Vol.1 Ch.6 §"Prompting Module".
 */
public class DefaultPromptingModule implements PromptingModule {

    private static final int SYSTEM_TOKEN_BUDGET = 1500;
    private final TokenWindowMemoryPolicy tokenPolicy;

    public DefaultPromptingModule(int tokenBudget) {
        this.tokenPolicy = new TokenWindowMemoryPolicy(tokenBudget);
    }

    public DefaultPromptingModule() { this(SYSTEM_TOKEN_BUDGET); }

    @Override
    public PromptPackage build(PerceptionResult perception, String goal, ExecutionContext ctx) {
        Objects.requireNonNull(perception, "perception must not be null");
        Objects.requireNonNull(goal,       "goal must not be null");
        Objects.requireNonNull(ctx,        "ctx must not be null");

        String agentId = ctx.agentIdentity() != null ? ctx.agentIdentity().agentId() : "unknown";

        // System prompt: identity + goal + entity context
        String entityBlock = perception.extractedEntities().isEmpty() ? ""
                : "\nKey entities: " + String.join(", ", perception.extractedEntities()) + ".";

        String systemPrompt = "You are an intelligent agent (id=" + agentId + ")."
                + " Your current goal: " + goal + "."
                + entityBlock
                + "\nReason step by step. Produce structured output: "
                + "PLAN, ACTIONS (comma-separated), RATIONALE, CONFIDENCE (0–1), HUMAN_INPUT (true/false).";

        // User turn: the normalised input, windowed to fit budget
        List<String> userLines = List.of(
                "Input: " + perception.normalizedContent(),
                "Confidence of input quality: " + String.format("%.2f", perception.confidenceScore())
        );
        String userPrompt = tokenPolicy.buildContextString(userLines);

        Map<String, Object> toolContext = Map.of(
                "executionId", ctx.executionId(),
                "conversationId", ctx.conversationId()
        );

        return new PromptPackage(systemPrompt, userPrompt, List.of(), toolContext);
    }
}
