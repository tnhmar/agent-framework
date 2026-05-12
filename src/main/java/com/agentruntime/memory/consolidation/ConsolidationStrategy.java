package com.agentruntime.memory.consolidation;

import com.agentruntime.memory.episodic.EpisodicStore;
import com.agentruntime.memory.semantic.SemanticStore;

/**
 * SPI for the three Vol.1 Ch.12 consolidation strategies.
 */
public interface ConsolidationStrategy {
    /** @return count of records written to semantic store */
    int consolidate(String agentId, EpisodicStore episodic, SemanticStore semantic, int batchSize);

    /** Human-readable name of this strategy. */
    String strategyName();
}
