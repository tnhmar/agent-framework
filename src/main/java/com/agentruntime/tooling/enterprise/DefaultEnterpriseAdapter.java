package com.agentruntime.tooling.enterprise;

import com.agentruntime.core.valueobjects.ExecutionContext;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Enterprise adapter with pluggable per-operation handlers.
 *
 * S-05 FIX: No longer silently returns {"status":"ok"} for all operations.
 * Unregistered operations throw UnsupportedOperationException.
 *
 * Register operation handlers at startup:
 *   adapter.registerOperation("send_email", (input, ctx) -> emailService.send(input));
 */
public class DefaultEnterpriseAdapter implements EnterpriseAdapter {

    @FunctionalInterface
    public interface OperationHandler {
        Map<String, Object> handle(Map<String, Object> input, ExecutionContext ctx);
    }

    private final ConcurrentHashMap<String, OperationHandler> operations = new ConcurrentHashMap<>();

    public void registerOperation(String operationName, OperationHandler handler) {
        Objects.requireNonNull(operationName, "operationName must not be null");
        Objects.requireNonNull(handler,       "handler must not be null");
        operations.put(operationName, handler);
    }

    @Override
    public Map<String, Object> invoke(String operation, Map<String, Object> input,
                                       ExecutionContext ctx) {
        Objects.requireNonNull(operation, "operation must not be null");
        Objects.requireNonNull(ctx,       "ctx must not be null");

        OperationHandler handler = operations.get(operation);
        if (handler == null)
            throw new UnsupportedOperationException(
                    "No handler registered for enterprise operation: '" + operation
                    + "'. Register one via DefaultEnterpriseAdapter.registerOperation().");

        return handler.handle(input != null ? input : Map.of(), ctx);
    }

    public boolean hasOperation(String operationName) { return operations.containsKey(operationName); }
}
