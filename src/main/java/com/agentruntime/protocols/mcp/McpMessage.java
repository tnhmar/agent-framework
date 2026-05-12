package com.agentruntime.protocols.mcp;
import java.time.Instant; import java.util.Map;
public record McpMessage(String messageId, String senderId, String receiverId, String content, Map<String, Object> metadata, Instant timestamp) {}
