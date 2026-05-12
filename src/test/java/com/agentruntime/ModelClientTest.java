package com.agentruntime;

import com.agentruntime.modelclient.*;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the ModelClient SPI and StubModelClient.
 * Validates the ported model-client layer.
 */
class ModelClientTest {

    @Test
    void stubClientReturnsDirect() throws ModelClientException {
        StubModelClient client = StubModelClient.direct("Hello from stub");
        ModelOutput output = client.generate(
                ModelPrompt.ofUser("What is AI?"),
                ModelRequestContext.of("session-1", "tenant-1"));

        assertNotNull(output);
        assertEquals("Hello from stub", output.text());
        assertFalse(output.hasToolCalls());
    }

    @Test
    void stubClientEmitsToolCallOnFirstInvocationWhenConfigured() throws ModelClientException {
        StubModelClient client = new StubModelClient("webSearch", "final answer");
        ModelRequestContext ctx = ModelRequestContext.of("s1", "t1");
        ModelPrompt prompt = ModelPrompt.ofUser("research agent systems");

        ModelOutput first = client.generate(prompt, ctx);
        assertTrue(first.hasToolCalls());
        assertEquals("webSearch", first.toolCalls().get(0).name());
        assertEquals(1, client.callCount());
    }

    @Test
    void stubClientReturnsFinalAnswerWhenToolResultPresent() throws ModelClientException {
        StubModelClient client = new StubModelClient("webSearch", "final answer");
        // Simulate context with a tool result already in working memory
        Map<String, Object> wm = Map.of("toolResult.webSearch", "some result");
        ModelRequestContext ctx = new ModelRequestContext("s1", "t1", wm);
        ModelPrompt prompt = ModelPrompt.ofUser("research");

        ModelOutput output = client.generate(prompt, ctx);
        assertEquals("final answer", output.text());
        assertFalse(output.hasToolCalls());
    }

    @Test
    void modelPromptOfUserCreatesCorrectStructure() {
        ModelPrompt prompt = ModelPrompt.ofUser("hello");
        assertEquals(1, prompt.messages().size());
        assertEquals(ModelMessage.Role.USER, prompt.messages().get(0).role());
        assertEquals("hello", prompt.messages().get(0).content());
    }

    @Test
    void modelPromptOfSystemAndUserCreatesCorrectStructure() {
        ModelPrompt prompt = ModelPrompt.of("You are helpful", "What is AI?");
        assertEquals(2, prompt.messages().size());
        assertEquals(ModelMessage.Role.SYSTEM, prompt.messages().get(0).role());
        assertEquals(ModelMessage.Role.USER,   prompt.messages().get(1).role());
    }

    @Test
    void modelClientExceptionRetryableFlagPreserved() {
        ModelClientException retryable    = new ModelClientException("timeout", true);
        ModelClientException nonRetryable = new ModelClientException("auth error", false);

        assertTrue(retryable.retryable());
        assertFalse(nonRetryable.retryable());
    }
}
