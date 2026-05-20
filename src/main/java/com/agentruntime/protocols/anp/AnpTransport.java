package com.agentruntime.protocols.anp;

import java.util.Map;

/**
 * SPI for ANP network transport.
 * S-12: AnpPublisher accepts an injectable transport so the in-process default
 * can be swapped for a real network implementation (HTTP/WebSocket/gRPC).
 */
@FunctionalInterface
public interface AnpTransport {
    /**
     * Deliver a message to a topic.
     * @throws AnpTransportException if delivery fails
     */
    void deliver(String topic, Map<String, Object> message, AnpIdentity sender)
            throws AnpTransportException;
}
