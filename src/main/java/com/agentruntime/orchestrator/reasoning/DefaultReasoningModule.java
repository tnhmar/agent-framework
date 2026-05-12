package com.agentruntime.orchestrator.reasoning;

import com.agentruntime.core.valueobjects.ExecutionContext;
import com.agentruntime.modelclient.*;
import com.agentruntime.orchestrator.perception.PerceptionResult;

import java.util.List;

/**
 * Default reasoning module — wires a real ModelClient.
 * Vol.1 Ch.4: "model proposes, runtime disposes."
 *
 * Enhanced with ModelClient SPI ported from agent-framework.
 * Falls back to stub plan when no ModelClient configured (unit-test mode).
 */
public class DefaultReasoningModule implements ReasoningModule {

    private final ModelClient modelClient;

    public DefaultReasoningModule(ModelClient modelClient) {
        this.modelClient = modelClient;
    }

    /** No-arg constructor: stub mode (no network calls). */
    public DefaultReasoningModule() { this(null); }

    @Override
    public ReasoningResult reason(PerceptionResult perception, AgentState state, ExecutionContext ctx) {
        if (modelClient == null) {
            return new ReasoningResult(
                    "Analyze input -> Select tool -> Execute -> Validate",
                    List.of("retrieve_context", "generate_response", "validate_output"),
                    "Stub reasoning chain (no ModelClient configured)",
                    0.87, false);
        }
        try {
            String systemPrompt =
                "You are a reasoning agent. Respond with:\n" +
                "PLAN: <one-line goal>\n" +
                "ACTIONS: <comma-separated action names>\n" +
                "RATIONALE: <one sentence>\n" +
                "CONFIDENCE: <0.0 to 1.0>\n" +
                "HUMAN_INPUT: <true|false>";
            String userPrompt = String.format(
                "Input: %s%nEntities: %s%nGoal: %s%nStep: %d",
                perception.normalizedContent(),
                String.join(", ", perception.extractedEntities()),
                state.currentGoal(), state.stepCount());
            ModelPrompt prompt = ModelPrompt.of(systemPrompt, userPrompt);
            ModelRequestContext reqCtx = ModelRequestContext.of(ctx.executionId(), ctx.agentIdentity().agentId());
            ModelOutput output = modelClient.generate(prompt, reqCtx);
            return parseReasoningResult(output);
        } catch (ModelClientException e) {
            return new ReasoningResult("Fallback after model error", List.of("retry_or_escalate"),
                    "ModelClientException: " + e.getMessage(), 0.0, !e.retryable());
        }
    }

    private ReasoningResult parseReasoningResult(ModelOutput output) {
        String text = output.text() != null ? output.text() : "";
        String plan      = extractField(text, "PLAN",       "Proceed with default plan");
        String actRaw    = extractField(text, "ACTIONS",    "generate_response");
        String rationale = extractField(text, "RATIONALE",  "Model-generated reasoning");
        double conf      = parseDouble(extractField(text, "CONFIDENCE",  "0.75"));
        boolean hitl     = "true".equalsIgnoreCase(extractField(text, "HUMAN_INPUT", "false").strip());
        return new ReasoningResult(plan, List.of(actRaw.split(",\\s*")), rationale, conf, hitl);
    }

    private static String extractField(String text, String field, String def) {
        for (String line : text.lines().toList())
            if (line.startsWith(field + ":")) return line.substring(field.length() + 1).strip();
        return def;
    }
    private static double parseDouble(String s) {
        try { return Double.parseDouble(s.strip()); } catch (NumberFormatException e) { return 0.75; }
    }
}
