package com.agentruntime.memory.scoring;

/**
 * Result of computing the composite importance score for a memory record.
 * Vol.1 Ch.12 §"The Composite Scoring Formula":
 *   I(m) = wr·e^(-λt) + wf·log(1+n)/log(101) + ws·salience + wc·source_weight + wv·task_relevance
 */
public record ImportanceScore(
        double total,
        double freshnessComponent,
        double frequencyComponent,
        double salienceComponent,
        double sourceComponent,
        double taskRelevanceComponent
) {
    public boolean exceeds(double threshold) { return total >= threshold; }
}
