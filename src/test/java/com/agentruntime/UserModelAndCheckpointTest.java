package com.agentruntime;

import com.agentruntime.memory.checkpoint.*;
import com.agentruntime.memory.usermodel.*;
import org.junit.jupiter.api.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for UserModelFact/UserModel (G-04) and TaskCheckpoint/CheckpointStore (G-05).
 */
class UserModelAndCheckpointTest {

    // ── UserModel ─────────────────────────────────────────────────────────────

    @Test void userModel_createAndMergeNew() {
        UserModel model = new UserModel("user-1");
        UserModelFact fact = UserModelFact.create("f1", "user-1", "preference",
                "language", "TypeScript", ConfidenceLevel.STATED, "user_explicit");
        model.merge(fact);

        assertTrue(model.get("language").isPresent());
        assertEquals("TypeScript", model.get("language").get().value());
        assertEquals(1, model.factCount());
    }

    @Test void userModel_mergeCase2_higherConfidenceUpgrades() {
        UserModel model = new UserModel("user-1");
        UserModelFact low  = UserModelFact.create("f1", "user-1", "expertise", "java", "beginner",
                ConfidenceLevel.INFERRED,  "agent_inferred");
        UserModelFact high = UserModelFact.create("f2", "user-1", "expertise", "java", "expert",
                ConfidenceLevel.VERIFIED,  "tool_verified");
        model.merge(low);
        model.merge(high);

        assertEquals("expert",              model.get("java").get().value());
        assertEquals(ConfidenceLevel.VERIFIED, model.get("java").get().confidence());
    }

    @Test void userModel_mergeCase3_lowerConfidenceRetained() {
        UserModel model = new UserModel("user-1");
        UserModelFact stated  = UserModelFact.create("f1", "user-1", "preference", "theme", "dark",
                ConfidenceLevel.STATED, "user_explicit");
        UserModelFact inferred = UserModelFact.create("f2", "user-1", "preference", "theme", "light",
                ConfidenceLevel.INFERRED, "agent_inferred");
        model.merge(stated);
        model.merge(inferred);

        // Case 3: stated is higher than inferred — retain stated
        assertEquals("dark",               model.get("theme").get().value());
        assertEquals(ConfidenceLevel.STATED, model.get("theme").get().confidence());
    }

    @Test void userModel_confidenceGatedByCategory() {
        UserModel model = new UserModel("user-1");
        model.merge(UserModelFact.create("f1", "user-1", "preference", "lang", "Java",
                ConfidenceLevel.STATED,     "user_explicit"));
        model.merge(UserModelFact.create("f2", "user-1", "preference", "ide",  "IntelliJ",
                ConfidenceLevel.INFERRED,   "agent_inferred"));
        model.merge(UserModelFact.create("f3", "user-1", "preference", "font", "FiraCode",
                ConfidenceLevel.SPECULATIVE,"agent_inferred"));

        // All 3 at speculative level
        assertEquals(3, model.getByCategory("preference", ConfidenceLevel.SPECULATIVE.value).size());
        // Only 2 at INFERRED level or above
        assertEquals(2, model.getByCategory("preference", ConfidenceLevel.INFERRED.value).size());
        // Only 1 at STATED level
        assertEquals(1, model.getByCategory("preference", ConfidenceLevel.STATED.value).size());
    }

    @Test void userModel_remove_supportsGdprErasure() {
        UserModel model = new UserModel("user-1");
        model.merge(UserModelFact.create("f1", "user-1", "pii", "email", "user@example.com",
                ConfidenceLevel.VERIFIED, "tool_verified"));
        assertTrue(model.remove("email"));
        assertTrue(model.get("email").isEmpty());
        assertEquals(0, model.factCount());
    }

    @Test void confidenceLevel_orderedByValue() {
        assertTrue(ConfidenceLevel.STATED.value     > ConfidenceLevel.VERIFIED.value);
        assertTrue(ConfidenceLevel.VERIFIED.value   > ConfidenceLevel.INFERRED.value);
        assertTrue(ConfidenceLevel.INFERRED.value   > ConfidenceLevel.SPECULATIVE.value);
        assertEquals(1.0, ConfidenceLevel.STATED.value, 0.001);
    }

    @Test void userModelFact_withConfidence_createsUpdatedFact() {
        UserModelFact f = UserModelFact.create("f1", "u1", "cat", "key", "val",
                ConfidenceLevel.INFERRED, "src");
        UserModelFact upgraded = f.withConfidence(ConfidenceLevel.VERIFIED);
        assertEquals(ConfidenceLevel.VERIFIED, upgraded.confidence());
        assertEquals("val", upgraded.value()); // value unchanged
    }

    // ── TaskCheckpoint ────────────────────────────────────────────────────────

    @Test void taskCheckpoint_initialCreation() {
        TaskCheckpoint cp = TaskCheckpoint.initial(
                "task-001", "user-1", "session-1",
                "Generate financial report",
                List.of("fetch_data", "validate", "format"),
                "Starting report generation — no prior context.");

        assertEquals("task-001",    cp.taskId());
        assertEquals("user-1",      cp.userId());
        assertEquals("session-1",   cp.sessionId());
        assertEquals(TaskStatus.IN_PROGRESS, cp.status());
        assertEquals(1,             cp.version());
        assertTrue(cp.completedSteps().isEmpty());
        assertEquals(3,             cp.nextSteps().size());
        assertEquals("fetch_data",  cp.nextSteps().get(0));
    }

