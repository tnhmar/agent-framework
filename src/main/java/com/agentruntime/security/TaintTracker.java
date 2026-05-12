package com.agentruntime.security;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Tracks taint propagation through agent data flows.
 * Vol.1 Ch.12 — source authority and taint tracking.
 *
 * Thread-safe: ConcurrentHashMap of CopyOnWriteArraySet.
 */
public class TaintTracker {

    private final ConcurrentHashMap<String, Set<String>> taintMap = new ConcurrentHashMap<>();

    /** Mark {@code dataId} as tainted by the given source. */
    public void taint(String dataId, String taintSource) {
        Objects.requireNonNull(dataId,      "dataId must not be null");
        Objects.requireNonNull(taintSource, "taintSource must not be null");
        taintMap.computeIfAbsent(dataId, k -> new CopyOnWriteArraySet<>()).add(taintSource);
    }

    /** Returns true if {@code dataId} has at least one taint source. */
    public boolean isTainted(String dataId) {
        Set<String> sources = taintMap.get(dataId);
        return sources != null && !sources.isEmpty();
    }

    /** Returns all taint sources for {@code dataId}, or an empty set if untainted. */
    public Set<String> taintSources(String dataId) {
        return Collections.unmodifiableSet(taintMap.getOrDefault(dataId, Set.of()));
    }

    /** Remove all taint from {@code dataId}. */
    public void clear(String dataId) {
        taintMap.remove(dataId);
    }
}
