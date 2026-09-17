package dev.jevcraft.companion;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;

public final class CompanionActionExecutor {
    public enum Result { IDLE, MOVING, WORKING, SUCCEEDED, INVALID }
    private final JevCompanion companion;
    private final CompanionInteractionContext interactions;
    private BlockPos miningTarget;
    private float miningProgress;
    private long actionGoalVersion;
    private Result lastResult = Result.IDLE;

    public CompanionActionExecutor(JevCompanion companion) {
        this.companion = companion; this.interactions = new CompanionInteractionContext(companion);
    }
    public void beginMine(BlockPos target, long goalVersion) {
        miningTarget = Objects.requireNonNull(target).immutable(); miningProgress = 0; actionGoalVersion = goalVersion;
    }
    public void cancel() { miningTarget = null; miningProgress = 0; companion.getNavigation().stop(); }
    public BlockPos miningTarget() { return miningTarget; }
    public Result lastResult() { return lastResult; }
    public float miningProgress() { return miningProgress; }
    public InteractionResult place(ServerLevel level, BlockHitResult hit, int inventorySlot) {
        if (companion.distanceToSqr(hit.getLocation()) > 25) return InteractionResult.FAIL;
        return interactions.useItemOn(level, hit, inventorySlot);
    }
    public boolean attack(ServerLevel level, LivingEntity target, int inventorySlot) {
        if (!target.isAlive() || companion.distanceToSqr(target) > 16) return false;
        float health = target.getHealth(); interactions.attack(level, target, inventorySlot); return target.getHealth() < health;
    }
    public boolean depositStack(ServerLevel level, BlockHitResult hit, int inventorySlot) {
        if (companion.distanceToSqr(hit.getLocation()) > 25) return false;
        return interactions.quickMoveToContainer(level, hit, inventorySlot);
    }
    public boolean craftSingleIngredient(ServerLevel level, BlockHitResult hit, int inventorySlot) {
        if (companion.distanceToSqr(hit.getLocation()) > 25) return false;
        return interactions.craftSingleIngredient(level, hit, inventorySlot);
    }
    public boolean teleport(ServerLevel level, Vec3 destination) {
        if (!companion.operatorTeleportAllowed() || !Double.isFinite(destination.x) || !Double.isFinite(destination.y) || !Double.isFinite(destination.z)) return false;
        BlockPos target = BlockPos.containing(destination); level.getChunkAt(target);
        MovingChunkTickets.prepare(level, companion, target.getX() >> 4, target.getZ() >> 4, 2);
        companion.teleportTo(level, destination.x, destination.y, destination.z, java.util.Set.of(), companion.getYRot(), companion.getXRot());
        return companion.distanceToSqr(destination) < .01;
    }

    public Result tick(ServerLevel level) {
        if (miningTarget == null) return lastResult = Result.IDLE;
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
}
