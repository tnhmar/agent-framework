package com.agentruntime.memory.usermodel;

import com.agentruntime.memory.working.WorkingMemoryStore;

import java.util.Objects;

/**
 * Injects UserModel facts into working memory at session start.
 * V-SRP-01 FIX: Extracted from SessionLifecycleManager — single responsibility.
 *
 * Vol.1 Ch.13 §"Confidence-Gated Injection":
 * Only facts at or above minConfidence are injected.
 * Facts are keyed as "userModel:{category}:{key}" in working memory.
 */
public class UserModelInjector {

    private final double defaultMinConfidence;

    public UserModelInjector(double defaultMinConfidence) {
        if (defaultMinConfidence < 0 || defaultMinConfidence > 1)
            throw new IllegalArgumentException("minConfidence must be in [0,1]");
        this.defaultMinConfidence = defaultMinConfidence;
    }

    /** Default threshold: INFERRED level (0.60). */
    public UserModelInjector() { this(ConfidenceLevel.INFERRED.value); }

    /**
     * Inject facts from userModel into workingMemory.
     * @param userModel      the user's persistent model (may be null — no-op)
     * @param workingMemory  the active working memory for this session
     * @param minConfidence  minimum confidence threshold for injection
     * @return count of facts injected
     */
    public int inject(UserModel userModel, WorkingMemoryStore workingMemory,
                      double minConfidence) {
        Objects.requireNonNull(workingMemory, "workingMemory must not be null");
        if (userModel == null) return 0;

        int injected = 0;
        for (UserModelFact fact : userModel.all()) {
            if (fact.confidence().value >= minConfidence) {
                workingMemory.put(
                        "userModel:" + fact.category() + ":" + fact.key(),
                        fact.value());
                injected++;
            }
        }
        return injected;
    }

    /** Convenience: inject using the default threshold. */
    public int inject(UserModel userModel, WorkingMemoryStore workingMemory) {
        return inject(userModel, workingMemory, defaultMinConfidence);
    }
}
