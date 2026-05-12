package com.agentruntime.memory.usermodel;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * A single structured fact about a user stored in the user model.
 * Vol.1 Ch.13 §"User Model Schema" — exact field mapping:
 *
 *   fact_id     | factId
 *   user_id     | userId
 *   category    | "preference" | "constraint" | "expertise" | "goal" | "style"
 *   key         | e.g. "preferred_language"
 *   value       | e.g. "TypeScript"
 *   confidence  | ConfidenceLevel enum value
 *   source      | "user_explicit" | "agent_inferred" | "tool_verified" | "behavioural"
 *   created_at  | createdAt
 *   updated_at  | updatedAt
 *   access_count| accessCount
 *   tags        | tags
 */
public record UserModelFact(
        String         factId,
        String         userId,
        String         category,
        String         key,
        String         value,
        ConfidenceLevel confidence,
        String         source,
        Instant        createdAt,
        Instant        updatedAt,
        int            accessCount,
        List<String>   tags
) {
    public UserModelFact {
        Objects.requireNonNull(factId,     "factId must not be null");
        Objects.requireNonNull(userId,     "userId must not be null");
        Objects.requireNonNull(category,   "category must not be null");
        Objects.requireNonNull(key,        "key must not be null");
        Objects.requireNonNull(value,      "value must not be null");
        Objects.requireNonNull(confidence, "confidence must not be null");
        Objects.requireNonNull(source,     "source must not be null");
        tags = tags != null ? List.copyOf(tags) : List.of();
    }

    public static UserModelFact create(String factId, String userId,
                                       String category, String key, String value,
                                       ConfidenceLevel confidence, String source) {
        Instant now = Instant.now();
        return new UserModelFact(factId, userId, category, key, value,
                confidence, source, now, now, 0, List.of());
    }

    public UserModelFact incrementAccess() {
        return new UserModelFact(factId, userId, category, key, value, confidence,
                source, createdAt, Instant.now(), accessCount + 1, tags);
    }

    public UserModelFact withConfidence(ConfidenceLevel newLevel) {
        return new UserModelFact(factId, userId, category, key, value, newLevel,
                source, createdAt, Instant.now(), accessCount, tags);
    }
}
