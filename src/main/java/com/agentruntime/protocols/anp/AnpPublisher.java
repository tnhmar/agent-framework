package com.agentruntime.protocols.anp;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * V-13 fix: ANP topic-based publish/subscribe infrastructure.
 * Vol1 Appendix A §"ANP Protocol": design contrast with A2A point-to-point.
 */
public class AnpPublisher {

    private final AnpIdentity identity;
    private final Map<String, List<Consumer<AnpEnvelope>>> subscribers = new ConcurrentHashMap<>();

    public AnpPublisher(AnpIdentity identity) { this.identity = identity; }

    public void subscribe(String topic, Consumer<AnpEnvelope> handler) {
        subscribers.computeIfAbsent(topic, k -> new CopyOnWriteArrayList<>()).add(handler);
    }

    public void publish(String topic, Map<String, Object> body, String targetNode) {
        var envelope = new AnpEnvelope(UUID.randomUUID().toString(), identity.did(), targetNode, topic, body, Instant.now());
        var handlers = subscribers.getOrDefault(topic, List.of());
        for (var handler : handlers) handler.accept(envelope);
    }

    public AnpIdentity identity() { return identity; }
}
