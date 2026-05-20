package com.agentruntime.orchestrator.action.validation;

import java.util.*;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Configurable safety policy — externally injectable blocked action set.
 * V-OCP-01 FIX: SafetyValidator no longer has a hardcoded blocked list.
 *
 * The default set matches the original hardcoded actions. Additional blocks
 * can be added at runtime without recompiling (e.g. from config file, database,
 * or admin API).
 */
public class SafetyPolicy {

    private static final Set<String> DEFAULT_BLOCKED = Set.of(
            "purge_all", "admin_delete", "self_replicate", "modify_own_code");

    private final Set<String> blockedActions;

    public SafetyPolicy(Set<String> blockedActions) {
        Objects.requireNonNull(blockedActions, "blockedActions must not be null");
        this.blockedActions = new CopyOnWriteArraySet<>(blockedActions);
    }

    /** Creates a policy with the default blocked action set. */
    public static SafetyPolicy defaults() {
        return new SafetyPolicy(DEFAULT_BLOCKED);
    }

    public boolean isBlocked(String action) {
        return action != null && blockedActions.contains(action.toLowerCase());
    }

    /** Add a blocked action at runtime (thread-safe). */
    public void block(String action) {
        Objects.requireNonNull(action);
        blockedActions.add(action.toLowerCase());
    }

    /** Remove a blocked action at runtime (thread-safe). */
    public void unblock(String action) {
        if (action != null) blockedActions.remove(action.toLowerCase());
    }

    public Set<String> blockedActions() {
        return Collections.unmodifiableSet(blockedActions);
    }
}
