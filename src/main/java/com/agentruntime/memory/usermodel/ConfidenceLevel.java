package com.agentruntime.memory.usermodel;

/**
 * Confidence levels for UserModelFact entries.
 * Vol.1 Ch.13 §"User Model Schema":
 *   STATED = 1.00  — user said it explicitly
 *   VERIFIED = 0.85 — confirmed by tool or repeated behaviour
 *   INFERRED = 0.60 — derived by agent from observation
 *   SPECULATIVE = 0.35 — single-instance inference; low confidence
 */
public enum ConfidenceLevel {
    STATED(1.00),
    VERIFIED(0.85),
    INFERRED(0.60),
    SPECULATIVE(0.35);

    public final double value;
    ConfidenceLevel(double v) { this.value = v; }
}
