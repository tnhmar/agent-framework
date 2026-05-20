package com.agentruntime.protocols.gateway;

import com.agentruntime.core.enums.ProtocolType;
import com.agentruntime.core.valueobjects.ExecutionContext;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Protocol gateway with pluggable per-protocol dispatch handlers.
 *
 * S-04 FIX: No longer silently returns "dispatched" for all calls.
 * If no handler is registered for a protocol, throws UnsupportedOperationException
 * so the caller knows it must provide a real implementation.
 *
 * Register handlers at startup:
 *   gateway.registerHandler(ProtocolType.MCP, (target, payload, ctx) -> mcpClient.dispatch(...));
 */
public class DefaultProtocolGateway implements ProtocolGateway {

    @FunctionalInterface
    public interface ProtocolHandler {
        Map<String, Object> handle(String target, Map<String, Object> payload, ExecutionContext ctx);
    }

    private final ConcurrentHashMap<ProtocolType, ProtocolHandler> handlers = new ConcurrentHashMap<>();

    public void registerHandler(ProtocolType protocol, ProtocolHandler handler) {
        Objects.requireNonNull(protocol, "protocol must not be null");
        Objects.requireNonNull(handler,  "handler must not be null");
        handlers.put(protocol, handler);
    }

    @Override
    public Map<String, Object> dispatch(ProtocolType protocol, String target,
                                         Map<String, Object> payload, ExecutionContext ctx) {
        Objects.requireNonNull(protocol, "protocol must not be null");
        Objects.requireNonNull(ctx,      "ctx must not be null");

        ProtocolHandler handler = handlers.get(protocol);
        if (handler == null)
            throw new UnsupportedOperationException(
                    "No handler registered for protocol: " + protocol
                    + ". Register one via DefaultProtocolGateway.registerHandler().");

        return handler.handle(target, payload != null ? payload : Map.of(), ctx);
    }

    public boolean hasHandler(ProtocolType protocol) { return handlers.containsKey(protocol); }
}
