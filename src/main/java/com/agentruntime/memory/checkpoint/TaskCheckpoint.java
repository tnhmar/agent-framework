package com.agentruntime.memory.checkpoint;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Complete, schema-versioned task checkpoint.
 *
 * Vol.1 Ch.13 §"The Task Checkpoint Schema" — exact field mapping:
 *   task_id         | taskId
 *   user_id         | userId
 *   session_id      | sessionId
 *   status          | TaskStatus enum
 *   goal            | original task statement
 *   completed_steps | what has been done
 *   next_steps      | what remains to be done
 *   open_questions  | unresolved blockers
 *   artefacts       | key=name, value=store reference
 *   context_summary | compact LLM-generated summary for resume injection
 *   checkpoint_at   | checkpointAt
 *   version         | incremented on each update
 *
 * Vol.1 Ch.8 §"State Serialization and Checkpointing":
 * "A complete checkpoint record contains: a schema-versioned run-state snapshot,
 *  the run ID, the step index, an integrity hash, and explicit resume instructions."
 */
public record TaskCheckpoint(
        String            taskId,
        String            userId,
        String            sessionId,
        TaskStatus        status,
        String            goal,
        List<String>      completedSteps,
        List<String>      nextSteps,
        List<String>      openQuestions,
        Map<String, String> artefacts,
        String            contextSummary,
        Instant           checkpointAt,
        int               version
) {
    public TaskCheckpoint {
        Objects.requireNonNull(taskId,         "taskId must not be null");
        Objects.requireNonNull(userId,         "userId must not be null");
        Objects.requireNonNull(sessionId,      "sessionId must not be null");
        Objects.requireNonNull(status,         "status must not be null");
        Objects.requireNonNull(goal,           "goal must not be null");
        Objects.requireNonNull(contextSummary, "contextSummary must not be null");
        Objects.requireNonNull(checkpointAt,   "checkpointAt must not be null");
        completedSteps = completedSteps != null ? List.copyOf(completedSteps) : List.of();
        nextSteps      = nextSteps      != null ? List.copyOf(nextSteps)      : List.of();
        openQuestions  = openQuestions  != null ? List.copyOf(openQuestions)  : List.of();
        artefacts      = artefacts      != null ? Map.copyOf(artefacts)       : Map.of();
        if (version < 1) throw new IllegalArgumentException("version must be >= 1");
    }

    /** Create an initial (version=1) checkpoint. */
    public static TaskCheckpoint initial(String taskId, String userId, String sessionId,
                                         String goal, List<String> nextSteps,
                                         String contextSummary) {
        return new TaskCheckpoint(taskId, userId, sessionId, TaskStatus.IN_PROGRESS, goal,
                List.of(), nextSteps, List.of(), Map.of(), contextSummary, Instant.now(), 1);
    }

    /** Produce the next checkpoint version at suspension time. */
    public TaskCheckpoint suspend(List<String> newCompletedSteps, List<String> remaining,
                                   List<String> openQuestions, String contextSummary) {
        return new TaskCheckpoint(taskId, userId, sessionId, TaskStatus.SUSPENDED, goal,
                newCompletedSteps, remaining, openQuestions, artefacts,
                contextSummary, Instant.now(), version + 1);
    }

    /** Produce the next checkpoint version with updated artefacts. */
    public TaskCheckpoint withArtefact(String name, String storeRef) {
        Map<String, String> updated = new java.util.HashMap<>(artefacts);
        updated.put(name, storeRef);
        return new TaskCheckpoint(taskId, userId, sessionId, status, goal,
                completedSteps, nextSteps, openQuestions, updated,
                contextSummary, Instant.now(), version + 1);
    }
}
