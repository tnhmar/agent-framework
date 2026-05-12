package com.agentruntime.modelclient;

import java.util.Objects;

/**
 * A single message in a model prompt.
 */
public record ModelMessage(Role role, String content) {

    public enum Role { SYSTEM, USER, ASSISTANT, TOOL }

    public ModelMessage {
        Objects.requireNonNull(role, "role must not be null");
        Objects.requireNonNull(content, "content must not be null");
    }
}
