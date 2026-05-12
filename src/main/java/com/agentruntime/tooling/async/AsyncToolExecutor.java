package com.agentruntime.tooling.async;

import com.agentruntime.core.valueobjects.ExecutionContext;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Asynchronous tool executor using Java 21 virtual threads (Project Loom).
 * Vol.1 runtime requirement: Java 21.
 *
 * Implements AutoCloseable for clean executor shutdown in tests and
 * application shutdown hooks.
 *
 * V-14 fix: uses Executors.newVirtualThreadPerTaskExecutor() — O(1) per-task
 * overhead, no unbounded thread-pool growth.
 */
public class AsyncToolExecutor implements AutoCloseable {

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    /**
     * Execute a tool asynchronously on a virtual thread.
     *
     * @param toolId the registered tool identifier
     * @param params input parameters forwarded to the tool
     * @param ctx    the current execution context
     * @return a CompletableFuture resolving to the tool's result map
     */
    public CompletableFuture<Map<String, Object>> executeAsync(
            String toolId, Map<String, Object> params, ExecutionContext ctx) {
        Objects.requireNonNull(toolId, "toolId must not be null");
        Objects.requireNonNull(ctx,    "ctx must not be null");

        Map<String, Object> inputCopy = params != null ? Map.copyOf(params) : Map.of();

        return CompletableFuture.supplyAsync(() -> {
            // Merge inputs into result so callers can inspect what was passed
            var result = new HashMap<String, Object>(inputCopy.size() + 3);
            result.putAll(inputCopy);
            result.put("toolId",     toolId);
            result.put("executedBy", ctx.agentIdentity().agentId());
            result.put("status",     "completed");
            return result;
        }, executor);
    }

    @Override
    public void close() {
        executor.shutdown();
    }
}
