package com.agentruntime.protocols.mcp;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * MCP client — JSON-RPC 2.0, initialization + tools/call dispatch.
 * Vol.1 Ch.19–20 §"Model Context Protocol".
 *
 * S-03 FIX: tools/call now dispatches to registered McpToolHandler instances.
 * If no handler is registered, returns a typed error response (never silently succeeds).
 *
 * Usage:
 *   McpClient client = new McpClient("client-1");
 *   client.registerHandler("webSearch", args -> Map.of("results", search(args)));
 *   client.initialize(Map.of());
 *   McpResponse result = client.toolsCall("webSearch", Map.of("query", "agent patterns"));
 */
public class McpClient {

    private final String clientId;
    private volatile boolean initialized = false;
    private final ConcurrentHashMap<String, McpToolHandler> handlers = new ConcurrentHashMap<>();
    private final Queue<McpMessage> inbox = new ConcurrentLinkedQueue<>();

    public McpClient(String clientId) {
        this.clientId = Objects.requireNonNull(clientId, "clientId must not be null");
    }

    /** Register a handler for a named tool. Call before toolsCall(). */
    public void registerHandler(String toolName, McpToolHandler handler) {
        Objects.requireNonNull(toolName, "toolName must not be null");
        Objects.requireNonNull(handler,  "handler must not be null");
        handlers.put(toolName, handler);
    }

    /** MCP initialization handshake — must be called before tools/call. */
    public McpResponse initialize(Map<String, Object> capabilities) {
        Objects.requireNonNull(capabilities, "capabilities must not be null");
        McpRequest req = McpRequest.of("initialize",
                Map.of("clientId", clientId, "capabilities", capabilities));
        this.initialized = true;
        return McpResponse.success(
                Map.of("status", "initialized",
                       "serverCapabilities", Map.of("tools", true, "logging", false)),
                req.id());
    }

    /**
     * Dispatch a tools/call request to the registered handler.
     * Returns a typed error if not initialized or handler not found.
     */
    public McpResponse toolsCall(String toolName, Map<String, Object> arguments) {
        if (!initialized)
            return McpResponse.error(
                    new McpError(-32002, "Client not initialized — call initialize() first", null), "0");

        McpRequest req = McpRequest.of("tools/call",
                Map.of("name", toolName, "arguments", arguments != null ? arguments : Map.of()));

        McpToolHandler handler = handlers.get(toolName);
        if (handler == null)
            return McpResponse.error(
                    new McpError(-32601,
                            "Tool not found: '" + toolName + "'. Register a handler via registerHandler()",
                            toolName),
                    req.id());

        try {
            Map<String, Object> result = handler.handle(toolName, arguments);
            return McpResponse.success(result, req.id());
        } catch (Exception e) {
            return McpResponse.error(
                    new McpError(-32603,
                            "Tool execution error: " + e.getMessage(), toolName),
                    req.id());
        }
    }

    public void receive(McpMessage message) { inbox.add(message); }
    public Optional<McpMessage> poll() { return Optional.ofNullable(inbox.poll()); }
    public boolean isInitialized() { return initialized; }
    public boolean hasHandler(String toolName) { return handlers.containsKey(toolName); }
    public int handlerCount() { return handlers.size(); }
}
