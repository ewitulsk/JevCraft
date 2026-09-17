package dev.jevcraft.companion;

import dev.jevcraft.JevCraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ForcedChunksSavedData;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.common.world.chunk.TicketController;

import java.util.UUID;

public final class MovingChunkTickets {
    private static final TicketController CONTROLLER = new TicketController(
            ResourceLocation.fromNamespaceAndPath(JevCraft.MOD_ID, "companions"),
            (level, tickets) -> {
                JevWorldData roster = JevWorldData.get(level.getServer());
                for (UUID owner : tickets.getEntityTickets().keySet()) if (!roster.containsLiving(owner)) tickets.removeAllTickets(owner);
            });
    private MovingChunkTickets() {}
    public static void register(RegisterTicketControllersEvent event) { event.register(CONTROLLER); }
    public static void move(ServerLevel level, JevCompanion owner, int oldX, int oldZ, int newX, int newZ, int radius) {
        for (int x = newX - radius; x <= newX + radius; x++) for (int z = newZ - radius; z <= newZ + radius; z++)
            if (oldX == Integer.MIN_VALUE || Math.abs(x - oldX) > radius || Math.abs(z - oldZ) > radius) CONTROLLER.forceChunk(level, owner, x, z, true, true);
        if (oldX != Integer.MIN_VALUE) for (int x = oldX - radius; x <= oldX + radius; x++) for (int z = oldZ - radius; z <= oldZ + radius; z++)
            if (Math.abs(x - newX) > radius || Math.abs(z - newZ) > radius) CONTROLLER.forceChunk(level, owner, x, z, false, true);
    }
    public static void release(ServerLevel level, JevCompanion owner, int xCenter, int zCenter, int radius) {
        for (int x = xCenter - radius; x <= xCenter + radius; x++) for (int z = zCenter - radius; z <= zCenter + radius; z++)
            CONTROLLER.forceChunk(level, owner, x, z, false, true);
    }
    public static void prepare(ServerLevel level, JevCompanion owner, int xCenter, int zCenter, int radius) {
        for (int x = xCenter - radius; x <= xCenter + radius; x++) for (int z = zCenter - radius; z <= zCenter + radius; z++)
            CONTROLLER.forceChunk(level, owner, x, z, true, true);
    }
    public static int tickingTicketCount(ServerLevel level) {
        ForcedChunksSavedData data = level.getDataStorage().get(ForcedChunksSavedData.factory(), "chunks");
        if (data == null) return 0;
        return data.getEntityForcedChunks().getTickingChunks().values().stream().mapToInt(set -> set.size()).sum();
    }
}
