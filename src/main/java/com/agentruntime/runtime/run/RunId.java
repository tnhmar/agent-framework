package com.agentruntime.runtime.run;

import java.util.Objects;
import java.util.UUID;

/**
 * Typed identifier for an agent run.
 * Ported from agent-framework model.RunId; namespace-adapted.
 */
public record RunId(String value) {

    public RunId {
        Objects.requireNonNull(value, "RunId value must not be null");
    }

    public static RunId generate() {
        return new RunId(UUID.randomUUID().toString());
    }

    public static RunId of(String value) {
        return new RunId(value);
    }

    @Override
    public String toString() { return value; }
}
