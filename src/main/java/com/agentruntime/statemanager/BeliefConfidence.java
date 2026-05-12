package com.agentruntime.statemanager;

/**
 * Confidence levels for belief entries.
 * Vol.1 Ch.8 §"Belief State": "beliefs derived from live authoritative sources
 * carry higher confidence than those inferred from circumstantial evidence."
 */
public enum BeliefConfidence {
    AUTHORITATIVE(1.00),   // live tool/API confirmation
    HIGH(0.85),            // multiple consistent sources
    MODERATE(0.60),        // single reliable source
    LOW(0.35),             // inferred or circumstantial
    SPECULATIVE(0.15);     // single-instance inference

    public final double value;
    BeliefConfidence(double value) { this.value = value; }
}
