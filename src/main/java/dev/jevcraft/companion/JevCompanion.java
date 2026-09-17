package dev.jevcraft.companion;

import dev.jevcraft.core.GoalParser;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.core.BlockPos;

import java.util.*;

public final class JevCompanion extends PathfinderMob {
    public record ChatObservation(UUID sender, String text, boolean authorized, boolean privateMessage) {}
    private static final int CHAT_LIMIT = 32;
    private final SimpleContainer inventory = new SimpleContainer(36);
    private UUID owner;
    private String currentGoal = "";
    private long goalVersion;
    private int food = 20;
    private int lastChunkX = Integer.MIN_VALUE, lastChunkZ = Integer.MIN_VALUE;
    private final CompanionActionExecutor actions = new CompanionActionExecutor(this);
    private final Deque<ChatObservation> recentChat = new ArrayDeque<>();

    public JevCompanion(EntityType<? extends PathfinderMob> type, Level level) { super(type, level); setPersistenceRequired(); setCanPickUpLoot(true); }
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
            CompanionActionExecutor.Result result = actions.tick(serverLevel);
            if (result == CompanionActionExecutor.Result.IDLE) applySimpleGoal(serverLevel);
        }
    }
    private void applySimpleGoal(ServerLevel level) {
        if (currentGoal.isBlank() || owner == null) return;
        GoalParser.Goal parsed = new GoalParser().parse(currentGoal);
        if (parsed.kind() == GoalParser.Kind.FOLLOW) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(owner);
            if (player != null && player.level() == level && distanceToSqr(player) > 9) getNavigation().moveTo(player, 1);
        } else if (parsed.kind() == GoalParser.Kind.ACQUIRE) {
            if (countLogs() >= parsed.quantity()) { stopNow(); return; }
            if (tickCount % 10 == 0) JevInferenceHost.instance().chooseMiningTarget(this, level, visibleLogs(level, 8, 32), currentGoal, goalVersion);
        }
    }
    private Map<String, BlockPos> visibleLogs(ServerLevel level, int radius, int limit) {
        Map<String, BlockPos> result = new LinkedHashMap<>();
        BlockPos center = blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, -3, -radius), center.offset(radius, radius, radius))) {
            if (result.size() >= limit) break;
            BlockPos immutable = pos.immutable();
            if (level.getBlockState(immutable).is(BlockTags.LOGS) && canSeeBlock(level, immutable)) result.put("mine_" + result.size(), immutable);
        }
        return result;
    }
    public boolean canSeeBlock(ServerLevel level, BlockPos pos) {
        Vec3 eye = getEyePosition(); Vec3 center = Vec3.atCenterOf(pos);
        BlockHitResult hit = level.clip(new ClipContext(eye, center, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        return hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(pos);
    }
    public int bestToolSlot(BlockState state) {
        int best = 0; float speed = 1;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            float candidate = inventory.getItem(slot).getDestroySpeed(state);
            if (candidate > speed) { speed = candidate; best = slot; }
        }
        return best;
    }
    private int countLogs() {
        int count = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) if (inventory.getItem(slot).is(ItemTags.LOGS)) count += inventory.getItem(slot).getCount();
        return count;
    }
    public List<String> inventorySummary() {
        List<String> values = new ArrayList<>();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) if (!inventory.getItem(slot).isEmpty())
            values.add(slot + ":" + inventory.getItem(slot).getHoverName().getString() + "x" + inventory.getItem(slot).getCount());
        return values;
    }
    public void observeChat(UUID sender, String text, boolean authorized, boolean privateMessage) {
        String bounded = text == null ? "" : text.strip();
        if (bounded.length() > 256) bounded = bounded.substring(0, 256);
        if (bounded.isEmpty()) return;
        while (recentChat.size() >= CHAT_LIMIT) recentChat.removeFirst();
        recentChat.addLast(new ChatObservation(sender, bounded, authorized, privateMessage));
    }
    public List<ChatObservation> recentChat() { return List.copyOf(recentChat); }
    public void setOwner(UUID owner) { this.owner = owner; }
    public UUID owner() { return owner; }
    public boolean canCommand(ServerPlayer player) { return owner != null && (owner.equals(player.getUUID()) || player.hasPermissions(2)); }
    public void acceptGoal(String goal) { JevInferenceHost.instance().cancel(getUUID()); actions.cancel(); currentGoal = goal.strip(); goalVersion++; getNavigation().stop(); }
    public void stopNow() { JevInferenceHost.instance().cancel(getUUID()); actions.cancel(); currentGoal = ""; goalVersion++; getNavigation().stop(); setXxa(0); setZza(0); }
    public String currentGoal() { return currentGoal; }
    public long goalVersion() { return goalVersion; }
    public int foodLevel() { return food; }
    public SimpleContainer inventory() { return inventory; }
    public CompanionActionExecutor actions() { return actions; }
    public boolean consumeFood(int slot) {
        if (slot < 0 || slot >= inventory.getContainerSize() || food >= 20) return false;
        ItemStack stack = inventory.getItem(slot);
        var properties = stack.get(DataComponents.FOOD);
        if (properties == null) return false;
        int before = stack.getCount();
        ItemStack remainder = stack.finishUsingItem(level(), this);
        if (remainder.getCount() >= before) return false;
        inventory.setItem(slot, remainder);
        food = Math.min(20, food + properties.nutrition());
        return true;
    }

    @Override protected void pickUpItem(ItemEntity itemEntity) {
        ItemStack before = itemEntity.getItem();
        int original = before.getCount();
        ItemStack remainder = inventory.addItem(before.copy());
        int pickedUp = original - remainder.getCount();
        if (pickedUp > 0) {
            take(itemEntity, pickedUp);
            if (remainder.isEmpty()) itemEntity.discard(); else itemEntity.setItem(remainder);
        }
    }

    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (owner != null) tag.putUUID("Owner", owner);
        tag.putString("Goal", currentGoal); tag.putLong("GoalVersion", goalVersion); tag.putInt("Food", food);
        tag.put("Inventory", inventory.createTag(registryAccess()));
        ListTag chat = new ListTag();
        for (ChatObservation observation : recentChat) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Sender", observation.sender()); entry.putString("Text", observation.text());
            entry.putBoolean("Authorized", observation.authorized()); entry.putBoolean("Private", observation.privateMessage());
            chat.add(entry);
        }
        tag.put("RecentChat", chat);
    }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        currentGoal = tag.getString("Goal"); goalVersion = tag.getLong("GoalVersion"); food = tag.contains("Food") ? tag.getInt("Food") : 20;
        inventory.fromTag(tag.getList("Inventory", 10), registryAccess());
        recentChat.clear();
        for (var value : tag.getList("RecentChat", 10)) if (value instanceof CompoundTag entry && entry.hasUUID("Sender"))
            observeChat(entry.getUUID("Sender"), entry.getString("Text"), entry.getBoolean("Authorized"), entry.getBoolean("Private"));
    }
    @Override protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);
        for (ItemStack stack : inventory.removeAllItems()) if (!stack.isEmpty()) spawnAtLocation(stack);
        JevWorldData.get(level.getServer()).unregister(getUUID());
    }
    @Override public void onRemovedFromLevel() {
        if (!level().isClientSide && level() instanceof ServerLevel serverLevel && lastChunkX != Integer.MIN_VALUE)
            MovingChunkTickets.release(serverLevel, this, lastChunkX, lastChunkZ, 2);
        super.onRemovedFromLevel();
    }
    @Override public boolean removeWhenFarAway(double distance) { return false; }
    @Override public boolean requiresCustomPersistence() { return true; }
}
