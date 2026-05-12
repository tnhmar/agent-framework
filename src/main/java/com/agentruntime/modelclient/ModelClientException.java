package com.agentruntime.modelclient;

/**
 * Exception thrown by a ModelClient on failure.
 * The {@code retryable} flag indicates whether the caller may safely retry.
 *
 * Ported from agent-framework; namespace-adapted.
 */
public class ModelClientException extends Exception {

    private final boolean retryable;

    public ModelClientException(String message, boolean retryable) {
        super(message);
        this.retryable = retryable;
    }

    public ModelClientException(String message, boolean retryable, Throwable cause) {
        super(message, cause);
        this.retryable = retryable;
    }

    /** True if the caller may attempt a retry. */
    public boolean retryable() {
        return retryable;
    }
}
