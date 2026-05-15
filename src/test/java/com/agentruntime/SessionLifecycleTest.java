package com.agentruntime;

import com.agentruntime.memory.consolidation.MemoryConsolidator;
import com.agentruntime.memory.episodic.EpisodicStore;
import com.agentruntime.memory.expiry.ExpiryPruner;
import com.agentruntime.memory.semantic.SemanticStore;
import com.agentruntime.memory.session.SessionLifecycleManager;
import com.agentruntime.memory.working.WorkingMemoryStore;
import org.junit.jupiter.api.*;
import java.time.Duration;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/** Tests for V-10: 4-step session-close handoff in strict order. */
class SessionLifecycleTest {

    private SessionLifecycleManager manager;
    private WorkingMemoryStore working;
    private EpisodicStore episodic;
    private SemanticStore semantic;

    @BeforeEach
    void setUp() {
        episodic = new EpisodicStore();
        semantic = new SemanticStore();
        working = new WorkingMemoryStore();
        var consolidator = new MemoryConsolidator(episodic, semantic);
        var pruner = new ExpiryPruner(Duration.ofDays(1));
        manager = new SessionLifecycleManager(consolidator, working, pruner, episodic);
    }

    @Test
    void sessionClose_returnsReport() {
        var report = manager.onSessionClose("session-1", "agent-1");
        assertNotNull(report);
        assertEquals("session-1", report.sessionId());
        assertEquals("agent-1", report.agentId());
    }

    @Test
    void sessionClose_flushesWorkingMemory() {
        working.put("k1", "v1");
        working.put("k2", "v2");
        assertEquals(2, working.size());
        manager.onSessionClose("session-2", "agent-2");
        assertEquals(0, working.size(), "Step 3: working memory must be flushed on session close");
    }

    @Test
    void sessionClose_consolidatesEpisodicToSemantic() {
        String agentId = "agent-3";
        episodic.store(agentId, "Portfolio A exposure computed successfully", Map.of());
        episodic.store(agentId, "Risk limits validated for Q3", Map.of());
        int beforeSemantic = semantic.all().size();
        var report = manager.onSessionClose("session-3", agentId);
        assertTrue(report.memoriesConsolidated() > 0, "Step 1: episodic memories must be consolidated");
        assertTrue(semantic.all().size() > beforeSemantic, "Semantic store must grow after consolidation");
    }

    @Test
    void sessionClose_providesTaskStateCheckpoint() {
        var report = manager.onSessionClose("session-4", "agent-4");
        assertNotNull(report.taskStateCheckpointRef(), "Step 2: task state checkpoint must be produced");
        assertTrue(report.taskStateCheckpointRef().contains("session-4"), "Checkpoint must reference session ID");
    }

    @Test
    void sessionClose_reportContainsEpisodesExpiredCount() {
        var report = manager.onSessionClose("session-5", "agent-5");
        assertTrue(report.episodesExpired() >= 0, "Step 4: expired episode count must be non-negative");
    }

    @Test
    void sessionClose_workingMemoryEmptyAfterSecondClose() {
        working.put("data", "value");
        manager.onSessionClose("s1", "a");
        working.put("newData", "newValue");
        manager.onSessionClose("s2", "a");
        assertEquals(0, working.size());
    }
}
