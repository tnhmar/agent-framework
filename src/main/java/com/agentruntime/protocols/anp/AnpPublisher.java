package com.agentruntime.protocols.anp;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.UUID;

/**
 * ANP publisher — DID-based identity, topic-based publish/subscribe.
 * Vol.1 Ch.22 §"Agent Networking Protocol".
 *
 * S-12 FIX: Transport is injectable. Default is in-process only (suitable for
 * single-JVM testing). Inject a real AnpTransport for production network delivery.
 *
 * In-process mode is explicit, not silent. If no transport is registered and
 * publish() is called, it uses the in-process bus. If a transport is registered,
 * it is used and its exceptions propagate cleanly.
 */
public class AnpPublisher {

    private final AnpIdentity identity;
    private final AnpTransport transport;

    // In-process subscriber registry (used when transport == IN_PROCESS_TRANSPORT)
    private static final AnpTransport IN_PROCESS_TRANSPORT = null; // sentinel
    private final ConcurrentHashMap<String, List<Consumer<AnpEnvelope>>> subscribers =
            new ConcurrentHashMap<>();

    /** Full constructor — inject a real transport for network delivery. */
    public AnpPublisher(AnpIdentity identity, AnpTransport transport) {
        this.identity  = Objects.requireNonNull(identity,  "identity must not be null");
        this.transport = transport; // null = in-process mode (explicit, tested)
    }

    /** In-process constructor — suitable for single-JVM tests and local development. */
    public AnpPublisher(AnpIdentity identity) { this(identity, null); }

    /**
     * Publish a message to a topic.
     * If a transport is configured, messages are delivered via it.
     * If in-process mode, registered subscribers receive the message synchronously.
     */
    public void publish(String topic, Map<String, Object> message) throws AnpTransportException {
        Objects.requireNonNull(topic,   "topic must not be null");
        Objects.requireNonNull(message, "message must not be null");

        if (transport != null) {
            transport.deliver(topic, Map.copyOf(message), identity);
        } else {
            // In-process delivery — explicit, not silent
            List<Consumer<AnpEnvelope>> handlers = subscribers.get(topic);
            if (handlers != null) {
                AnpEnvelope envelope = new AnpEnvelope(
                        UUID.randomUUID().toString(),
                        identity.did(), "", topic,
                        Map.copyOf(message), java.time.Instant.now());
                handlers.forEach(h -> h.accept(envelope));
            }
        }
    }

    /** Subscribe to a topic (in-process mode only). */
    public void subscribe(String topic, Consumer<AnpEnvelope> handler) {
        Objects.requireNonNull(topic,   "topic must not be null");
        Objects.requireNonNull(handler, "handler must not be null");
        subscribers.computeIfAbsent(topic, k -> new CopyOnWriteArrayList<>()).add(handler);
    }

    public AnpIdentity identity() { return identity; }
    public boolean isNetworkEnabled() { return transport != null; }
}
