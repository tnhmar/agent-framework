package com.agentruntime.orchestrator.failuredetection;

/**
 * Vol.1 Ch.4 §"Reliability Through Failure Classification" — 4 categories:
 *
 *   TRANSIENT     — network timeouts, temporary rate limits, brief unavailability.
 *                   Handling: retry with exponential backoff.
 *   DETERMINISTIC — schema mismatches, invalid tool inputs, permanent API errors.
 *                   Handling: fail fast with structured diagnostics.
 *   POLICY        — permission denials, budget exhaustion, safety violations.
 *                   Handling: stop execution or escalate.
 *   SEMANTIC      — agent misunderstands task, irrelevant plan, objective drift.
 *                   Handling: replan or request clarification.
 */
public enum FailureCategory {
    TRANSIENT,
    DETERMINISTIC,
    POLICY,
    SEMANTIC
}
