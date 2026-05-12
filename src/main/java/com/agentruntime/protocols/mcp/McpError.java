package com.agentruntime.protocols.mcp;
public record McpError(int code, String message, Object data) {}
