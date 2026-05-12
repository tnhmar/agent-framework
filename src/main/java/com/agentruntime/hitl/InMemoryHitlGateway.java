package com.agentruntime.hitl;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory HitlGateway.
 *
 * Thread-safe. Pending requests are tracked by requestId in a ConcurrentHashMap
 * (not a queue) so that:
 *  - pendingCount() accurately reflects only unresolved requests
 *  - allPending() never includes resolved requests
 *  - resolve() atomically moves a request from pending → resolved
 */
public class InMemoryHitlGateway implements HitlGateway {

    private final Map<String, HitlRequest>  pending  = new ConcurrentHashMap<>();
    private final Map<String, HitlResponse> resolved = new ConcurrentHashMap<>();

    @Override
    public void submit(HitlRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(request.requestId(), "requestId must not be null");
        pending.put(request.requestId(), request);
    }

    @Override
    public Optional<HitlResponse> poll(String requestId) {
        Objects.requireNonNull(requestId);
        return Optional.ofNullable(resolved.get(requestId));
    }

    @Override
    public boolean isResolved(String requestId) {
        Objects.requireNonNull(requestId);
        return resolved.containsKey(requestId);
    }

    /**
     * Resolve a pending request.
     * Atomically removes it from pending and stores the response.
     */
    public void resolve(String requestId, String answer, String responderId) {
        Objects.requireNonNull(requestId, "requestId must not be null");
        Objects.requireNonNull(answer,    "answer must not be null");
        Objects.requireNonNull(responderId, "responderId must not be null");

        HitlResponse response = new HitlResponse(
                requestId, answer, responderId, Map.of(), Instant.now());
        pending.remove(requestId);
        resolved.put(requestId, response);
    }

    /** Returns the count of requests not yet resolved. */
    public int pendingCount() { return pending.size(); }

    /** Returns a snapshot of all currently unresolved requests. */
    public List<HitlRequest> allPending() {
        return List.copyOf(pending.values());
    }
}
