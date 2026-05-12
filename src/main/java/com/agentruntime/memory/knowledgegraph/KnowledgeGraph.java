package com.agentruntime.memory.knowledgegraph;

import com.agentruntime.core.valueobjects.MemoryRecordId;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Knowledge graph store — one of the 5 Vol.1 Ch.10 memory types.
 * Thread-safe: ConcurrentHashMap for nodes, CopyOnWriteArrayList for edges.
 */
public class KnowledgeGraph {

    private final Map<String, KgNode>  nodes = new ConcurrentHashMap<>();
    private final List<KgEdge>         edges = new CopyOnWriteArrayList<>();

    public void addNode(KgNode node) {
        Objects.requireNonNull(node, "node must not be null");
        nodes.put(node.id().value(), node);
    }

    public void addEdge(KgEdge edge) {
        Objects.requireNonNull(edge, "edge must not be null");
        edges.add(edge);
    }

    public Optional<KgNode> findNode(MemoryRecordId id) {
        Objects.requireNonNull(id);
        return Optional.ofNullable(nodes.get(id.value()));
    }

    public List<KgNode> findByLabel(String label) {
        Objects.requireNonNull(label);
        return nodes.values().stream()
                .filter(n -> n.label().equalsIgnoreCase(label))
                .toList();
    }

    public List<KgEdge> edgesFrom(MemoryRecordId nodeId) {
        Objects.requireNonNull(nodeId);
        return edges.stream().filter(e -> e.fromNode().equals(nodeId)).toList();
    }

    public List<KgEdge> edgesTo(MemoryRecordId nodeId) {
        Objects.requireNonNull(nodeId);
        return edges.stream().filter(e -> e.toNode().equals(nodeId)).toList();
    }

    public int nodeCount() { return nodes.size(); }
    public int edgeCount() { return edges.size(); }

    public Collection<KgNode> allNodes() {
        return Collections.unmodifiableCollection(nodes.values());
    }
}
