package dev.jevcraft.companion;

import dev.jevcraft.core.GoalParser;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.core.BlockPos;

import java.util.*;

public final class JevCompanion extends PathfinderMob {
    private static final EntityDimensions STANDING_DIMENSIONS=EntityDimensions.scalable(.6F,1.8F).withEyeHeight(1.62F);
    private static final EntityDimensions CROUCHING_DIMENSIONS=EntityDimensions.scalable(.6F,1.5F).withEyeHeight(1.27F);
    private static final EntityDimensions CRAWLING_DIMENSIONS=EntityDimensions.scalable(.6F,.6F).withEyeHeight(.4F);
    public record ChatObservation(UUID sender, String text, boolean authorized, boolean privateMessage) {}
    private static final int CHAT_LIMIT = 32;
    private final SimpleContainer inventory = new SimpleContainer(36);
    private UUID owner;
    private final Set<UUID> administrators = new LinkedHashSet<>();
    private String currentGoal = "";
    private long goalVersion;
    private int food = 20;
    private int experienceLevel, totalExperience;
    private float experienceProgress;
    private boolean operatorTeleportAllowed;
    private boolean creativeMode;
    private ResourceKey<Level> respawnDimension;
    private BlockPos respawnPosition;
    private float respawnAngle;
    private int sleepingTicks;
    private int lastChunkX = Integer.MIN_VALUE, lastChunkZ = Integer.MIN_VALUE;
    private final CompanionActionExecutor actions = new CompanionActionExecutor(this);
    private final Deque<ChatObservation> recentChat = new ArrayDeque<>();

