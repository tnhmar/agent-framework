package com.agentruntime;

import com.agentruntime.core.valueobjects.AgentIdentity;
import com.agentruntime.protocols.a2a.*;
import com.agentruntime.protocols.anp.*;
import com.agentruntime.protocols.mcp.*;
import org.junit.jupiter.api.*;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Tests for V-11 (MCP JSON-RPC), V-12 (A2A task lifecycle), V-13 (ANP identity + pub/sub). */
class ProtocolsTest {

    // --- V-11: MCP ---

    @Test
    void mcp_initializationHandshake() {
        var client = new McpClient("client-1");
        assertFalse(client.isInitialized());
        var response = client.initialize(Map.of("tools", true));
        assertTrue(client.isInitialized());
        assertTrue(response.isSuccess());
        assertEquals("2.0", response.jsonrpc());
    }

    @Test
    void mcp_toolsCallRequiresInitialization() {
        var client = new McpClient("client-2");
        var response = client.toolsCall("some_tool", Map.of());
        assertFalse(response.isSuccess());
        assertNotNull(response.error());
        assertEquals(-32002, response.error().code());
    }

    @Test
    void mcp_toolsCallSucceedsAfterInit() {
        var client = new McpClient("client-3");
        client.initialize(Map.of());
        // Register a handler — required since S-03 fix (no more silent success)
        client.registerHandler("retrieve_context",
                (name, args) -> Map.of("result", "context for: " + args.get("query")));
        var response = client.toolsCall("retrieve_context", Map.of("query", "test"));
        assertTrue(response.isSuccess());
        assertNull(response.error());
    }

    @Test
    void mcpRequest_hasJsonRpc20Structure() {
        var req = McpRequest.of("tools/list", Map.of());
        assertEquals("2.0", req.jsonrpc());
        assertEquals("tools/list", req.method());
        assertNotNull(req.id());
    }

    @Test
    void mcpResponse_successAndErrorDistinct() {
        var success = McpResponse.success("result", "1");
        var error = McpResponse.error(new McpError(-32600, "Invalid request", null), "2");
        assertTrue(success.isSuccess());
        assertFalse(error.isSuccess());
        assertNull(success.error());
        assertNull(error.result());
    }

    // --- V-12: A2A ---

    @Test
    void a2a_agentCardRegistration() {
        var router = new A2ARouter();
        var card = new AgentCard("agent-1", "Compliance Bot", "Handles compliance", List.of("report"), "http://a1");
        router.registerAgent(card);
        assertTrue(router.getCard("agent-1").isPresent());
        assertEquals("Compliance Bot", router.getCard("agent-1").get().name());
    }

    @Test
    void a2a_tasksSend_createsSubmittedTask() {
        var router = new A2ARouter();
        var sender = new AgentCard("sender", "Sender", "desc", List.of(), "http://s");
        var task = router.tasksSend(sender, "receiver-1", "compute_exposure", Map.of("portfolio", "A"));
        assertNotNull(task.taskId());
        assertEquals(A2ATaskStatus.SUBMITTED, task.status());
        assertEquals(sender, task.sender());
    }

    @Test
    void a2a_tasksGet_returnsCurrentStatus() {
        var router = new A2ARouter();
        var sender = new AgentCard("s","S","d", List.of(),"http://s");
        var task = router.tasksSend(sender, "r", "intent", Map.of());
        var retrieved = router.tasksGet(task.taskId());
        assertTrue(retrieved.isPresent());
        assertEquals(task.taskId(), retrieved.get().taskId());
    }

    @Test
    void a2a_tasksCancel_setsStatusCanceled() {
        var router = new A2ARouter();
        var sender = new AgentCard("s","S","d", List.of(),"http://s");
        var task = router.tasksSend(sender, "r", "intent", Map.of());
        boolean canceled = router.tasksCancel(task.taskId());
        assertTrue(canceled);
        assertEquals(A2ATaskStatus.CANCELED, router.tasksGet(task.taskId()).get().status());
    }

    @Test
    void a2a_taskStatusTransitions() {
        var router = new A2ARouter();
        var sender = new AgentCard("s","S","d", List.of(),"http://s");
        var task = router.tasksSend(sender, "r", "intent", Map.of());
        router.updateTaskStatus(task.taskId(), A2ATaskStatus.WORKING);
        assertEquals(A2ATaskStatus.WORKING, router.tasksGet(task.taskId()).get().status());
        router.updateTaskStatus(task.taskId(), A2ATaskStatus.COMPLETED);
        assertEquals(A2ATaskStatus.COMPLETED, router.tasksGet(task.taskId()).get().status());
    }

    @Test
    void a2a_taskStatusEnum_hasAllRequiredValues() {
        var values = EnumSet.allOf(A2ATaskStatus.class);
        assertTrue(values.contains(A2ATaskStatus.SUBMITTED));
        assertTrue(values.contains(A2ATaskStatus.WORKING));
        assertTrue(values.contains(A2ATaskStatus.INPUT_REQUIRED));
        assertTrue(values.contains(A2ATaskStatus.COMPLETED));
        assertTrue(values.contains(A2ATaskStatus.FAILED));
        assertTrue(values.contains(A2ATaskStatus.CANCELED));
    }

    // --- V-13: ANP ---

    @Test
    void anp_identityHasDid() {
        var identity = AnpIdentity.of("did:example:agent1");
        assertEquals("did:example:agent1", identity.did());
        assertNotNull(identity.verificationMethod());
    }

    @Test
    void anp_publishReceivesOnSubscribedTopic() {
        var identity = AnpIdentity.of("did:example:publisher");
        var publisher = new AnpPublisher(identity);
        var received = new ArrayList<AnpEnvelope>();
        publisher.subscribe("compliance.events", msg -> received.add(msg));
        assertDoesNotThrow(() -> publisher.publish("compliance.events", Map.of("event", "exposure_computed")));
        assertEquals(1, received.size());
        assertEquals("compliance.events", received.get(0).networkTopic());
        assertEquals(identity.did(), received.get(0).sourceNode());
    }

    @Test
    void anp_topicIsolation_noLeakBetweenTopics() {
        var identity = AnpIdentity.of("did:example:pub");
        var publisher = new AnpPublisher(identity);
        var topicA = new ArrayList<AnpEnvelope>();
        var topicB = new ArrayList<AnpEnvelope>();
        publisher.subscribe("topic.a", msg -> topicA.add(msg));
        publisher.subscribe("topic.b", msg -> topicB.add(msg));
        assertDoesNotThrow(() -> publisher.publish("topic.a", Map.of("data", "forA")));
        assertEquals(1, topicA.size());
        assertEquals(0, topicB.size());
    }

    @Test
    void anp_multipleSubscribersOnSameTopic() {
        var pub = new AnpPublisher(AnpIdentity.of("did:example:p"));
        var sub1 = new ArrayList<AnpEnvelope>();
        var sub2 = new ArrayList<AnpEnvelope>();
        pub.subscribe("shared.topic", msg -> sub1.add(msg));
        pub.subscribe("shared.topic", msg -> sub2.add(msg));
        assertDoesNotThrow(() -> pub.publish("shared.topic", Map.of()));
        assertEquals(1, sub1.size());
        assertEquals(1, sub2.size());
    }
}
