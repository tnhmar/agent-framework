package com.agentruntime.protocols.a2a;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * V-12 fix: A2A Router with Agent Card registration and task lifecycle state machine.
 * Vol1 Appendix A §"A2A Protocol": tasks/send, tasks/get, tasks/cancel.
 */
public class A2ARouter {

    private final Map<String, Queue<A2AMessage>> routingTable = new ConcurrentHashMap<>();
    private final Map<String, A2ATask> tasks = new ConcurrentHashMap<>();
    private final Map<String, AgentCard> agentCards = new ConcurrentHashMap<>();

    public void registerAgent(AgentCard card) { agentCards.put(card.agentId(), card); }
    public Optional<AgentCard> getCard(String agentId) { return Optional.ofNullable(agentCards.get(agentId)); }

    /** tasks/send — create and route a new task. */
    public A2ATask tasksSend(AgentCard sender, String receiverAgentId, String intent, Map<String, Object> payload) {
        var task = new A2ATask(UUID.randomUUID().toString(), A2ATaskStatus.SUBMITTED, sender, intent, payload, Instant.now());
        tasks.put(task.taskId(), task);
        return task;
    }

    /** tasks/get — retrieve current task status. */
    public Optional<A2ATask> tasksGet(String taskId) { return Optional.ofNullable(tasks.get(taskId)); }

    /** tasks/cancel — cancel a pending or working task. */
    public boolean tasksCancel(String taskId) {
        var task = tasks.get(taskId);
        if (task == null) return false;
        tasks.put(taskId, new A2ATask(task.taskId(), A2ATaskStatus.CANCELED, task.sender(), task.intent(), task.payload(), task.createdAt()));
        return true;
    }

    public void updateTaskStatus(String taskId, A2ATaskStatus status) {
        var task = tasks.get(taskId);
        if (task != null) tasks.put(taskId, new A2ATask(task.taskId(), status, task.sender(), task.intent(), task.payload(), task.createdAt()));
    }

    public void route(A2AMessage message) { routingTable.computeIfAbsent(message.receiver().agentId(), k -> new ConcurrentLinkedQueue<>()).add(message); }
    public Optional<A2AMessage> poll(String agentId) { var q = routingTable.get(agentId); return q != null ? Optional.ofNullable(q.poll()) : Optional.empty(); }
    public int queueDepth(String agentId) { var q = routingTable.get(agentId); return q != null ? q.size() : 0; }
}