    public JevCompanion(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);setPersistenceRequired();setCanPickUpLoot(true);getNavigation().setCanFloat(true);
        if(getNavigation() instanceof GroundPathNavigation ground){ground.setCanOpenDoors(true);ground.setCanPassDoors(true);}
    }
    @Override protected EntityDimensions getDefaultDimensions(Pose pose){return switch(pose){case CROUCHING->CROUCHING_DIMENSIONS;case SWIMMING,FALL_FLYING,SPIN_ATTACK->CRAWLING_DIMENSIONS;default->STANDING_DIMENSIONS;};}
    @Override protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1,new OpenDoorGoal(this,true));
        goalSelector.addGoal(5,new RandomStrollGoal(this,.8){@Override public boolean canUse(){return currentGoal.isBlank()&&super.canUse();}});
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }
    @Override public void tick() {
        super.tick();
        if (!level().isClientSide && level() instanceof ServerLevel serverLevel) {
            sleepingTicks=isSleeping()?sleepingTicks+1:0;
            if (tickCount % 1200 == 0 && !currentGoal.isBlank()) food = Math.max(0, food - 1);
            activateChunkTickets(serverLevel);
            if(isSleeping()){getNavigation().stop();return;}
            CompanionActionExecutor.Result result = actions.tick(serverLevel);
            if (result == CompanionActionExecutor.Result.IDLE) applySimpleGoal(serverLevel);
        }
    }
    public void activateChunkTickets(ServerLevel level) {
        int cx = chunkPosition().x, cz = chunkPosition().z;
        if (cx == lastChunkX && cz == lastChunkZ) return;
        MovingChunkTickets.move(level, this, lastChunkX, lastChunkZ, cx, cz, 2);
        lastChunkX = cx; lastChunkZ = cz;
    }
    @Override public Entity changeDimension(DimensionTransition transition) {
        ServerLevel destination=transition.newLevel(); BlockPos target=BlockPos.containing(transition.pos());
        boolean crossDimension=level()!=destination;
        if(crossDimension) MovingChunkTickets.prepare(destination,this,target.getX()>>4,target.getZ()>>4,2);
        Entity transferred=super.changeDimension(transition);
        if(crossDimension&&transferred==null) MovingChunkTickets.release(destination,this,target.getX()>>4,target.getZ()>>4,2);
        else if(transferred instanceof JevCompanion replacement) replacement.activateChunkTickets(destination);
        return transferred;
    }
    @Override public boolean teleportTo(ServerLevel destination,double x,double y,double z,Set<RelativeMovement> relative,float yRot,float xRot){
        boolean crossDimension=level()!=destination; int chunkX=BlockPos.containing(x,y,z).getX()>>4,chunkZ=BlockPos.containing(x,y,z).getZ()>>4;
        if(crossDimension) MovingChunkTickets.prepare(destination,this,chunkX,chunkZ,2);
        boolean transferred=super.teleportTo(destination,x,y,z,relative,yRot,xRot);
        if(crossDimension&&!transferred) MovingChunkTickets.release(destination,this,chunkX,chunkZ,2);
        else if(crossDimension&&destination.getEntity(getUUID()) instanceof JevCompanion replacement) replacement.activateChunkTickets(destination);
        return transferred;
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
    @Override public ItemStack getProjectile(ItemStack weapon){
        if(weapon.getItem() instanceof CrossbowItem)for(int i=0;i<inventory.getContainerSize();i++){ItemStack candidate=inventory.getItem(i);if(candidate.is(ItemTags.ARROWS)||candidate.is(net.minecraft.world.item.Items.FIREWORK_ROCKET))return candidate;}return super.getProjectile(weapon);
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
    public boolean canCommand(ServerPlayer player) { return owner != null && (owner.equals(player.getUUID()) || administrators.contains(player.getUUID()) || player.hasPermissions(2)); }
    public boolean canManageAccess(ServerPlayer player) { return owner != null && (owner.equals(player.getUUID()) || player.hasPermissions(2)); }
    public boolean addAdministrator(UUID player) { return administrators.add(player); }
    public boolean removeAdministrator(UUID player) { return administrators.remove(player); }
    public Set<UUID> administrators() { return Set.copyOf(administrators); }
    public void acceptGoal(String goal) { JevInferenceHost.instance().cancel(getUUID()); actions.cancel(); currentGoal = goal.strip(); goalVersion++; getNavigation().stop(); }
    public void stopNow() { JevInferenceHost.instance().cancel(getUUID()); actions.cancel(); currentGoal = ""; goalVersion++; getNavigation().stop(); setXxa(0); setZza(0); }
    public String currentGoal() { return currentGoal; }
    public long goalVersion() { return goalVersion; }
    public int foodLevel() { return food; }
    public int experienceLevel() { return experienceLevel; }
    public int totalExperience() { return totalExperience; }
    public float experienceProgress() { return experienceProgress; }
    public void setExperience(int level, int total, float progress) { experienceLevel = Math.max(0, level); totalExperience = Math.max(0, total); experienceProgress = Math.max(0, Math.min(1, progress)); }
    public boolean operatorTeleportAllowed() { return operatorTeleportAllowed; }
    public void setOperatorTeleportAllowed(boolean allowed) { operatorTeleportAllowed = allowed; }
    public boolean creativeMode(){return creativeMode;}
    public void setCreativeMode(boolean allowed){creativeMode=allowed;}
    public boolean tryStartCompanionFallFlying(){if(onGround()||isFallFlying()||isInWater()||hasEffect(net.minecraft.world.effect.MobEffects.LEVITATION))return false;ItemStack chest=getItemBySlot(EquipmentSlot.CHEST);if(!chest.canElytraFly(this))return false;setSharedFlag(7,true);setPose(Pose.FALL_FLYING);return true;}
    public void stopCompanionFallFlying(){if(isFallFlying()){setSharedFlag(7,true);setSharedFlag(7,false);}if(getPose()==Pose.FALL_FLYING)setPose(Pose.STANDING);}
    public void setRespawnPoint(ResourceKey<Level> dimension, BlockPos position, float angle) { respawnDimension = dimension; respawnPosition = position.immutable(); respawnAngle = angle; }
    public ResourceKey<Level> respawnDimension() { return respawnDimension; }
    public BlockPos respawnPosition() { return respawnPosition; }
    public float respawnAngle() { return respawnAngle; }
    public boolean companionSleepingLongEnough(){return isSleeping()&&sleepingTicks>=100;}
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
        ListTag admins = new ListTag();
        for (UUID administrator : administrators) { CompoundTag entry = new CompoundTag(); entry.putUUID("Id", administrator); admins.add(entry); }
        tag.put("Administrators", admins);
        tag.putString("Goal", currentGoal); tag.putLong("GoalVersion", goalVersion); tag.putInt("Food", food);
        tag.putInt("ExperienceLevel", experienceLevel); tag.putInt("TotalExperience", totalExperience); tag.putFloat("ExperienceProgress", experienceProgress);
        tag.putBoolean("OperatorTeleportAllowed", operatorTeleportAllowed);
        tag.putBoolean("CreativeMode",creativeMode);
        if (respawnDimension != null && respawnPosition != null) {
            tag.putString("RespawnDimension", respawnDimension.location().toString()); tag.putLong("RespawnPosition", respawnPosition.asLong()); tag.putFloat("RespawnAngle", respawnAngle);
        }
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
        administrators.clear();
        for (var value : tag.getList("Administrators", 10)) if (value instanceof CompoundTag entry && entry.hasUUID("Id")) administrators.add(entry.getUUID("Id"));
        currentGoal = tag.getString("Goal"); goalVersion = tag.getLong("GoalVersion"); food = tag.contains("Food") ? tag.getInt("Food") : 20;
        experienceLevel = Math.max(0, tag.getInt("ExperienceLevel")); totalExperience = Math.max(0, tag.getInt("TotalExperience")); experienceProgress = Math.max(0, Math.min(1, tag.getFloat("ExperienceProgress")));
        operatorTeleportAllowed = tag.getBoolean("OperatorTeleportAllowed");creativeMode=tag.getBoolean("CreativeMode"); respawnDimension = null; respawnPosition = null; respawnAngle = 0;
        if (tag.contains("RespawnDimension") && tag.contains("RespawnPosition")) {
            ResourceLocation id = ResourceLocation.tryParse(tag.getString("RespawnDimension"));
            if (id != null) { respawnDimension = ResourceKey.create(Registries.DIMENSION, id); respawnPosition = BlockPos.of(tag.getLong("RespawnPosition")); respawnAngle = tag.getFloat("RespawnAngle"); }
        }
        inventory.fromTag(tag.getList("Inventory", 10), registryAccess());
        recentChat.clear();
        for (var value : tag.getList("RecentChat", 10)) if (value instanceof CompoundTag entry && entry.hasUUID("Sender"))
            observeChat(entry.getUUID("Sender"), entry.getString("Text"), entry.getBoolean("Authorized"), entry.getBoolean("Private"));
    }
    @Override protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        JevInferenceHost.instance().cancel(getUUID());
        super.dropCustomDeathLoot(level, source, recentlyHit);
        for (ItemStack stack : inventory.removeAllItems()) if (!stack.isEmpty()) spawnAtLocation(stack);
        CompoundTag respawnState = new CompoundTag(); addAdditionalSaveData(respawnState);
        respawnState.putLong("RespawnFallbackPos", blockPosition().asLong());
        long delay = Math.max(20, Math.min(12000, Long.getLong("jevcraft.respawnTicks", 100L)));
        JevWorldData.get(level.getServer()).queueRespawn(getUUID(), respawnState, level.getGameTime() + delay);
    }
    @Override public void remove(Entity.RemovalReason reason) {
        if(!level().isClientSide&&level() instanceof ServerLevel serverLevel&&lastChunkX!=Integer.MIN_VALUE&&!reason.shouldSave()){
            MovingChunkTickets.release(serverLevel,this,lastChunkX,lastChunkZ,2); lastChunkX=Integer.MIN_VALUE; lastChunkZ=Integer.MIN_VALUE;
        }
        super.remove(reason);
    }
    @Override public void onRemovedFromLevel() {
        Entity.RemovalReason reason = getRemovalReason();
        if (!level().isClientSide && level() instanceof ServerLevel serverLevel && lastChunkX != Integer.MIN_VALUE
                && (reason == null || !reason.shouldSave()))
            MovingChunkTickets.release(serverLevel, this, lastChunkX, lastChunkZ, 2);
        lastChunkX=Integer.MIN_VALUE; lastChunkZ=Integer.MIN_VALUE;
        super.onRemovedFromLevel();
    }
    @Override public boolean removeWhenFarAway(double distance) { return false; }
    @Override public boolean requiresCustomPersistence() { return true; }
}
