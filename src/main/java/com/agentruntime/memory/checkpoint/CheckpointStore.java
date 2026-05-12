package com.agentruntime.memory.checkpoint;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * In-memory checkpoint store with full version history per task.
 *
 * Vol.1 Ch.8 §"State Serialization and Checkpointing":
 * "Checkpoint complete, schema-validated state before every risk boundary."
 *
 * Checkpoint triggers (Ch.8):
 *   1. After each major subgoal completion
 *   2. Before any irreversible action
 *   3. When context window crosses high-water mark
 *   4. After any step that materially changes the plan
 */
public class CheckpointStore {

    // taskId → ordered list of checkpoints (index 0 = first, last = current)
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<TaskCheckpoint>> history
            = new ConcurrentHashMap<>();

    /** Save a checkpoint. Version must be >= existing latest version + 1. */
    public void save(TaskCheckpoint checkpoint) {
        Objects.requireNonNull(checkpoint);
        history.computeIfAbsent(checkpoint.taskId(), k -> new CopyOnWriteArrayList<>())
               .add(checkpoint);
    }

    /** Return the latest checkpoint for a task. */
    public Optional<TaskCheckpoint> latest(String taskId) {
        List<TaskCheckpoint> versions = history.get(taskId);
        if (versions == null || versions.isEmpty()) return Optional.empty();
        return Optional.of(versions.get(versions.size() - 1));
    }

    /** Return all checkpoint versions for a task (oldest first). */
    public List<TaskCheckpoint> allVersions(String taskId) {
        List<TaskCheckpoint> versions = history.get(taskId);
        return versions != null ? List.copyOf(versions) : List.of();
    }

    /** Retrieve a specific version. */
    public Optional<TaskCheckpoint> version(String taskId, int version) {
        return allVersions(taskId).stream().filter(c -> c.version() == version).findFirst();
    }
}
