package dev.jevcraft.core;

import java.time.*;
import java.util.*;

public final class ChatRouter {
    public record Message(long sequence, UUID sender, String text, boolean authorized, boolean jevAuthored, Instant created) {}
    private final int capacity;
    private final Duration instructionTtl;
    private final Deque<Message> messages = new ArrayDeque<>();
    private final Map<UUID, Long> cursors = new HashMap<>();
    private long sequence;
    public ChatRouter(int capacity, Duration instructionTtl) { this.capacity = capacity; this.instructionTtl = instructionTtl; }
    public synchronized Message publish(UUID sender, String text, boolean authorized, boolean jevAuthored, Instant now) {
        String bounded = Objects.requireNonNull(text).strip();
        if (bounded.length() > 512) bounded = bounded.substring(0, 512);
        Message message = new Message(++sequence, sender, bounded, authorized, jevAuthored, now);
        messages.addLast(message); while (messages.size() > capacity) messages.removeFirst(); return message;
    }
    public synchronized List<Message> drain(UUID companion, Instant now) {
        long after = cursors.getOrDefault(companion, 0L);
        List<Message> result = messages.stream().filter(m -> m.sequence() > after)
                .filter(m -> !m.jevAuthored()).toList();
        cursors.put(companion, sequence); return result;
    }
    public boolean executable(Message message, Instant now) {
        return message.authorized() && !message.jevAuthored()
                && Duration.between(message.created(), now).compareTo(instructionTtl) <= 0;
    }
}
