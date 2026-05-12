package com.agentruntime.memory.checkpoint;

/**
 * Status of a task at checkpoint time.
 * Vol.1 Ch.13 §"The Task Checkpoint Schema".
 */
public enum TaskStatus {
    IN_PROGRESS,
    SUSPENDED,
    AWAITING_INPUT,
    COMPLETED,
    FAILED
}
