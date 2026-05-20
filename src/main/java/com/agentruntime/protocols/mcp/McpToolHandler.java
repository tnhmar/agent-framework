package com.agentruntime.protocols.mcp;

import java.util.Map;

/**
 * SPI for handling MCP tool/call requests.
 * Register implementations in McpClient for each tool name.
 */
@FunctionalInterface
public interface McpToolHandler {
    Map<String, Object> handle(String toolName, Map<String, Object> arguments);
}
