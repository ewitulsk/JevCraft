package dev.jevcraft.companion;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.effect.MobEffect;
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
    private Vec3 climbTarget;
    private int climbTicks;
    private Vec3 swimTarget;
    private int swimTicks;
    private String lastFailure="";
    private long actionGoalVersion;
    private Result lastResult = Result.IDLE;

    public CompanionActionExecutor(JevCompanion companion) {
        this.companion = companion; this.interactions = new CompanionInteractionContext(companion);
    }
    public void beginMine(BlockPos target, long goalVersion) {
        miningTarget = Objects.requireNonNull(target).immutable(); miningProgress = 0; actionGoalVersion = goalVersion;rangedTarget=null;rangedTicks=0;movementTarget=null;movementTicks=0;climbTarget=null;climbTicks=0;swimTarget=null;swimTicks=0;
    }
    public void beginRangedAttack(LivingEntity target,int bowSlot,int ammoSlot,long goalVersion){
        rangedTarget=Objects.requireNonNull(target);this.bowSlot=bowSlot;this.ammoSlot=ammoSlot;rangedTicks=0;actionGoalVersion=goalVersion;miningTarget=null;miningProgress=0;movementTarget=null;movementTicks=0;climbTarget=null;climbTicks=0;swimTarget=null;swimTicks=0;
    }
    public void beginMove(Vec3 target,double speed,long goalVersion){
        movementTarget=Objects.requireNonNull(target);movementSpeed=Math.max(.1,Math.min(2,speed));movementTicks=0;lastFailure="";actionGoalVersion=goalVersion;miningTarget=null;miningProgress=0;rangedTarget=null;rangedTicks=0;climbTarget=null;climbTicks=0;swimTarget=null;swimTicks=0;companion.getNavigation().stop();
    }
    public void beginClimb(Vec3 target,long goalVersion){climbTarget=Objects.requireNonNull(target);climbTicks=0;lastFailure="";actionGoalVersion=goalVersion;miningTarget=null;miningProgress=0;rangedTarget=null;rangedTicks=0;movementTarget=null;movementTicks=0;swimTarget=null;swimTicks=0;companion.getNavigation().stop();}
    public void beginSwim(Vec3 target,long goalVersion){swimTarget=Objects.requireNonNull(target);swimTicks=0;lastFailure="";actionGoalVersion=goalVersion;miningTarget=null;miningProgress=0;rangedTarget=null;rangedTicks=0;movementTarget=null;movementTicks=0;climbTarget=null;climbTicks=0;companion.getNavigation().stop();}
    public void cancel() { miningTarget = null; miningProgress = 0; rangedTarget=null;bowSlot=-1;ammoSlot=-1;rangedTicks=0;movementTarget=null;movementTicks=0;climbTarget=null;climbTicks=0;swimTarget=null;swimTicks=0;companion.setSwimming(false);Vec3 velocity=companion.getDeltaMovement();companion.setDeltaMovement(0,Math.min(velocity.y,0),0);companion.getNavigation().stop(); }
    public BlockPos miningTarget() { return miningTarget; }
    public Result lastResult() { return lastResult; }
    public String lastFailure(){return lastFailure;}
    public String lastInteractionFailure(){return interactions.lastFailure();}
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
    public boolean grind(ServerLevel level,BlockHitResult hit,int inputSlot,int additionalSlot){
        return companion.distanceToSqr(hit.getLocation())<=25&&interactions.grind(level,hit,inputSlot,additionalSlot);
    }
    public boolean weaveBanner(ServerLevel level,BlockHitResult hit,int bannerSlot,int dyeSlot,int patternIndex){
        return companion.distanceToSqr(hit.getLocation())<=25&&interactions.weaveBanner(level,hit,bannerSlot,dyeSlot,patternIndex);
    }
    public boolean renameAtAnvil(ServerLevel level,BlockHitResult hit,int inputSlot,String name){
        return companion.distanceToSqr(hit.getLocation())<=25&&interactions.renameAtAnvil(level,hit,inputSlot,name);
    }
    public boolean enchant(ServerLevel level,BlockHitResult hit,int itemSlot,int lapisSlot,int option){
        return companion.distanceToSqr(hit.getLocation())<=25&&interactions.enchant(level,hit,itemSlot,lapisSlot,option);
    }
    public boolean smith(ServerLevel level,BlockHitResult hit,int templateSlot,int baseSlot,int additionSlot){
        return companion.distanceToSqr(hit.getLocation())<=25&&interactions.smith(level,hit,templateSlot,baseSlot,additionSlot);
    }
    public boolean loadBrewingStand(ServerLevel level,BlockHitResult hit,int bottleSlot,int ingredientSlot,int fuelSlot){
        return companion.distanceToSqr(hit.getLocation())<=25&&interactions.loadBrewingStand(level,hit,bottleSlot,ingredientSlot,fuelSlot);
    }
    public boolean collectBrewingStand(ServerLevel level,BlockHitResult hit,int standSlot){
        return companion.distanceToSqr(hit.getLocation())<=25&&interactions.collectBrewingStand(level,hit,standSlot);
    }
    public boolean cartography(ServerLevel level,BlockHitResult hit,int mapSlot,int additionSlot){
        return companion.distanceToSqr(hit.getLocation())<=25&&interactions.cartography(level,hit,mapSlot,additionSlot);
    }
    public boolean activateBeacon(ServerLevel level,BlockHitResult hit,int paymentSlot,Holder<MobEffect> primary){
        return companion.distanceToSqr(hit.getLocation())<=25&&interactions.activateBeacon(level,hit,paymentSlot,primary);
    }
    public boolean moveInventoryStack(ServerLevel level,int sourceSlot,int destinationSlot,int amount){
        return interactions.moveInventoryStack(level,sourceSlot,destinationSlot,amount);
    }
    public boolean dropInventoryStack(ServerLevel level,int sourceSlot,int amount){
        return interactions.dropInventoryStack(level,sourceSlot,amount);
    }
    public boolean equipInventoryStack(ServerLevel level,int sourceSlot,EquipmentSlot equipmentSlot){
        return interactions.equipInventoryStack(level,sourceSlot,equipmentSlot);
    }
    public java.util.List<String> readSign(ServerLevel level,BlockPos pos,boolean front){
        return companion.distanceToSqr(Vec3.atCenterOf(pos))<=25?interactions.readSign(level,pos,front):java.util.List.of();
    }
    public boolean writeSign(ServerLevel level,BlockHitResult hit,boolean front,java.util.List<String> lines){
        return companion.distanceToSqr(hit.getLocation())<=25&&interactions.writeSign(level,hit,front,lines);
    }
    public java.util.List<String> readBook(int slot){return interactions.readBook(slot);}
    public boolean writeBook(int slot,java.util.List<String> pages){return interactions.writeBook(slot,pages);}
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
        if(swimTarget!=null)return tickSwim(level);
        if(climbTarget!=null)return tickClimb(level);
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
    private Result tickClimb(ServerLevel level){
        if(actionGoalVersion!=companion.goalVersion()){lastFailure="stale_goal";cancel();return lastResult=Result.INVALID;}if(++climbTicks>240){lastFailure="timeout";cancel();return lastResult=Result.INVALID;}if(companion.getY()>=climbTarget.y-.35&&Math.abs(companion.getX()-climbTarget.x)<=.8&&Math.abs(companion.getZ()-climbTarget.z)<=.8){companion.setDeltaMovement(companion.getDeltaMovement().multiply(.25,0,.25));climbTarget=null;climbTicks=0;return lastResult=Result.SUCCEEDED;}
        BlockPos feet=companion.blockPosition();BlockState state=level.getBlockState(feet),below=level.getBlockState(feet.below());boolean climbable=state.is(BlockTags.CLIMBABLE)||state.is(Blocks.SCAFFOLDING)||below.is(BlockTags.CLIMBABLE)||below.is(Blocks.SCAFFOLDING);if(!climbable&&climbTicks>12){lastFailure="left_climbable_surface";cancel();return lastResult=Result.INVALID;}
        double dx=Mth.clamp(climbTarget.x-companion.getX(),-.08,.08),dz=Mth.clamp(climbTarget.z-companion.getZ(),-.08,.08);companion.setDeltaMovement(dx,.22,dz);companion.fallDistance=0;companion.getLookControl().setLookAt(climbTarget);return lastResult=Result.MOVING;
    }
    private Result tickSwim(ServerLevel level){
        if(actionGoalVersion!=companion.goalVersion()){lastFailure="stale_goal";cancel();return lastResult=Result.INVALID;}if(++swimTicks>400){lastFailure="timeout";cancel();return lastResult=Result.INVALID;}if(companion.distanceToSqr(swimTarget)<=1.5){Vec3 correction=swimTarget.subtract(companion.position());companion.setSwimming(true);companion.setDeltaMovement(correction.scale(.08));return lastResult=Result.SUCCEEDED;}if(!companion.isInWater()&&swimTicks>12){lastFailure="left_water";cancel();return lastResult=Result.INVALID;}
        Vec3 delta=swimTarget.subtract(companion.position()),direction=delta.normalize();companion.setSwimming(true);companion.setDeltaMovement(direction.x*.16,Mth.clamp(delta.y,-.12,.16),direction.z*.16);companion.getLookControl().setLookAt(swimTarget);return lastResult=Result.MOVING;
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
