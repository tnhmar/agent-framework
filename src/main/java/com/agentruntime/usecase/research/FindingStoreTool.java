package com.agentruntime.usecase.research;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Accumulates research findings across ReAct iterations.
 *
 * Ported from agent-framework usecase.research.FindingStoreTool; namespace-adapted.
 * Actions: add | list | clear
 *
 * Thread-safe; designed to be shared across multiple orchestrator steps.
 */
public final class FindingStoreTool {

    public static final String NAME = "findingStore";

    private final CopyOnWriteArrayList<String> findings = new CopyOnWriteArrayList<>();

    public String execute(Map<String, Object> input) {
        String action = input.getOrDefault("action", "list").toString();
        return switch (action) {
            case "add" -> {
                String finding = input.getOrDefault("finding", "").toString();
                if (finding.isBlank()) throw new IllegalArgumentException("FindingStoreTool: 'finding' must not be blank");
                findings.add(finding);
                yield "Finding stored. Total: " + findings.size();
            }
            case "list" -> findings.isEmpty()
                    ? "No findings yet."
                    : String.join("\n", findings.stream().map(f -> "• " + f).toList());
            case "clear" -> {
                findings.clear();
                yield "Findings cleared.";
            }
            default -> throw new IllegalArgumentException("FindingStoreTool: unknown action '" + action + "'");
        };
    }

    public List<String> getFindings() { return List.copyOf(findings); }
}
