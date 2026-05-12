package com.agentruntime.protocols.mcp;
/** JSON-RPC 2.0 request structure per V-11 fix. Vol1 Ch.20 §"Model Context Protocol". */
public record McpRequest(String jsonrpc, String method, Object params, String id) {
    public static McpRequest of(String method, Object params) {
        return new McpRequest("2.0", method, params, java.util.UUID.randomUUID().toString());
    }
}
