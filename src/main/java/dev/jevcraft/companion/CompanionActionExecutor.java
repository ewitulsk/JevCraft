package dev.jevcraft.companion;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;

public final class CompanionActionExecutor {
    public enum Result { IDLE, MOVING, WORKING, SUCCEEDED, INVALID }
    private final JevCompanion companion;
    private final CompanionInteractionContext interactions;
    private BlockPos miningTarget;
    private float miningProgress;
    private LivingEntity rangedTarget;
    private int bowSlot=-1,ammoSlot=-1,rangedTicks;
    private Vec3 movementTarget;
    private double movementSpeed;
    private int movementTicks;
    private String lastFailure="";
    private long actionGoalVersion;
    private Result lastResult = Result.IDLE;

    public CompanionActionExecutor(JevCompanion companion) {
        this.companion = companion; this.interactions = new CompanionInteractionContext(companion);
    }
    public void beginMine(BlockPos target, long goalVersion) {
        miningTarget = Objects.requireNonNull(target).immutable(); miningProgress = 0; actionGoalVersion = goalVersion;rangedTarget=null;rangedTicks=0;movementTarget=null;movementTicks=0;
    }
    public void beginRangedAttack(LivingEntity target,int bowSlot,int ammoSlot,long goalVersion){
        rangedTarget=Objects.requireNonNull(target);this.bowSlot=bowSlot;this.ammoSlot=ammoSlot;rangedTicks=0;actionGoalVersion=goalVersion;miningTarget=null;miningProgress=0;movementTarget=null;movementTicks=0;
    }
    public void beginMove(Vec3 target,double speed,long goalVersion){
        movementTarget=Objects.requireNonNull(target);movementSpeed=Math.max(.1,Math.min(2,speed));movementTicks=0;lastFailure="";actionGoalVersion=goalVersion;miningTarget=null;miningProgress=0;rangedTarget=null;rangedTicks=0;companion.getNavigation().stop();
    }
    public void cancel() { miningTarget = null; miningProgress = 0; rangedTarget=null;bowSlot=-1;ammoSlot=-1;rangedTicks=0;movementTarget=null;movementTicks=0; companion.getNavigation().stop(); }
    public BlockPos miningTarget() { return miningTarget; }
    public Result lastResult() { return lastResult; }
    public String lastFailure(){return lastFailure;}
    public float miningProgress() { return miningProgress; }
    public InteractionResult place(ServerLevel level, BlockHitResult hit, int inventorySlot) {
        return useBlock(level,hit,inventorySlot);
    }
    public InteractionResult useBlock(ServerLevel level, BlockHitResult hit, int inventorySlot) {
        if (companion.distanceToSqr(hit.getLocation()) > 25) return InteractionResult.FAIL;
        return interactions.useItemOn(level, hit, inventorySlot);
    }
    public InteractionResult useItem(ServerLevel level, Vec3 aim, int inventorySlot) {
        if(companion.distanceToSqr(aim)>25) return InteractionResult.FAIL;
        return interactions.useItem(level,aim,inventorySlot);
    }
    public boolean attack(ServerLevel level, LivingEntity target, int inventorySlot) {
        if (!target.isAlive() || companion.distanceToSqr(target) > 16) return false;
        float health = target.getHealth(); interactions.attack(level, target, inventorySlot); return target.getHealth() < health;
    }
    public InteractionResult interactEntity(ServerLevel level, Entity target, int inventorySlot) {
        if (!target.isAlive() || target.level() != level || companion.distanceToSqr(target) > 16) return InteractionResult.FAIL;
        return interactions.interactEntity(level, target, inventorySlot);
    }
    public boolean trade(ServerLevel level,Entity merchant,int offerIndex){
        return merchant.isAlive()&&merchant.level()==level&&companion.distanceToSqr(merchant)<=16&&interactions.trade(level,merchant,offerIndex);
    }
    public boolean depositStack(ServerLevel level, BlockHitResult hit, int inventorySlot) {
        if (companion.distanceToSqr(hit.getLocation()) > 25) return false;
        return interactions.quickMoveToContainer(level, hit, inventorySlot);
    }
    public boolean craftSingleIngredient(ServerLevel level, BlockHitResult hit, int inventorySlot) {
        if (companion.distanceToSqr(hit.getLocation()) > 25) return false;
        return interactions.craftSingleIngredient(level, hit, inventorySlot);
    }
    public boolean craftPattern(ServerLevel level,BlockHitResult hit,int[] ingredientSlots){
        if(companion.distanceToSqr(hit.getLocation())>25)return false;
        return interactions.craftPattern(level,hit,ingredientSlots);
    }
    public boolean loadFurnace(ServerLevel level,BlockHitResult hit,int inputSlot,int fuelSlot){
        return companion.distanceToSqr(hit.getLocation())<=25&&interactions.loadFurnace(level,hit,inputSlot,fuelSlot);
    }
    public boolean collectFurnace(ServerLevel level,BlockHitResult hit){
        return companion.distanceToSqr(hit.getLocation())<=25&&interactions.collectFurnace(level,hit);
    }
    public boolean stonecut(ServerLevel level,BlockHitResult hit,int inputSlot,Item requestedResult){
        return companion.distanceToSqr(hit.getLocation())<=25&&interactions.stonecut(level,hit,inputSlot,requestedResult);
    }
    public boolean teleport(ServerLevel level, Vec3 destination) {
        if (!companion.operatorTeleportAllowed() || !Double.isFinite(destination.x) || !Double.isFinite(destination.y) || !Double.isFinite(destination.z)) return false;
        BlockPos target = BlockPos.containing(destination); level.getChunkAt(target);
        MovingChunkTickets.prepare(level, companion, target.getX() >> 4, target.getZ() >> 4, 2);
        companion.teleportTo(level, destination.x, destination.y, destination.z, java.util.Set.of(), companion.getYRot(), companion.getXRot());
        return companion.distanceToSqr(destination) < .01;
    }
    public boolean mount(Entity vehicle) {
        return vehicle.isAlive()&&vehicle.level()==companion.level()&&!companion.isPassenger()&&companion.distanceToSqr(vehicle)<=16&&companion.startRiding(vehicle);
    }
    public boolean dismount() {
        if(!companion.isPassenger()) return false;
        companion.stopRiding(); return !companion.isPassenger();
    }
    public boolean sleepInBed(ServerLevel level,BlockHitResult hit){
        return companion.distanceToSqr(hit.getLocation())<=25&&interactions.sleepInBed(level,hit);
    }
    public boolean wakeUp(){if(!companion.isSleeping())return false;companion.stopSleeping();return !companion.isSleeping();}

