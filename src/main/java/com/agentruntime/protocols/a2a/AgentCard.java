package com.agentruntime.protocols.a2a;
import java.util.List;
/** V-12 fix: Agent Card per A2A spec. Vol1 Appendix A §"A2A Protocol". */
public record AgentCard(String agentId, String name, String description, List<String> capabilities, String endpoint) {}
