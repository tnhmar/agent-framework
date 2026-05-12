package com.agentruntime.tooling.registry;

import com.agentruntime.core.enums.ToolCategory;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Full tool contract definition.
 *
 * Vol.1 Ch.7 §"Tool Calling: The Mechanism, the Protocol, the Contract":
 * A strong tool contract defines:
 *   1. Tool identity       — stable identifier, not version-coupled
 *   2. Input schema        — typed, versioned parameter definitions
 *   3. Output schema       — typed, versioned response definitions
 *   4. Error taxonomy      — typed error codes the runtime can act on
 *   5. Idempotency         — whether safe to retry without side-effect duplication
 *   6. Freshness profile   — how current the returned data typically is
 */
public record ToolDefinition(
        String              toolId,
        String              name,
        String              description,
        ToolCategory        category,
        Map<String, String> parameterSchema,  // param name → type string
        Map<String, String> outputSchema,     // field name → type string
        List<String>        errorTaxonomy,    // typed error codes (e.g. NOT_FOUND, RATE_LIMITED)
        boolean             idempotent,       // safe to retry without side-effect duplication
        String              freshnessProfile, // e.g. "real-time", "cached-5m", "daily-batch"
        boolean             async
) {
    public ToolDefinition {
        Objects.requireNonNull(toolId,   "toolId must not be null");
        Objects.requireNonNull(name,     "name must not be null");
        Objects.requireNonNull(category, "category must not be null");
        parameterSchema  = parameterSchema  != null ? Map.copyOf(parameterSchema)  : Map.of();
        outputSchema     = outputSchema     != null ? Map.copyOf(outputSchema)      : Map.of();
        errorTaxonomy    = errorTaxonomy    != null ? List.copyOf(errorTaxonomy)    : List.of();
        freshnessProfile = freshnessProfile != null ? freshnessProfile              : "unknown";
    }

    /**
     * Backward-compatible factory — callers that only have the original 6-field
     * signature get sensible defaults for the 4 new fields.
     */
    public static ToolDefinition of(String toolId, String name, String description,
                                    ToolCategory category, Map<String, String> parameterSchema,
                                    boolean async) {
        return new ToolDefinition(toolId, name, description, category, parameterSchema,
                Map.of(), List.of(), false, "unknown", async);
    }
}
