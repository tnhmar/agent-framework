package com.agentruntime.modelclient;

/**
 * SPI for all model interactions.
 * Ported from agent-framework; adapted to agentruntime namespace.
 * Follows Vol.1 Ch.4 "model proposes, runtime disposes" principle.
 */
public interface ModelClient {
    /**
     * Generate a model response for the given prompt.
     *
     * @param prompt  the assembled prompt package
     * @param context execution context (session id, tenant, working memory)
     * @return model output (text + optional tool calls)
     * @throws ModelClientException on HTTP or parse failure (retryable flag indicates whether retry is safe)
     */
    ModelOutput generate(ModelPrompt prompt, ModelRequestContext context) throws ModelClientException;
}
