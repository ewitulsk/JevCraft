package dev.jevcraft.companion;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

/** A per-companion player-shaped adapter for mechanics that require a player context. */
public final class CompanionInteractionContext {
    private final JevCompanion companion;
    private FakePlayer player;

    public CompanionInteractionContext(JevCompanion companion) { this.companion = companion; }

    public FakePlayer player(ServerLevel level) {
        if (player == null || player.serverLevel() != level) {
            player = FakePlayerFactory.get(level, new GameProfile(companion.getUUID(), "[Jev:" + companion.getStringUUID().substring(0, 8) + "]"));
        }
        player.moveTo(companion.getX(), companion.getY(), companion.getZ(), companion.getYRot(), companion.getXRot());
        return player;
    }

    public float destroyProgress(ServerLevel level, BlockPos target, int toolSlot) {
        FakePlayer actor = player(level);
        ItemStack authoritative = companion.inventory().getItem(toolSlot);
        actor.getInventory().selected = 0;
        actor.getInventory().setItem(0, authoritative.copy());
        return level.getBlockState(target).getDestroyProgress(actor, level, target);
    }

    public boolean destroyBlock(ServerLevel level, BlockPos target, int toolSlot) {
        FakePlayer actor = player(level);
        ItemStack authoritative = companion.inventory().getItem(toolSlot);
        actor.getInventory().selected = 0;
        actor.getInventory().setItem(0, authoritative.copy());
        boolean destroyed = actor.gameMode.destroyBlock(target);
        companion.inventory().setItem(toolSlot, actor.getInventory().getItem(0).copy());
        actor.getInventory().setItem(0, ItemStack.EMPTY);
        return destroyed;
    }

    public InteractionResult useItemOn(ServerLevel level, BlockHitResult hit, int slot) {
        FakePlayer actor = player(level);
        actor.getInventory().selected = 0;
        actor.getInventory().setItem(0, companion.inventory().getItem(slot).copy());
        InteractionResult result = actor.gameMode.useItemOn(actor, level, actor.getInventory().getItem(0), InteractionHand.MAIN_HAND, hit);
        companion.inventory().setItem(slot, actor.getInventory().getItem(0).copy());
        actor.getInventory().setItem(0, ItemStack.EMPTY);
        return result;
    }

    public void attack(ServerLevel level, Entity target, int slot) {
        FakePlayer actor = player(level);
        actor.getInventory().selected = 0;
        actor.getInventory().setItem(0, companion.inventory().getItem(slot).copy());
        actor.attack(target);
        companion.inventory().setItem(slot, actor.getInventory().getItem(0).copy());
        actor.getInventory().setItem(0, ItemStack.EMPTY);
    }

    /** Transfers one complete stack through a real ChestMenu after normal block-use access succeeds. */
    public boolean quickMoveToContainer(ServerLevel level, BlockHitResult hit, int companionSlot) {
        if (companionSlot < 0 || companionSlot >= companion.inventory().getContainerSize()) return false;
        ItemStack source = companion.inventory().getItem(companionSlot);
        if (source.isEmpty()) return false;
        FakePlayer actor = player(level);
        int handSlot = firstEmptySlot(companionSlot);
        actor.getInventory().selected = handSlot < 9 ? handSlot : 0;
        syncToPlayer(actor);
        ItemStack handBefore = actor.getMainHandItem().copy();
        actor.getInventory().setItem(actor.getInventory().selected, ItemStack.EMPTY);
        InteractionResult access = actor.gameMode.useItemOn(actor, level, actor.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
        actor.getInventory().setItem(actor.getInventory().selected, handBefore);
        if (!access.consumesAction()) { clearPlayer(actor); return false; }
        BlockEntity blockEntity = level.getBlockEntity(hit.getBlockPos());
        if (!(blockEntity instanceof Container container) || container.getContainerSize() != 27 || !container.stillValid(actor)) {
            clearPlayer(actor); return false;
        }
        int sourceBefore = source.getCount(), containerBefore = totalItems(container);
        ChestMenu menu = ChestMenu.threeRows(0, actor.getInventory(), container);
        int menuSlot = -1;
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot slot = menu.slots.get(i);
            if (slot.container == actor.getInventory() && slot.getContainerSlot() == companionSlot) { menuSlot = i; break; }
        }
        if (menuSlot >= 0) menu.clicked(menuSlot, 0, ClickType.QUICK_MOVE, actor);
        menu.removed(actor);
        syncFromPlayer(actor);
        boolean exact = companion.inventory().getItem(companionSlot).getCount() + totalItems(container) - containerBefore == sourceBefore;
        clearPlayer(actor);
        return menuSlot >= 0 && exact && totalItems(container) > containerBefore;
    }

