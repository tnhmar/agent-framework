package com.agentruntime.observability;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
public class MetricsCollector {
    private final Map<String, AtomicLong> counters = new ConcurrentHashMap<>();
    private final Map<String, List<Long>> histograms = new ConcurrentHashMap<>();
    public void increment(String metric) { counters.computeIfAbsent(metric, k -> new AtomicLong(0)).incrementAndGet(); }
    public void incrementBy(String metric, long amount) { counters.computeIfAbsent(metric, k -> new AtomicLong(0)).addAndGet(amount); }
    public void record(String metric, long value) { histograms.computeIfAbsent(metric, k -> new ArrayList<>()).add(value); }
    public long getCount(String metric) { var c = counters.get(metric); return c == null ? 0 : c.get(); }
    public OptionalDouble average(String metric) { var vals = histograms.get(metric); if (vals == null || vals.isEmpty()) return OptionalDouble.empty(); return vals.stream().mapToLong(Long::longValue).average(); }
    public Map<String, Long> snapshot() { var snap = new HashMap<String, Long>(); counters.forEach((k, v) -> snap.put(k, v.get())); return snap; }
}
