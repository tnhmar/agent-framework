package com.agentruntime.modelclient;

import java.util.List;
import java.util.Objects;

/**
 * Assembled prompt sent to a ModelClient.
 * Ported from agent-framework model.ModelPrompt; namespace-adapted.
 */
public record ModelPrompt(List<ModelMessage> messages) {
    public ModelPrompt {
        Objects.requireNonNull(messages, "messages must not be null");
        messages = List.copyOf(messages);
    }

    /** Convenience factory for a single user message. */
    public static ModelPrompt ofUser(String content) {
        return new ModelPrompt(List.of(new ModelMessage(ModelMessage.Role.USER, content)));
    }

    /** Convenience factory for a system + user pair. */
    public static ModelPrompt of(String systemContent, String userContent) {
        return new ModelPrompt(List.of(
                new ModelMessage(ModelMessage.Role.SYSTEM, systemContent),
                new ModelMessage(ModelMessage.Role.USER, userContent)
        ));
    }
}
