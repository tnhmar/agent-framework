package com.agentruntime.statemanager;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * Explicit goal stack — the agent's navigational instrument during complex runs.
 *
 * Vol.1 Ch.8 §"Goal Stack and Subgoal Tracking":
 * "Build it as a first-class data structure and make it inspectable in real time."
 *
 * Also supports intent anchoring: periodic verification that the current active
 * subgoal is still aligned with the top-level mission.
 *
 * Thread-safe: CopyOnWriteArrayList for ordered insertion.
 */
public class GoalStack {

    private final CopyOnWriteArrayList<GoalEntry> stack = new CopyOnWriteArrayList<>();

    // ── Mutation ─────────────────────────────────────────────────────────────

    /** Push a new goal onto the stack (appended at the end — newest = deepest). */
    public void push(GoalEntry goal) {
        Objects.requireNonNull(goal);
        if (stack.stream().anyMatch(g -> g.goalId().equals(goal.goalId())))
            throw new IllegalArgumentException("Duplicate goalId: '" + goal.goalId() + "' is already in the stack");
        stack.add(goal);
    }

    /** Update the status of a goal by ID. No-op if not found. */
    public void updateStatus(String goalId, GoalStatus status) {
        Objects.requireNonNull(goalId);
        for (int i = 0; i < stack.size(); i++) {
            if (stack.get(i).goalId().equals(goalId)) {
                stack.set(i, stack.get(i).withStatus(status));
                return;
            }
        }
    }

    /** Mark a goal as DEFERRED with a resume condition. */
    public void defer(String goalId, String resumeCondition) {
        Objects.requireNonNull(goalId);
        for (int i = 0; i < stack.size(); i++) {
            if (stack.get(i).goalId().equals(goalId)) {
                stack.set(i, stack.get(i).deferred(resumeCondition));
                return;
            }
        }
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    /** Return the top-level (first-pushed) goal, if any. */
    public Optional<GoalEntry> topLevel() {
        return stack.isEmpty() ? Optional.empty() : Optional.of(stack.get(0));
    }

    /** Return the currently ACTIVE goal deepest in the stack (most recent). */
    public Optional<GoalEntry> currentActive() {
        for (int i = stack.size() - 1; i >= 0; i--) {
            if (stack.get(i).status() == GoalStatus.ACTIVE) return Optional.of(stack.get(i));
        }
        return Optional.empty();
    }

    public List<GoalEntry> byStatus(GoalStatus status) {
        return stack.stream().filter(g -> g.status() == status).toList();
    }

    public Optional<GoalEntry> findById(String goalId) {
        return stack.stream().filter(g -> g.goalId().equals(goalId)).findFirst();
    }

    /**
     * Intent anchoring: verify the current active subgoal is aligned with the
     * top-level mission. Returns false if there is an active goal whose parent
     * chain does not reach the top-level goal.
     */
    public boolean isAligned() {
        Optional<GoalEntry> top = topLevel();
        if (top.isEmpty()) return true;
        Optional<GoalEntry> active = currentActive();
        if (active.isEmpty()) return true;
        return isDescendantOf(active.get(), top.get().goalId());
    }

    public List<GoalEntry> all() { return List.copyOf(stack); }
    public int size()            { return stack.size(); }
    public boolean isEmpty()     { return stack.isEmpty(); }

    // ── Private ───────────────────────────────────────────────────────────────

    private boolean isDescendantOf(GoalEntry entry, String ancestorId) {
        if (ancestorId.equals(entry.goalId())) return true;
        if (entry.parentGoalId() == null)      return false;
        return findById(entry.parentGoalId())
                .map(parent -> isDescendantOf(parent, ancestorId))
                .orElse(false);
    }
}
