package dev.jevcraft.core;

import java.time.Instant;
import java.util.*;

public final class MemoryStore {
    public record Memory(UUID companion, String kind, String value, Instant observed, double certainty, UUID sourcePlayer, boolean privateMessage) {}
    private final Map<UUID, List<Memory>> byCompanion = new HashMap<>();
    public synchronized void remember(Memory memory) { byCompanion.computeIfAbsent(memory.companion(), ignored -> new ArrayList<>()).add(memory); }
    public synchronized List<Memory> retrieve(UUID companion, String query, int limit) {
        String lower = query.toLowerCase(Locale.ROOT);
        return byCompanion.getOrDefault(companion, List.of()).stream()
                .filter(m -> m.value().toLowerCase(Locale.ROOT).contains(lower) || m.kind().toLowerCase(Locale.ROOT).contains(lower))
                .sorted(Comparator.comparing(Memory::observed).reversed()).limit(limit).toList();
    }
    public synchronized List<Memory> exportPublic(UUID companion) {
        return byCompanion.getOrDefault(companion, List.of()).stream().filter(m -> !m.privateMessage()).toList();
    }
}
