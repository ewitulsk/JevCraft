package dev.jevcraft.companion;

import dev.jevcraft.core.GoalParser;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.UUID;

public final class JevCompanion extends PathfinderMob {
    private final SimpleContainer inventory = new SimpleContainer(36);
    private UUID owner;
    private String currentGoal = "";
    private long goalVersion;
    private int food = 20;
    private int lastChunkX = Integer.MIN_VALUE, lastChunkZ = Integer.MIN_VALUE;

    public JevCompanion(EntityType<? extends PathfinderMob> type, Level level) { super(type, level); setPersistenceRequired(); }
    @Override protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(5, new RandomStrollGoal(this, .8));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }
    @Override public void tick() {
        super.tick();
        if (!level().isClientSide && level() instanceof ServerLevel serverLevel) {
            if (tickCount % 1200 == 0 && !currentGoal.isBlank()) food = Math.max(0, food - 1);
            int cx = chunkPosition().x, cz = chunkPosition().z;
            if (cx != lastChunkX || cz != lastChunkZ) {
                MovingChunkTickets.move(serverLevel, this, lastChunkX, lastChunkZ, cx, cz, 2);
                lastChunkX = cx; lastChunkZ = cz;
            }
            applySimpleGoal(serverLevel);
        }
    }
    private void applySimpleGoal(ServerLevel level) {
        if (currentGoal.isBlank() || owner == null) return;
        GoalParser.Goal parsed = new GoalParser().parse(currentGoal);
        if (parsed.kind() == GoalParser.Kind.FOLLOW) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(owner);
            if (player != null && player.level() == level && distanceToSqr(player) > 9) getNavigation().moveTo(player, 1);
        }
    }
    public void setOwner(UUID owner) { this.owner = owner; }
    public UUID owner() { return owner; }
    public boolean canCommand(ServerPlayer player) { return owner != null && (owner.equals(player.getUUID()) || player.hasPermissions(2)); }
    public void acceptGoal(String goal) { currentGoal = goal.strip(); goalVersion++; getNavigation().stop(); }
    public void stopNow() { currentGoal = ""; goalVersion++; getNavigation().stop(); setXxa(0); setZza(0); }
    public String currentGoal() { return currentGoal; }
    public long goalVersion() { return goalVersion; }
    public int foodLevel() { return food; }
    public SimpleContainer inventory() { return inventory; }

    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (owner != null) tag.putUUID("Owner", owner);
        tag.putString("Goal", currentGoal); tag.putLong("GoalVersion", goalVersion); tag.putInt("Food", food);
        tag.put("Inventory", inventory.createTag(registryAccess()));
    }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        currentGoal = tag.getString("Goal"); goalVersion = tag.getLong("GoalVersion"); food = tag.contains("Food") ? tag.getInt("Food") : 20;
        inventory.fromTag(tag.getList("Inventory", 10), registryAccess());
    }
    @Override protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);
        for (ItemStack stack : inventory.removeAllItems()) if (!stack.isEmpty()) spawnAtLocation(stack);
    }
    @Override public void onRemovedFromLevel() {
        if (!level().isClientSide && level() instanceof ServerLevel serverLevel && lastChunkX != Integer.MIN_VALUE)
            MovingChunkTickets.release(serverLevel, this, lastChunkX, lastChunkZ, 2);
        super.onRemovedFromLevel();
    }
    @Override public boolean removeWhenFarAway(double distance) { return false; }
    @Override public boolean requiresCustomPersistence() { return true; }
}
