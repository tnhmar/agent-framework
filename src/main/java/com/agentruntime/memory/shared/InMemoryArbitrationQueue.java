package com.agentruntime.memory.shared;

import com.agentruntime.core.valueobjects.*;
import com.agentruntime.observability.AuditEvent;
import com.agentruntime.observability.ObservabilityBus;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * In-memory human arbitration queue.
 *
 * V-07 fix: resolveManually() emits an immutable AuditEvent before storing the resolution.
 * Vol.1 Ch.12 §"Human Arbitration": "Human-arbitrated resolutions are logged immutably
 * and become canonical until superseded."
 *
 * Thread-safe: resolveManually() uses ConcurrentHashMap.compute() for atomic
 * check-and-update (prevents lost-update race between containsKey and put).
 */
public class InMemoryArbitrationQueue implements HumanArbitrationQueue {

    private final Queue<ArbitrationRequest>       queue       = new ConcurrentLinkedQueue<>();
    private final ConcurrentHashMap<String, ConflictResolution> resolutions = new ConcurrentHashMap<>();
    private final ObservabilityBus                observabilityBus;

    public InMemoryArbitrationQueue(ObservabilityBus observabilityBus) {
        this.observabilityBus = Objects.requireNonNull(observabilityBus);
    }

    public InMemoryArbitrationQueue() { this(new ObservabilityBus()); }

    @Override
    public void submit(ArbitrationRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        queue.add(request);
    }

    @Override
    public Optional<ConflictResolution> poll(String entityKey) {
        Objects.requireNonNull(entityKey);
        return Optional.ofNullable(resolutions.get(entityKey));
    }

    /**
     * V-07: atomically stores the resolution and emits mandatory audit events.
     * If a resolution already exists it is superseded (logged before overwrite).
     */
    public void resolveManually(String entityKey, ConflictResolution resolution,
                                AgentIdentity arbitrator) {
        Objects.requireNonNull(entityKey,   "entityKey must not be null");
        Objects.requireNonNull(resolution,  "resolution must not be null");
        Objects.requireNonNull(arbitrator,  "arbitrator must not be null");

        resolutions.compute(entityKey, (key, existing) -> {
            if (existing != null) {
                // Emit supersession event before overwriting
                observabilityBus.emit(new AuditEvent(
                        UUID.randomUUID().toString(),
                        "HUMAN_ARBITRATION_SUPERSEDED",
                        arbitrator, "resolveManually",
                        Map.of("entityKey", key,
                               "previousValue", existing.canonicalValue().value()),
                        Instant.now(), true));
            }
            // Mandatory immutable audit record for every resolution
            observabilityBus.emit(new AuditEvent(
                    UUID.randomUUID().toString(),
                    "HUMAN_ARBITRATION_RESOLUTION",
                    arbitrator, "resolveManually",
                    Map.of("entityKey", key,
                           "canonicalValue", resolution.canonicalValue().value(),
                           "policyApplied",  resolution.policyApplied().name()),
                    Instant.now(), true));
            // Drain the resolved request from the pending queue
            queue.removeIf(req -> req.conflicts().stream()
                    .anyMatch(cr -> cr.entityKey().equals(key)));
            return resolution;
        });
    }

    public int pendingCount() { return queue.size(); }

    public List<ArbitrationRequest> allPending() { return List.copyOf(queue); }
}