    public Result tick(ServerLevel level) {
        if(rangedTarget!=null) return tickRanged(level);
        if(movementTarget!=null)return tickMove(level);
        if (miningTarget == null) return Result.IDLE;
        if (actionGoalVersion != companion.goalVersion()) { cancel(); return lastResult = Result.INVALID; }
        BlockState state = level.getBlockState(miningTarget);
        if (state.isAir() || state.getDestroySpeed(level, miningTarget) < 0) { cancel(); return lastResult = Result.INVALID; }
        double distance = companion.distanceToSqr(miningTarget.getX() + .5, miningTarget.getY() + .5, miningTarget.getZ() + .5);
        if (distance > 16) {
            if (!companion.getNavigation().moveTo(miningTarget.getX() + .5, miningTarget.getY(), miningTarget.getZ() + .5, 1.0)) {
                cancel(); return lastResult = Result.INVALID;
            }
            return lastResult = Result.MOVING;
        }
        companion.getNavigation().stop();
        companion.getLookControl().setLookAt(miningTarget.getX() + .5, miningTarget.getY() + .5, miningTarget.getZ() + .5);
        float increment = interactions.destroyProgress(level, miningTarget, companion.bestToolSlot(state));
        if (increment <= 0) { cancel(); return lastResult = Result.INVALID; }
        miningProgress += increment;
        level.destroyBlockProgress(companion.getId(), miningTarget, Math.min(9, (int) (miningProgress * 10)));
        if (miningProgress < 1) return lastResult = Result.WORKING;
        int slot = companion.bestToolSlot(state);
        boolean success = interactions.destroyBlock(level, miningTarget, slot);
        level.destroyBlockProgress(companion.getId(), miningTarget, -1);
        miningTarget = null; miningProgress = 0;
        return lastResult = success ? Result.SUCCEEDED : Result.INVALID;
    }
    private Result tickMove(ServerLevel level){
        if(actionGoalVersion!=companion.goalVersion()){lastFailure="stale_goal";cancel();return lastResult=Result.INVALID;}
        if(++movementTicks>400){lastFailure="timeout";cancel();return lastResult=Result.INVALID;}
        if(companion.distanceToSqr(movementTarget)<=2.25){companion.getNavigation().stop();movementTarget=null;movementTicks=0;return lastResult=Result.SUCCEEDED;}
        openNearbyFenceGates(level);
        if(companion.getNavigation().isDone()||movementTicks%10==1)if(!companion.getNavigation().moveTo(movementTarget.x,movementTarget.y,movementTarget.z,movementSpeed)){lastFailure="no_path";cancel();return lastResult=Result.INVALID;}
        companion.getLookControl().setLookAt(movementTarget);return lastResult=Result.MOVING;
    }
    private void openNearbyFenceGates(ServerLevel level){
        BlockPos center=companion.blockPosition();
        for(BlockPos candidate:BlockPos.betweenClosed(center.offset(-2,-1,-2),center.offset(2,1,2))){
            BlockState state=level.getBlockState(candidate);
            if(state.getBlock() instanceof FenceGateBlock&& !state.getValue(FenceGateBlock.OPEN)&&companion.distanceToSqr(Vec3.atCenterOf(candidate))<=9){
                interactions.useItemOn(level,new BlockHitResult(Vec3.atCenterOf(candidate),Direction.UP,candidate.immutable(),false),0);
            }
        }
    }
    private Result tickRanged(ServerLevel level){
        if(actionGoalVersion!=companion.goalVersion()||!rangedTarget.isAlive()||rangedTarget.level()!=level||bowSlot<0||bowSlot>=companion.inventory().getContainerSize()||ammoSlot<0||ammoSlot>=companion.inventory().getContainerSize()){
            cancel();return lastResult=Result.INVALID;
        }
        ItemStack bow=companion.inventory().getItem(bowSlot),ammo=companion.inventory().getItem(ammoSlot);
        if(!(bow.getItem() instanceof BowItem)||!(ammo.getItem() instanceof ArrowItem arrowItem)||ammo.isEmpty()) {cancel();return lastResult=Result.INVALID;}
        if(companion.distanceToSqr(rangedTarget)>225||!companion.hasLineOfSight(rangedTarget)){cancel();return lastResult=Result.INVALID;}
        companion.getNavigation().stop();companion.getLookControl().setLookAt(rangedTarget,30,30);
        if(++rangedTicks<20)return lastResult=Result.WORKING;
        ItemStack projectileStack=ammo.split(1);AbstractArrow arrow=arrowItem.createArrow(level,projectileStack,companion,bow);
        Vec3 delta=rangedTarget.getEyePosition().subtract(arrow.position());double horizontal=Math.sqrt(delta.x*delta.x+delta.z*delta.z);
        arrow.shoot(delta.x,delta.y+horizontal*.03,delta.z,2.0F,0.0F);level.addFreshEntity(arrow);bow.hurtAndBreak(1,level,companion,item->{});
        rangedTarget=null;bowSlot=-1;ammoSlot=-1;rangedTicks=0;return lastResult=Result.SUCCEEDED;
    }
}
