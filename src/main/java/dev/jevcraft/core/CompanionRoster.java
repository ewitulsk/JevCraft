package dev.jevcraft.core;

import java.util.*;

public final class CompanionRoster {
    public enum GrantState { PENDING, DELIVERED, CONSUMED }
    public record Companion(UUID id, String name, UUID owner, boolean living) {}
    private final int cap;
    private final Map<UUID, Companion> companions = new LinkedHashMap<>();
    private final Map<String, UUID> names = new HashMap<>();
    private final Map<UUID, GrantState> grants = new HashMap<>();
    public CompanionRoster(int cap) { if (cap < 1 || cap > 10) throw new IllegalArgumentException(); this.cap = cap; }
    public synchronized void register(Companion companion) {
        String key = normalize(companion.name());
        if (companions.containsKey(companion.id())) return;
        if (livingCount() >= cap && companion.living()) throw new IllegalStateException("companion cap reached");
        if (names.putIfAbsent(key, companion.id()) != null) throw new IllegalArgumentException("name already in use");
        companions.put(companion.id(), companion);
    }
    public synchronized void updateLiving(UUID id, boolean living) {
        Companion old = require(id);
        if (!old.living() && living && livingCount() >= cap) throw new IllegalStateException("companion cap reached");
        companions.put(id, new Companion(id, old.name(), old.owner(), living));
    }
    public synchronized GrantState ensureGrant(UUID player) { return grants.computeIfAbsent(player, ignored -> GrantState.PENDING); }
    public synchronized boolean deliverGrant(UUID player, boolean inventoryHasSpace) {
        if (ensureGrant(player) != GrantState.PENDING || !inventoryHasSpace) return false;
        grants.put(player, GrantState.DELIVERED); return true;
    }
    public synchronized boolean consumeGrant(UUID player) {
        if (grants.get(player) != GrantState.DELIVERED) return false;
        grants.put(player, GrantState.CONSUMED); return true;
    }
    public synchronized Optional<Companion> findByName(String name) { return Optional.ofNullable(companions.get(names.get(normalize(name)))); }
    public synchronized int livingCount() { return (int) companions.values().stream().filter(Companion::living).count(); }
    private Companion require(UUID id) { Companion c = companions.get(id); if (c == null) throw new NoSuchElementException(); return c; }
    private static String normalize(String name) {
        String result = Objects.requireNonNull(name).strip().toLowerCase(Locale.ROOT);
        if (!result.matches("[a-z0-9_]{1,16}")) throw new IllegalArgumentException("invalid companion name");
        return result;
    }
}
