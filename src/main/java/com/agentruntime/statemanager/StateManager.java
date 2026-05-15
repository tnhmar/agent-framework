package com.agentruntime.statemanager;

import com.agentruntime.core.enums.AgentStatus;
import com.agentruntime.core.valueobjects.AgentIdentity;
import com.agentruntime.observability.AuditEvent;
import com.agentruntime.observability.ObservabilityBus;
import com.agentruntime.observability.TelemetryRecord;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Single source of truth for agent execution state.
 *
 * Vol.1 Ch.4: "StateManager is the single source of truth for execution status."
 * Vol.1 Ch.4 tip: "Log each lifecycle phase transition by name."
 *
 * G-12 fix: updateStatus() now emits a structured PHASE_TRANSITION AuditEvent
 * to the ObservabilityBus on every status change.
 */
public class StateManager {

    public record AgentStateRecord(
            String      executionId,
            AgentIdentity agentIdentity,
            AgentStatus status,
            int         iteration,
            Instant     lastUpdated,
            Instant     phaseStarted) {}

    private final ConcurrentHashMap<String, AgentStateRecord> states = new ConcurrentHashMap<>();
    private final ObservabilityBus bus;

    public StateManager(ObservabilityBus bus) {
        this.bus = Objects.requireNonNull(bus);
    }

    /** No-arg constructor for contexts where observability is not wired. */
    public StateManager() { this(new ObservabilityBus()); }

    public void initialize(String executionId, AgentIdentity identity) {
        Objects.requireNonNull(executionId, "executionId must not be null");
        Objects.requireNonNull(identity,    "identity must not be null");
        states.put(executionId, new AgentStateRecord(
                executionId, identity, AgentStatus.IDLE, 0, Instant.now(), Instant.now()));
    }

    public void updateStatus(String executionId, AgentStatus newStatus) {
        states.compute(executionId, (id, existing) -> {
            if (existing == null) return null;
            AgentStatus prev = existing.status();
            Instant now = Instant.now();
            // P2-03: emit latency telemetry for the phase that just ended
            if (existing.phaseStarted() != null) {
                java.time.Duration phaseDuration = java.time.Duration.between(existing.phaseStarted(), now);
                bus.record(new TelemetryRecord(
                        executionId, executionId + ":" + prev.name(),
                        prev.name(), phaseDuration, true,
                        java.util.Map.of("agentId", existing.agentIdentity() != null
                                ? existing.agentIdentity().agentId() : "unknown",
                                "iteration", existing.iteration()),
                        existing.phaseStarted()));
            }
            AgentStateRecord updated = new AgentStateRecord(
                    executionId, existing.agentIdentity(), newStatus,
                    existing.iteration(), now, now);
            // G-12: emit phase transition event
            emitPhaseTransition(existing.agentIdentity(), executionId, prev, newStatus,
                    existing.iteration());
            return updated;
        });
    }

    public void incrementIteration(String executionId) {
        states.computeIfPresent(executionId, (id, s) -> new AgentStateRecord(
                id, s.agentIdentity(), s.status(), s.iteration() + 1, Instant.now(), s.phaseStarted()));
    }

    public Optional<AgentStateRecord> get(String executionId) {
        return Optional.ofNullable(states.get(executionId));
    }

    public boolean isActive(String executionId) {
        return get(executionId)
                .map(s -> s.status() != AgentStatus.IDLE
                        && s.status() != AgentStatus.FAILED)
                .orElse(false);
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private void emitPhaseTransition(AgentIdentity agent, String executionId,
                                      AgentStatus from, AgentStatus to, int step) {
        if (agent == null) return;
        bus.emit(new AuditEvent(
                UUID.randomUUID().toString(),
                "PHASE_TRANSITION",
                agent,
                from.name() + " -> " + to.name(),
                Map.of("executionId", executionId,
                       "fromPhase",   from.name(),
                       "toPhase",     to.name()),
                Instant.now(),
                false,
                step, 0, List.of()));
    }
}