    @Test void taskCheckpoint_suspend_incrementsVersion() {
        TaskCheckpoint v1 = TaskCheckpoint.initial("t1", "u1", "s1", "goal",
                List.of("step1", "step2"), "starting");
        TaskCheckpoint v2 = v1.suspend(
                List.of("step1"),
                List.of("step2"),
                List.of("Is data source available?"),
                "Completed step1; awaiting user input for step2.");

        assertEquals(2,              v2.version());
        assertEquals(TaskStatus.SUSPENDED, v2.status());
        assertEquals(1,              v2.completedSteps().size());
        assertEquals("step1",        v2.completedSteps().get(0));
        assertEquals(1,              v2.nextSteps().size());
        assertEquals(1,              v2.openQuestions().size());
    }

    @Test void taskCheckpoint_withArtefact_addsEntry() {
        TaskCheckpoint cp = TaskCheckpoint.initial("t1", "u1", "s1", "goal",
                List.of("step1"), "ctx");
        TaskCheckpoint updated = cp.withArtefact("report.pdf", "s3://bucket/report.pdf");

        assertEquals(2, updated.version());
        assertTrue(updated.artefacts().containsKey("report.pdf"));
        assertEquals("s3://bucket/report.pdf", updated.artefacts().get("report.pdf"));
    }

    @Test void taskCheckpoint_requiresVersionAtLeastOne() {
        assertThrows(IllegalArgumentException.class, () ->
            new TaskCheckpoint("t1", "u1", "s1", TaskStatus.IN_PROGRESS, "goal",
                    List.of(), List.of(), List.of(), java.util.Map.of(),
                    "ctx", java.time.Instant.now(), 0));
    }

    @Test void taskCheckpoint_immutableFields() {
        TaskCheckpoint cp = TaskCheckpoint.initial("t1", "u1", "s1", "goal",
                List.of("s1", "s2"), "ctx");
        assertThrows(UnsupportedOperationException.class, () -> cp.nextSteps().add("hack"));
    }

    // ── CheckpointStore ───────────────────────────────────────────────────────

    @Test void checkpointStore_saveAndLatest() {
        CheckpointStore store = new CheckpointStore();
        TaskCheckpoint cp = TaskCheckpoint.initial("t1", "u1", "s1", "goal",
                List.of("step1"), "ctx");
        store.save(cp);

        assertTrue(store.latest("t1").isPresent());
        assertEquals(1, store.latest("t1").get().version());
    }

    @Test void checkpointStore_latest_returnsNewest() {
        CheckpointStore store = new CheckpointStore();
        TaskCheckpoint v1 = TaskCheckpoint.initial("t1", "u1", "s1", "goal",
                List.of("step1", "step2"), "v1-ctx");
        TaskCheckpoint v2 = v1.suspend(List.of("step1"), List.of("step2"), List.of(), "v2-ctx");
        store.save(v1);
        store.save(v2);

        assertEquals(2, store.latest("t1").get().version());
        assertEquals(2, store.allVersions("t1").size());
    }

    @Test void checkpointStore_version_retrievesSpecific() {
        CheckpointStore store = new CheckpointStore();
        TaskCheckpoint v1 = TaskCheckpoint.initial("t1", "u1", "s1", "goal",
                List.of("a", "b", "c"), "ctx-v1");
        TaskCheckpoint v2 = v1.suspend(List.of("a"), List.of("b", "c"), List.of(), "ctx-v2");
        store.save(v1);
        store.save(v2);

        assertTrue(store.version("t1", 1).isPresent());
        assertEquals("ctx-v1", store.version("t1", 1).get().contextSummary());
    }

    @Test void checkpointStore_unknownTask_returnsEmpty() {
        CheckpointStore store = new CheckpointStore();
        assertTrue(store.latest("nonexistent").isEmpty());
        assertTrue(store.allVersions("nonexistent").isEmpty());
    }

    @Test void fullCheckpointLifecycle() {
        CheckpointStore store = new CheckpointStore();
        // Research task: 3 steps
        TaskCheckpoint v1 = TaskCheckpoint.initial("research-001", "user-42", "sess-1",
                "Research agent design patterns",
                List.of("search_web", "extract_findings", "synthesise_report"),
                "Beginning research on agent design patterns.");
        store.save(v1);

        // After first step completes — suspend for user review
        TaskCheckpoint v2 = v1.suspend(
                List.of("search_web"),
                List.of("extract_findings", "synthesise_report"),
                List.of("Should we focus on ReAct or event-driven?"),
                "Web search complete. Found 12 papers. Awaiting focus direction from user.");
        store.save(v2);

        // Add artefact
        TaskCheckpoint v3 = v2.withArtefact("search_results.json", "mem://episodic/search-001");
        store.save(v3);

        assertEquals(3, store.allVersions("research-001").size());
        assertEquals(3, store.latest("research-001").get().version());
        assertEquals(1, store.latest("research-001").get().artefacts().size());
        assertEquals(1, store.latest("research-001").get().completedSteps().size());
        assertEquals(2, store.latest("research-001").get().nextSteps().size());
    }
}
