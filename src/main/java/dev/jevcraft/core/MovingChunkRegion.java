package dev.jevcraft.core;

import java.util.*;

public final class MovingChunkRegion {
    public record Chunk(int x, int z) {}
    public record Delta(Set<Chunk> addFirst, Set<Chunk> releaseAfterReady) {
        public Delta { addFirst = Set.copyOf(addFirst); releaseAfterReady = Set.copyOf(releaseAfterReady); }
    }
    private final int radius;
    private Set<Chunk> held = Set.of();
    public MovingChunkRegion(int radius) { if (radius < 0 || radius > 32) throw new IllegalArgumentException(); this.radius = radius; }
    public synchronized Delta moveTo(Chunk center) {
        Set<Chunk> target = square(center, radius);
        Set<Chunk> add = new HashSet<>(target); add.removeAll(held);
        Set<Chunk> release = new HashSet<>(held); release.removeAll(target);
        return new Delta(add, release);
    }
    public synchronized void commit(Chunk center) { held = square(center, radius); }
    public synchronized int footprint() { return held.size(); }
    public static Set<Chunk> square(Chunk center, int radius) {
        Set<Chunk> result = new LinkedHashSet<>();
        for (int x = center.x() - radius; x <= center.x() + radius; x++)
            for (int z = center.z() - radius; z <= center.z() + radius; z++) result.add(new Chunk(x, z));
        return result;
    }
}
