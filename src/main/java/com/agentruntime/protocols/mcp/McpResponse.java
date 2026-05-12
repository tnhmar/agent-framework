package com.agentruntime.protocols.mcp;
/** JSON-RPC 2.0 response structure per V-11 fix. */
public record McpResponse(String jsonrpc, Object result, McpError error, String id) {
    public boolean isSuccess() { return error == null; }
    public static McpResponse success(Object result, String id) { return new McpResponse("2.0", result, null, id); }
    public static McpResponse error(McpError error, String id) { return new McpResponse("2.0", null, error, id); }
}