    /** Crafts a recipe whose complete input is one item, using real crafting slots and result callbacks. */
    public boolean craftSingleIngredient(ServerLevel level, BlockHitResult hit, int companionSlot) {
        if (companionSlot < 0 || companionSlot >= companion.inventory().getContainerSize()
                || companion.inventory().getItem(companionSlot).isEmpty()) return false;
        FakePlayer actor = player(level);
        syncToPlayer(actor);
        int handSlot = firstEmptySlot(companionSlot);
        actor.getInventory().selected = handSlot < 9 ? handSlot : 0;
        ItemStack handBefore = actor.getMainHandItem().copy();
        actor.getInventory().setItem(actor.getInventory().selected, ItemStack.EMPTY);
        InteractionResult access = actor.gameMode.useItemOn(actor, level, actor.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
        actor.getInventory().setItem(actor.getInventory().selected, handBefore);
        if (!access.consumesAction()) { clearPlayer(actor); return false; }
        CraftingMenu menu = new CraftingMenu(0, actor.getInventory(), ContainerLevelAccess.create(level, hit.getBlockPos()));
        int menuSlot = playerMenuSlot(menu, actor, companionSlot);
        if (menuSlot < 0) { menu.removed(actor); clearPlayer(actor); return false; }
        int sourceBefore = actor.getInventory().getItem(companionSlot).getCount();
        menu.clicked(menuSlot, 0, ClickType.PICKUP, actor);
        menu.clicked(1, 1, ClickType.PICKUP, actor);
        menu.clicked(menuSlot, 0, ClickType.PICKUP, actor);
        if (!menu.getSlot(0).hasItem()) { menu.removed(actor); syncFromPlayer(actor); clearPlayer(actor); return false; }
        ItemStack result = menu.getSlot(0).getItem().copy();
        menu.clicked(0, 0, ClickType.QUICK_MOVE, actor);
        menu.removed(actor);
        syncFromPlayer(actor);
        boolean consumedOne = companion.inventory().getItem(companionSlot).getCount() == sourceBefore - 1;
        boolean received = countItem(companion.inventory(), result) >= result.getCount();
        clearPlayer(actor);
        return consumedOne && received;
    }

    private static int playerMenuSlot(net.minecraft.world.inventory.AbstractContainerMenu menu, FakePlayer actor, int inventorySlot) {
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot slot = menu.slots.get(i);
            if (slot.container == actor.getInventory() && slot.getContainerSlot() == inventorySlot) return i;
        }
        return -1;
    }

    private int firstEmptySlot(int except) {
        for (int i = 0; i < companion.inventory().getContainerSize(); i++)
            if (i != except && companion.inventory().getItem(i).isEmpty()) return i;
        return except < 9 ? (except + 1) % 9 : 0;
    }
    private void syncToPlayer(FakePlayer actor) {
        for (int i = 0; i < companion.inventory().getContainerSize(); i++) actor.getInventory().setItem(i, companion.inventory().getItem(i).copy());
    }
    private void syncFromPlayer(FakePlayer actor) {
        for (int i = 0; i < companion.inventory().getContainerSize(); i++) companion.inventory().setItem(i, actor.getInventory().getItem(i).copy());
    }
    private static void clearPlayer(FakePlayer actor) {
        for (int i = 0; i < actor.getInventory().getContainerSize(); i++) actor.getInventory().setItem(i, ItemStack.EMPTY);
    }
    private static int totalItems(Container container) {
        int total = 0;
        for (int i = 0; i < container.getContainerSize(); i++) total += container.getItem(i).getCount();
        return total;
    }
    private static int countItem(Container container, ItemStack target) {
        int total = 0;
        for (int i = 0; i < container.getContainerSize(); i++) if (ItemStack.isSameItemSameComponents(container.getItem(i), target)) total += container.getItem(i).getCount();
        return total;
    }
}
