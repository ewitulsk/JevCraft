package dev.jevcraft.core;

import java.util.*;

public final class InventoryTransaction {
    public enum Click { PICKUP, PLACE, SPLIT, QUICK_MOVE, HOTBAR_SWAP, DROP, CRAFT_OUTPUT, BUTTON }
    public enum State { NEW, APPLIED, ACKNOWLEDGED, VERIFIED, ABORTED, UNCERTAIN }
    public record Slot(int index, String item, int count) {}
    public record Snapshot(int menuId, long revision, Map<Integer, Slot> slots, Slot cursor) {
        public Snapshot { slots = Map.copyOf(slots); }
    }
    public record Operation(Click click, int source, int destination, int amount) {}
    private final Snapshot before;
    private final Operation operation;
    private State state = State.NEW;
    public InventoryTransaction(Snapshot before, Operation operation) { this.before = before; this.operation = operation; }
    public synchronized void applied() { transition(State.NEW, State.APPLIED); }
    public synchronized void acknowledged(int menuId, long revision) {
        if (state != State.APPLIED || menuId != before.menuId()) { state = State.ABORTED; return; }
        if (revision <= before.revision()) { state = State.UNCERTAIN; return; }
        state = State.ACKNOWLEDGED;
    }
    public synchronized boolean verify(Snapshot after) {
        if (state != State.ACKNOWLEDGED || after.menuId() != before.menuId()) { state = State.ABORTED; return false; }
        boolean changed = !before.slots().equals(after.slots()) || !Objects.equals(before.cursor(), after.cursor());
        state = changed ? State.VERIFIED : State.ABORTED; return changed;
    }
    public synchronized void concurrentChange() { if (state != State.VERIFIED) state = State.ABORTED; }
    public synchronized State state() { return state; }
    public Operation operation() { return operation; }
    private void transition(State expected, State next) { if (state != expected) throw new IllegalStateException(state.name()); state = next; }
}
