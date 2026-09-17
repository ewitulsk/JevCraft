package dev.jevcraft.companion;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.*;

/** World-global identities, living-cap accounting, unique names, and exactly-once starter grants. */
public final class JevWorldData extends SavedData {
    public enum GrantState { PENDING, DELIVERED, CONSUMED }
    private static final String FILE = "jevcraft_roster";
    private static final Factory<JevWorldData> FACTORY = new Factory<>(JevWorldData::new, JevWorldData::load, DataFixTypes.LEVEL);
    private final Map<UUID, String> living = new LinkedHashMap<>();
    private final Map<UUID, GrantState> grants = new LinkedHashMap<>();

    public static JevWorldData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, FILE);
    }
    public boolean canRegister(UUID id) { return living.containsKey(id) || living.size() < 10; }
    public boolean register(UUID id, String preferredName) {
        if (living.containsKey(id)) return true;
        if (living.size() >= 10) return false;
        living.put(id, uniqueName(preferredName, id)); setDirty(); return true;
    }
    public void unregister(UUID id) { if (living.remove(id) != null) setDirty(); }
    public int livingCount() { return living.size(); }
    public boolean containsLiving(UUID id) { return living.containsKey(id); }
    public String name(UUID id) { return living.get(id); }
    public boolean nameInUse(String name, UUID except) {
        return living.entrySet().stream().anyMatch(e -> !e.getKey().equals(except) && e.getValue().equalsIgnoreCase(name));
    }
    private String uniqueName(String preferred, UUID id) {
        String base = preferred == null || preferred.isBlank() ? "Jev_" + id.toString().substring(0, 6) : preferred;
        if (!nameInUse(base, id)) return base;
        for (int suffix = 2; suffix < 1000; suffix++) {
            String candidate = base.substring(0, Math.min(base.length(), Math.max(1, 16 - Integer.toString(suffix).length() - 1))) + "_" + suffix;
            if (!nameInUse(candidate, id)) return candidate;
        }
        return "Jev_" + id.toString().replace("-", "").substring(0, 11);
    }
    public GrantState grant(UUID player) { return grants.computeIfAbsent(player, ignored -> { setDirty(); return GrantState.PENDING; }); }
    public void grantDelivered(UUID player) { grants.put(player, GrantState.DELIVERED); setDirty(); }
    public void grantConsumed(UUID player) { if (grants.get(player) == GrantState.DELIVERED) { grants.put(player, GrantState.CONSUMED); setDirty(); } }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag roster = new ListTag();
        living.forEach((id, name) -> { CompoundTag e = new CompoundTag(); e.putUUID("Id", id); e.putString("Name", name); roster.add(e); });
        tag.put("Living", roster);
        ListTag grantList = new ListTag();
        grants.forEach((id, state) -> { CompoundTag e = new CompoundTag(); e.putUUID("Player", id); e.putString("State", state.name()); grantList.add(e); });
        tag.put("Grants", grantList); return tag;
    }
    private static JevWorldData load(CompoundTag tag, HolderLookup.Provider registries) {
        JevWorldData data = new JevWorldData();
        for (var value : tag.getList("Living", 10)) if (value instanceof CompoundTag e && e.hasUUID("Id")) data.living.put(e.getUUID("Id"), e.getString("Name"));
        for (var value : tag.getList("Grants", 10)) if (value instanceof CompoundTag e && e.hasUUID("Player")) {
            try { data.grants.put(e.getUUID("Player"), GrantState.valueOf(e.getString("State"))); } catch (IllegalArgumentException ignored) {}
        }
        return data;
    }
}
