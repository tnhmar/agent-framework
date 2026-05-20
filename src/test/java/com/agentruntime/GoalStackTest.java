package com.agentruntime;

import com.agentruntime.statemanager.*;
import org.junit.jupiter.api.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for GoalStack — Vol.1 Ch.8 §"Goal Stack and Subgoal Tracking".
 */
class GoalStackTest {

    private GoalStack stack;

    @BeforeEach void setUp() { stack = new GoalStack(); }

    private GoalEntry entry(String id, String parentId, String desc, GoalStatus status) {
        return new GoalEntry(id, parentId, desc, status, "done when complete",
                List.of(), 10, 1000, null);
    }

    @Test void emptyStack_isEmpty() {
        assertTrue(stack.isEmpty());
        assertEquals(0, stack.size());
        assertTrue(stack.topLevel().isEmpty());
        assertTrue(stack.currentActive().isEmpty());
    }

    @Test void push_addsToStack() {
        stack.push(entry("g1", null, "top-level", GoalStatus.ACTIVE));
        assertEquals(1, stack.size());
        assertFalse(stack.isEmpty());
    }

    @Test void topLevel_returnsFirstPushed() {
        stack.push(entry("g1", null, "top", GoalStatus.ACTIVE));
        stack.push(entry("g2", "g1", "sub", GoalStatus.ACTIVE));
        assertEquals("g1", stack.topLevel().get().goalId());
    }

    @Test void currentActive_returnsMostRecentActive() {
        stack.push(entry("g1", null,  "top",    GoalStatus.COMPLETED));
        stack.push(entry("g2", "g1",  "middle", GoalStatus.ACTIVE));
        stack.push(entry("g3", "g2",  "deep",   GoalStatus.ACTIVE));
        assertEquals("g3", stack.currentActive().get().goalId());
    }

    @Test void updateStatus_changesGoalStatus() {
        stack.push(entry("g1", null, "goal", GoalStatus.PENDING));
        stack.updateStatus("g1", GoalStatus.ACTIVE);
        assertEquals(GoalStatus.ACTIVE, stack.findById("g1").get().status());
    }

    @Test void updateStatus_noOpForUnknownId() {
        stack.push(entry("g1", null, "goal", GoalStatus.PENDING));
        assertDoesNotThrow(() -> stack.updateStatus("unknown", GoalStatus.ACTIVE));
        assertEquals(GoalStatus.PENDING, stack.findById("g1").get().status());
    }

    @Test void byStatus_filtersCorrectly() {
        stack.push(entry("g1", null, "done",    GoalStatus.COMPLETED));
        stack.push(entry("g2", "g1", "active",  GoalStatus.ACTIVE));
        stack.push(entry("g3", "g1", "pending", GoalStatus.PENDING));
        stack.push(entry("g4", "g2", "failed",  GoalStatus.FAILED));

        assertEquals(1, stack.byStatus(GoalStatus.COMPLETED).size());
        assertEquals(1, stack.byStatus(GoalStatus.ACTIVE).size());
        assertEquals(1, stack.byStatus(GoalStatus.PENDING).size());
        assertEquals(1, stack.byStatus(GoalStatus.FAILED).size());
        assertEquals(0, stack.byStatus(GoalStatus.DEFERRED).size());
    }

    @Test void defer_setsStatusAndResumeCondition() {
        stack.push(entry("g1", null, "long-running", GoalStatus.ACTIVE));
        stack.defer("g1", "user_responds == true");
        GoalEntry g = stack.findById("g1").get();
        assertEquals(GoalStatus.DEFERRED, g.status());
        assertEquals("user_responds == true", g.resumeCondition());
    }

    @Test void goalsEntry_withStatus_preservesAllFields() {
        GoalEntry original = new GoalEntry("g1", "parent",  "desc", GoalStatus.PENDING,
                "success", List.of("dep1"), 5, 500, null);
        GoalEntry updated = original.withStatus(GoalStatus.ACTIVE);

        assertEquals(GoalStatus.ACTIVE,  updated.status());
        assertEquals("g1",              updated.goalId());
        assertEquals("parent",          updated.parentGoalId());
        assertEquals("desc",            updated.description());
        assertEquals(5,                 updated.allocatedSteps());
        assertEquals(500,               updated.allocatedTokens());
        assertEquals(List.of("dep1"),   updated.dependencies());
    }

    @Test void isAligned_trueWhenActiveDescendsFromTopLevel() {
        stack.push(entry("g1", null, "top",    GoalStatus.ACTIVE));
        stack.push(entry("g2", "g1", "sub",    GoalStatus.PENDING));
        stack.push(entry("g3", "g2", "subsub", GoalStatus.ACTIVE));
        assertTrue(stack.isAligned());
    }

    @Test void isAligned_trueWhenEmpty() {
        assertTrue(stack.isAligned());
    }

    @Test void all_returnsCopy() {
        stack.push(entry("g1", null, "g", GoalStatus.ACTIVE));
        List<GoalEntry> all = stack.all();
        assertEquals(1, all.size());
        assertThrows(UnsupportedOperationException.class, () -> all.add(entry("g2", null, "x", GoalStatus.PENDING)));
    }

    @Test void fullGoalStackLifecycle() {
        // Simulate: top → subgoal A → subgoal A.1 → complete A.1 → complete A → done
        stack.push(entry("mission",     null,      "Generate daily report",     GoalStatus.ACTIVE));
        stack.push(entry("fetch-data",  "mission", "Fetch portfolio data",       GoalStatus.PENDING));
        stack.push(entry("validate",    "mission", "Validate data integrity",    GoalStatus.PENDING));

        stack.updateStatus("fetch-data", GoalStatus.ACTIVE);
        assertEquals(GoalStatus.ACTIVE, stack.findById("fetch-data").get().status());

        stack.updateStatus("fetch-data", GoalStatus.COMPLETED);
        stack.updateStatus("validate",   GoalStatus.ACTIVE);

        assertEquals("validate", stack.currentActive().get().goalId(),
                "validate must be the current active goal");

        stack.updateStatus("validate", GoalStatus.COMPLETED);
        stack.updateStatus("mission",  GoalStatus.COMPLETED);

        assertEquals(3, stack.byStatus(GoalStatus.COMPLETED).size());
        assertEquals(0, stack.byStatus(GoalStatus.ACTIVE).size());
    }
}
