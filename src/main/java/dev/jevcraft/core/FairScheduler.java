package dev.jevcraft.core;

import java.time.Instant;
import java.util.*;

public final class FairScheduler {
    public record Work(UUID actor, int urgency, Instant deadline, long sequence) {}
    private final int maxActors;
    private final int maxConcurrent;
    private final Map<UUID, Work> newest = new LinkedHashMap<>();
    private final Set<UUID> running = new HashSet<>();
    public FairScheduler(int maxActors, int maxConcurrent) {
        if (maxActors < 1 || maxActors > 10 || maxConcurrent < 1 || maxConcurrent > maxActors) throw new IllegalArgumentException();
        this.maxActors = maxActors; this.maxConcurrent = maxConcurrent;
    }
    public synchronized void offer(Work work) {
        if (!newest.containsKey(work.actor()) && newest.size() + running.size() >= maxActors) throw new IllegalStateException("ten-controller cap reached");
        newest.put(work.actor(), work);
    }
    public synchronized Optional<Work> poll(Instant now) {
        newest.entrySet().removeIf(e -> e.getValue().deadline().isBefore(now));
        if (running.size() >= maxConcurrent || newest.isEmpty()) return Optional.empty();
        Work selected = newest.values().stream().filter(w -> !running.contains(w.actor()))
                .max(Comparator.comparingInt(Work::urgency).thenComparingLong(w -> -w.sequence())).orElse(null);
        if (selected == null) return Optional.empty();
        newest.remove(selected.actor()); running.add(selected.actor()); return Optional.of(selected);
    }
    public synchronized void complete(UUID actor) { running.remove(actor); }
    public synchronized void cancel(UUID actor) { newest.remove(actor); running.remove(actor); }
    public synchronized int queued() { return newest.size(); }
    public synchronized int running() { return running.size(); }
}
