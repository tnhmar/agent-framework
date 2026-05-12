package com.agentruntime.core.valueobjects;
import java.util.List;
public record SharedMemoryQuery(List<String> recordIds, List<String> requiredTags, int maxResults) {}
