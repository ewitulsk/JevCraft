package dev.jevcraft.companion;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.BlastFurnaceMenu;
import net.minecraft.world.inventory.BrewingStandMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.inventory.FurnaceMenu;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.inventory.LoomMenu;
import net.minecraft.world.inventory.SmokerMenu;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.inventory.StonecutterMenu;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlastFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.world.level.block.entity.SmokerBlockEntity;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.Mth;
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

    public InteractionResult useItem(ServerLevel level, Vec3 aim, int slot) {
        FakePlayer actor=player(level); Vec3 delta=aim.subtract(actor.getEyePosition()); double horizontal=Math.sqrt(delta.x*delta.x+delta.z*delta.z);
        actor.setYRot((float)(Mth.atan2(delta.z,delta.x)*180/Math.PI)-90); actor.setYHeadRot(actor.getYRot()); actor.setXRot((float)-(Mth.atan2(delta.y,horizontal)*180/Math.PI));
        actor.getInventory().selected=0; actor.getInventory().setItem(0,companion.inventory().getItem(slot).copy());
        InteractionResult result=actor.gameMode.useItem(actor,level,actor.getInventory().getItem(0),InteractionHand.MAIN_HAND);
        companion.inventory().setItem(slot,actor.getInventory().getItem(0).copy()); actor.getInventory().setItem(0,ItemStack.EMPTY); return result;
    }

    public void attack(ServerLevel level, Entity target, int slot) {
        FakePlayer actor = player(level);
        actor.getInventory().selected = 0;
        actor.getInventory().setItem(0, companion.inventory().getItem(slot).copy());
        actor.attack(target);
        companion.inventory().setItem(slot, actor.getInventory().getItem(0).copy());
        actor.getInventory().setItem(0, ItemStack.EMPTY);
    }

    /** Runs the entity's ordinary player interaction callback, then transfers any new lead to the companion body. */
    public InteractionResult interactEntity(ServerLevel level, Entity target, int slot) {
        if (slot < 0 || slot >= companion.inventory().getContainerSize()) return InteractionResult.FAIL;
        FakePlayer actor = player(level);
        actor.getInventory().selected = 0;
        actor.getInventory().setItem(0, companion.inventory().getItem(slot).copy());
        InteractionResult result = actor.interactOn(target, InteractionHand.MAIN_HAND);
        companion.inventory().setItem(slot, actor.getInventory().getItem(0).copy());
        actor.getInventory().setItem(0, ItemStack.EMPTY);
        if (target instanceof Mob mob && mob.getLeashHolder() == actor) mob.setLeashedTo(companion, true);
        return result;
    }

    /** Uses the normal server-player bed checks, then transfers sleep and respawn state to the companion body. */
    public boolean sleepInBed(ServerLevel level, BlockHitResult hit) {
        FakePlayer actor=player(level);if(actor.isSleeping())actor.stopSleeping();
        actor.setRespawnPosition(level.dimension(),null,0,false,false);actor.getInventory().selected=0;actor.getInventory().setItem(0,ItemStack.EMPTY);
        InteractionResult result=actor.gameMode.useItemOn(actor,level,actor.getMainHandItem(),InteractionHand.MAIN_HAND,hit);
        BlockPos spawn=actor.getRespawnPosition();if(result.consumesAction()&&spawn!=null&&level.dimension().equals(actor.getRespawnDimension()))companion.setRespawnPoint(level.dimension(),spawn,companion.getYRot());
        if(!actor.isSleeping())return false;
        BlockPos sleeping=actor.getSleepingPos().orElse(null);actor.stopSleeping();if(sleeping==null)return false;
        companion.startSleeping(sleeping);return companion.isSleeping();
    }

    /** Selects and completes one merchant offer through payment and result slots after normal entity access succeeds. */
    public boolean trade(ServerLevel level,Entity target,int offerIndex){
        if(!(target instanceof Merchant merchant)||offerIndex<0)return false;FakePlayer actor=player(level);syncToPlayer(actor);int handSlot=firstEmptySlot(-1);actor.getInventory().selected=handSlot<9?handSlot:0;
        ItemStack held=actor.getMainHandItem().copy();actor.getInventory().setItem(actor.getInventory().selected,ItemStack.EMPTY);InteractionResult access=actor.interactOn(target,InteractionHand.MAIN_HAND);actor.getInventory().setItem(actor.getInventory().selected,held);
        if(!access.consumesAction()||merchant.getTradingPlayer()!=actor||offerIndex>=merchant.getOffers().size()){merchant.setTradingPlayer(null);clearPlayer(actor);return false;}
        MerchantOffer offer=merchant.getOffers().get(offerIndex);if(offer.isOutOfStock()){merchant.setTradingPlayer(null);clearPlayer(actor);return false;}ItemStack result=offer.getResult();int before=countItem(actor.getInventory(),result),uses=offer.getUses();
        MerchantMenu menu=new MerchantMenu(0,actor.getInventory(),merchant);menu.setSelectionHint(offerIndex);menu.tryMoveItems(offerIndex);if(!menu.getSlot(2).hasItem()){menu.removed(actor);syncFromPlayer(actor);clearPlayer(actor);return false;}
        menu.clicked(2,0,ClickType.QUICK_MOVE,actor);menu.removed(actor);syncFromPlayer(actor);boolean completed=offer.getUses()==uses+1&&countItem(companion.inventory(),result)>=before+result.getCount();clearPlayer(actor);return completed;
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

    /** Fills an explicit 3x3 pattern through normal menu clicks; each non-negative entry names a companion inventory slot. */
    public boolean craftPattern(ServerLevel level,BlockHitResult hit,int[] ingredientSlots){
        if(ingredientSlots==null||ingredientSlots.length!=9)return false;
        for(int slot:ingredientSlots)if(slot>=companion.inventory().getContainerSize()||(slot>=0&&companion.inventory().getItem(slot).isEmpty()))return false;
        FakePlayer actor=player(level);syncToPlayer(actor);int handSlot=firstEmptySlot(-1);actor.getInventory().selected=handSlot<9?handSlot:0;
        ItemStack handBefore=actor.getMainHandItem().copy();actor.getInventory().setItem(actor.getInventory().selected,ItemStack.EMPTY);
        InteractionResult access=actor.gameMode.useItemOn(actor,level,actor.getMainHandItem(),InteractionHand.MAIN_HAND,hit);actor.getInventory().setItem(actor.getInventory().selected,handBefore);
        if(!access.consumesAction()){clearPlayer(actor);return false;}
        CraftingMenu menu=new CraftingMenu(0,actor.getInventory(),ContainerLevelAccess.create(level,hit.getBlockPos()));
        for(int grid=0;grid<9;grid++){
            int source=ingredientSlots[grid];if(source<0)continue;int menuSlot=playerMenuSlot(menu,actor,source);
            if(menuSlot<0||!menu.getSlot(menuSlot).hasItem()){menu.removed(actor);clearPlayer(actor);return false;}
            menu.clicked(menuSlot,0,ClickType.PICKUP,actor);menu.clicked(grid+1,1,ClickType.PICKUP,actor);menu.clicked(menuSlot,0,ClickType.PICKUP,actor);
        }
        if(!menu.getSlot(0).hasItem()){menu.removed(actor);syncFromPlayer(actor);clearPlayer(actor);return false;}
        ItemStack result=menu.getSlot(0).getItem().copy();int before=countItem(actor.getInventory(),result);
        menu.clicked(0,0,ClickType.QUICK_MOVE,actor);menu.removed(actor);syncFromPlayer(actor);boolean received=countItem(companion.inventory(),result)>=before+result.getCount();clearPlayer(actor);return received;
    }

    public boolean loadFurnace(ServerLevel level,BlockHitResult hit,int inputSlot,int fuelSlot){
        if(inputSlot<0||fuelSlot<0||inputSlot>=companion.inventory().getContainerSize()||fuelSlot>=companion.inventory().getContainerSize())return false;
        FakePlayer actor=player(level);syncToPlayer(actor);int handSlot=firstEmptySlot(-1);actor.getInventory().selected=handSlot<9?handSlot:0;
        ItemStack held=actor.getMainHandItem().copy();actor.getInventory().setItem(actor.getInventory().selected,ItemStack.EMPTY);
        InteractionResult access=actor.gameMode.useItemOn(actor,level,actor.getMainHandItem(),InteractionHand.MAIN_HAND,hit);actor.getInventory().setItem(actor.getInventory().selected,held);
        actor.closeContainer();BlockEntity blockEntity=level.getBlockEntity(hit.getBlockPos());if(!access.consumesAction()||!(blockEntity instanceof AbstractFurnaceBlockEntity furnace)){clearPlayer(actor);return false;}
        AbstractFurnaceMenu menu=furnaceMenu(actor,furnace);int inputMenu=playerMenuSlot(menu,actor,inputSlot),fuelMenu=playerMenuSlot(menu,actor,fuelSlot);
        int inputBefore=furnace.getItem(0).getCount(),fuelBefore=furnace.getItem(1).getCount();if(inputMenu>=0)menu.clicked(inputMenu,0,ClickType.QUICK_MOVE,actor);if(fuelMenu>=0)menu.clicked(fuelMenu,0,ClickType.QUICK_MOVE,actor);
        menu.removed(actor);syncFromPlayer(actor);boolean loaded=furnace.getItem(0).getCount()>inputBefore&&furnace.getItem(1).getCount()>fuelBefore;clearPlayer(actor);return loaded;
    }

    public boolean collectFurnace(ServerLevel level,BlockHitResult hit){
        FakePlayer actor=player(level);syncToPlayer(actor);int handSlot=emptySlot();if(handSlot<0){clearPlayer(actor);return false;}actor.getInventory().selected=handSlot<9?handSlot:0;
        ItemStack held=actor.getMainHandItem().copy();actor.getInventory().setItem(actor.getInventory().selected,ItemStack.EMPTY);
        InteractionResult access=actor.gameMode.useItemOn(actor,level,actor.getMainHandItem(),InteractionHand.MAIN_HAND,hit);actor.getInventory().setItem(actor.getInventory().selected,held);
        actor.closeContainer();BlockEntity blockEntity=level.getBlockEntity(hit.getBlockPos());if(!access.consumesAction()||!(blockEntity instanceof AbstractFurnaceBlockEntity furnace)||furnace.getItem(2).isEmpty()){clearPlayer(actor);return false;}
        ItemStack result=furnace.getItem(2).copy();int before=countItem(actor.getInventory(),result);AbstractFurnaceMenu menu=furnaceMenu(actor,furnace);int destinationMenu=playerMenuSlot(menu,actor,handSlot);if(destinationMenu<0){menu.removed(actor);clearPlayer(actor);return false;}menu.clicked(2,0,ClickType.PICKUP,actor);menu.clicked(destinationMenu,0,ClickType.PICKUP,actor);menu.removed(actor);syncFromPlayer(actor);
        boolean collected=countItem(companion.inventory(),result)>=before+result.getCount()&&furnace.getItem(2).isEmpty();clearPlayer(actor);return collected;
    }

    /** Selects a requested stonecutting result and takes it through the real result slot callback. */
    public boolean stonecut(ServerLevel level,BlockHitResult hit,int inputSlot,Item requestedResult){
        if(inputSlot<0||inputSlot>=companion.inventory().getContainerSize()||companion.inventory().getItem(inputSlot).isEmpty()||requestedResult==null)return false;
        FakePlayer actor=player(level);syncToPlayer(actor);int handSlot=firstEmptySlot(inputSlot);actor.getInventory().selected=handSlot<9?handSlot:0;
        ItemStack held=actor.getMainHandItem().copy();actor.getInventory().setItem(actor.getInventory().selected,ItemStack.EMPTY);
        InteractionResult access=actor.gameMode.useItemOn(actor,level,actor.getMainHandItem(),InteractionHand.MAIN_HAND,hit);actor.getInventory().setItem(actor.getInventory().selected,held);
        if(!access.consumesAction()){clearPlayer(actor);return false;}
        StonecutterMenu menu=new StonecutterMenu(0,actor.getInventory(),ContainerLevelAccess.create(level,hit.getBlockPos()));int sourceMenu=playerMenuSlot(menu,actor,inputSlot);ItemStack input=actor.getInventory().getItem(inputSlot).copy();
        int inputBefore=countItem(actor.getInventory(),input),resultBefore=countItem(actor.getInventory(),new ItemStack(requestedResult));
        if(sourceMenu<0){menu.removed(actor);clearPlayer(actor);return false;}menu.clicked(sourceMenu,0,ClickType.QUICK_MOVE,actor);
        int recipe=-1;for(int i=0;i<menu.getRecipes().size();i++)if(menu.getRecipes().get(i).value().getResultItem(level.registryAccess()).is(requestedResult)){recipe=i;break;}
        if(recipe<0||!menu.clickMenuButton(actor,recipe)||!menu.getSlot(1).hasItem()){menu.removed(actor);syncFromPlayer(actor);clearPlayer(actor);return false;}
        ItemStack result=menu.getSlot(1).getItem().copy();int destinationMenu=playerMenuSlot(menu,actor,handSlot);if(destinationMenu<0){menu.removed(actor);clearPlayer(actor);return false;}menu.clicked(1,0,ClickType.PICKUP,actor);menu.clicked(destinationMenu,0,ClickType.PICKUP,actor);menu.removed(actor);syncFromPlayer(actor);
        boolean exact=countItem(companion.inventory(),input)==inputBefore-1&&countItem(companion.inventory(),result)>=resultBefore+result.getCount();clearPlayer(actor);return exact;
    }

    /** Repairs or disenchants through the real grindstone input and result callbacks. */
    public boolean grind(ServerLevel level,BlockHitResult hit,int inputSlot,int additionalSlot){
        if(inputSlot<0||additionalSlot<0||inputSlot==additionalSlot||inputSlot>=companion.inventory().getContainerSize()||additionalSlot>=companion.inventory().getContainerSize())return false;
        ItemStack first=companion.inventory().getItem(inputSlot),second=companion.inventory().getItem(additionalSlot);if(first.isEmpty()||second.isEmpty()||first.getItem()!=second.getItem())return false;
        FakePlayer actor=player(level);syncToPlayer(actor);int handSlot=firstEmptySlot(-1);actor.getInventory().selected=handSlot<9?handSlot:0;ItemStack held=actor.getMainHandItem().copy();actor.getInventory().setItem(actor.getInventory().selected,ItemStack.EMPTY);
        InteractionResult access=actor.gameMode.useItemOn(actor,level,actor.getMainHandItem(),InteractionHand.MAIN_HAND,hit);actor.getInventory().setItem(actor.getInventory().selected,held);if(!access.consumesAction()){clearPlayer(actor);return false;}
        GrindstoneMenu menu=new GrindstoneMenu(0,actor.getInventory(),ContainerLevelAccess.create(level,hit.getBlockPos()));int firstMenu=playerMenuSlot(menu,actor,inputSlot),secondMenu=playerMenuSlot(menu,actor,additionalSlot),destinationMenu=playerMenuSlot(menu,actor,handSlot);int before=countItemType(actor.getInventory(),first.getItem());
        if(firstMenu<0||secondMenu<0||destinationMenu<0){menu.removed(actor);clearPlayer(actor);return false;}
        menu.clicked(firstMenu,0,ClickType.PICKUP,actor);menu.clicked(0,1,ClickType.PICKUP,actor);menu.clicked(firstMenu,0,ClickType.PICKUP,actor);menu.clicked(secondMenu,0,ClickType.PICKUP,actor);menu.clicked(1,1,ClickType.PICKUP,actor);menu.clicked(secondMenu,0,ClickType.PICKUP,actor);
        if(!menu.getSlot(2).hasItem()){menu.removed(actor);syncFromPlayer(actor);clearPlayer(actor);return false;}ItemStack result=menu.getSlot(2).getItem().copy();menu.clicked(2,0,ClickType.PICKUP,actor);menu.clicked(destinationMenu,0,ClickType.PICKUP,actor);menu.removed(actor);syncFromPlayer(actor);
        boolean exact=countItemType(companion.inventory(),first.getItem())==before-1&&countItem(companion.inventory(),result)>=result.getCount();clearPlayer(actor);return exact;
    }

    /** Applies one selectable banner pattern through the real loom input, selection, and result callbacks. */
    public boolean weaveBanner(ServerLevel level,BlockHitResult hit,int bannerSlot,int dyeSlot,int patternIndex){
        if(bannerSlot<0||dyeSlot<0||bannerSlot==dyeSlot||bannerSlot>=companion.inventory().getContainerSize()||dyeSlot>=companion.inventory().getContainerSize())return false;
        ItemStack banner=companion.inventory().getItem(bannerSlot).copy(),dye=companion.inventory().getItem(dyeSlot).copy();if(banner.isEmpty()||dye.isEmpty())return false;
        FakePlayer actor=player(level);syncToPlayer(actor);int destination=emptySlot();if(destination<0){clearPlayer(actor);return false;}actor.getInventory().selected=destination<9?destination:0;ItemStack held=actor.getMainHandItem().copy();actor.getInventory().setItem(actor.getInventory().selected,ItemStack.EMPTY);
        InteractionResult access=actor.gameMode.useItemOn(actor,level,actor.getMainHandItem(),InteractionHand.MAIN_HAND,hit);actor.getInventory().setItem(actor.getInventory().selected,held);if(!access.consumesAction()){clearPlayer(actor);return false;}
        LoomMenu menu=new LoomMenu(0,actor.getInventory(),ContainerLevelAccess.create(level,hit.getBlockPos()));int bannerMenu=playerMenuSlot(menu,actor,bannerSlot),dyeMenu=playerMenuSlot(menu,actor,dyeSlot),destinationMenu=playerMenuSlot(menu,actor,destination);int bannerBefore=countItem(actor.getInventory(),banner),dyeBefore=countItem(actor.getInventory(),dye);
        if(bannerMenu<0||dyeMenu<0||destinationMenu<0){menu.removed(actor);clearPlayer(actor);return false;}menu.clicked(bannerMenu,0,ClickType.PICKUP,actor);menu.clicked(menu.slots.indexOf(menu.getBannerSlot()),1,ClickType.PICKUP,actor);menu.clicked(bannerMenu,0,ClickType.PICKUP,actor);menu.clicked(dyeMenu,0,ClickType.PICKUP,actor);menu.clicked(menu.slots.indexOf(menu.getDyeSlot()),1,ClickType.PICKUP,actor);menu.clicked(dyeMenu,0,ClickType.PICKUP,actor);
        if(patternIndex<0||patternIndex>=menu.getSelectablePatterns().size()||!menu.clickMenuButton(actor,patternIndex)||!menu.getResultSlot().hasItem()){menu.removed(actor);syncFromPlayer(actor);clearPlayer(actor);return false;}ItemStack result=menu.getResultSlot().getItem().copy();menu.clicked(menu.slots.indexOf(menu.getResultSlot()),0,ClickType.PICKUP,actor);menu.clicked(destinationMenu,0,ClickType.PICKUP,actor);menu.removed(actor);syncFromPlayer(actor);
        boolean exact=countItem(companion.inventory(),banner)==bannerBefore-1&&countItem(companion.inventory(),dye)==dyeBefore-1&&countItem(companion.inventory(),result)>=result.getCount();clearPlayer(actor);return exact;
    }

    /** Renames one item through the real anvil result callback and charges the companion's own XP level. */
    public boolean renameAtAnvil(ServerLevel level,BlockHitResult hit,int inputSlot,String name){
        if(inputSlot<0||inputSlot>=companion.inventory().getContainerSize()||companion.inventory().getItem(inputSlot).isEmpty()||name==null||name.isBlank()||name.length()>50)return false;
        ItemStack input=companion.inventory().getItem(inputSlot).copy();FakePlayer actor=player(level);syncToPlayer(actor);actor.experienceLevel=companion.experienceLevel();actor.totalExperience=companion.totalExperience();actor.experienceProgress=companion.experienceProgress();int destination=emptySlot();if(destination<0){clearPlayer(actor);return false;}actor.getInventory().selected=destination<9?destination:0;
        ItemStack held=actor.getMainHandItem().copy();actor.getInventory().setItem(actor.getInventory().selected,ItemStack.EMPTY);InteractionResult access=actor.gameMode.useItemOn(actor,level,actor.getMainHandItem(),InteractionHand.MAIN_HAND,hit);actor.getInventory().setItem(actor.getInventory().selected,held);if(!access.consumesAction()){clearPlayer(actor);return false;}
        AnvilMenu menu=new AnvilMenu(0,actor.getInventory(),ContainerLevelAccess.create(level,hit.getBlockPos()));int sourceMenu=playerMenuSlot(menu,actor,inputSlot),destinationMenu=playerMenuSlot(menu,actor,destination),levelBefore=actor.experienceLevel;if(sourceMenu<0||destinationMenu<0){menu.removed(actor);clearPlayer(actor);return false;}
        menu.clicked(sourceMenu,0,ClickType.PICKUP,actor);menu.clicked(0,1,ClickType.PICKUP,actor);menu.clicked(sourceMenu,0,ClickType.PICKUP,actor);if(!menu.setItemName(name)||!menu.getSlot(2).hasItem()||menu.getCost()<=0||menu.getCost()>levelBefore){menu.removed(actor);syncFromPlayer(actor);clearPlayer(actor);return false;}ItemStack result=menu.getSlot(2).getItem().copy();int cost=menu.getCost();menu.clicked(2,0,ClickType.PICKUP,actor);menu.clicked(destinationMenu,0,ClickType.PICKUP,actor);menu.removed(actor);syncFromPlayer(actor);companion.setExperience(actor.experienceLevel,actor.totalExperience,actor.experienceProgress);
        boolean exact=countItem(companion.inventory(),input)==0&&countItem(companion.inventory(),result)>=result.getCount()&&companion.experienceLevel()==levelBefore-cost;clearPlayer(actor);return exact;
    }

    /** Enchants one item through the real table slots and charges Jev-owned lapis and XP. */
    public boolean enchant(ServerLevel level,BlockHitResult hit,int itemSlot,int lapisSlot,int option){
        if(itemSlot<0||lapisSlot<0||itemSlot==lapisSlot||itemSlot>=companion.inventory().getContainerSize()||lapisSlot>=companion.inventory().getContainerSize()||option<0||option>2)return false;
        ItemStack input=companion.inventory().getItem(itemSlot).copy(),lapis=companion.inventory().getItem(lapisSlot).copy();if(input.isEmpty()||!lapis.is(net.minecraft.world.item.Items.LAPIS_LAZULI))return false;
        FakePlayer actor=player(level);syncToPlayer(actor);actor.experienceLevel=companion.experienceLevel();actor.totalExperience=companion.totalExperience();actor.experienceProgress=companion.experienceProgress();int handSlot=firstEmptySlot(-1);actor.getInventory().selected=handSlot<9?handSlot:0;ItemStack held=actor.getMainHandItem().copy();actor.getInventory().setItem(actor.getInventory().selected,ItemStack.EMPTY);
        InteractionResult access=actor.gameMode.useItemOn(actor,level,actor.getMainHandItem(),InteractionHand.MAIN_HAND,hit);actor.getInventory().setItem(actor.getInventory().selected,held);if(!access.consumesAction()){clearPlayer(actor);return false;}
        EnchantmentMenu menu=new EnchantmentMenu(0,actor.getInventory(),ContainerLevelAccess.create(level,hit.getBlockPos()));int itemMenu=playerMenuSlot(menu,actor,itemSlot),lapisMenu=playerMenuSlot(menu,actor,lapisSlot),lapisBefore=countItem(actor.getInventory(),lapis),levelBefore=actor.experienceLevel;if(itemMenu<0||lapisMenu<0){menu.removed(actor);clearPlayer(actor);return false;}
        menu.clicked(itemMenu,0,ClickType.PICKUP,actor);menu.clicked(0,1,ClickType.PICKUP,actor);menu.clicked(itemMenu,0,ClickType.PICKUP,actor);menu.clicked(lapisMenu,0,ClickType.PICKUP,actor);menu.clicked(1,1,ClickType.PICKUP,actor);menu.clicked(lapisMenu,0,ClickType.PICKUP,actor);
        if(menu.costs[option]<=0||menu.costs[option]>levelBefore||!menu.clickMenuButton(actor,option)){menu.removed(actor);syncFromPlayer(actor);clearPlayer(actor);return false;}ItemStack result=menu.getSlot(0).getItem().copy();menu.removed(actor);syncFromPlayer(actor);companion.setExperience(actor.experienceLevel,actor.totalExperience,actor.experienceProgress);
        boolean exact=countItem(companion.inventory(),input)==0&&countItem(companion.inventory(),result)>=result.getCount()&&countItem(companion.inventory(),lapis)==lapisBefore-(option+1)&&companion.experienceLevel()==levelBefore-(option+1);clearPlayer(actor);return exact;
    }

    /** Completes one three-input upgrade through the real smithing result callback. */
    public boolean smith(ServerLevel level,BlockHitResult hit,int templateSlot,int baseSlot,int additionSlot){
        if(templateSlot<0||baseSlot<0||additionSlot<0||templateSlot==baseSlot||templateSlot==additionSlot||baseSlot==additionSlot||templateSlot>=companion.inventory().getContainerSize()||baseSlot>=companion.inventory().getContainerSize()||additionSlot>=companion.inventory().getContainerSize())return false;
        ItemStack template=companion.inventory().getItem(templateSlot).copy(),base=companion.inventory().getItem(baseSlot).copy(),addition=companion.inventory().getItem(additionSlot).copy();if(template.isEmpty()||base.isEmpty()||addition.isEmpty())return false;
        FakePlayer actor=player(level);syncToPlayer(actor);int destination=emptySlot();if(destination<0){clearPlayer(actor);return false;}actor.getInventory().selected=destination<9?destination:0;ItemStack held=actor.getMainHandItem().copy();actor.getInventory().setItem(actor.getInventory().selected,ItemStack.EMPTY);InteractionResult access=actor.gameMode.useItemOn(actor,level,actor.getMainHandItem(),InteractionHand.MAIN_HAND,hit);actor.getInventory().setItem(actor.getInventory().selected,held);if(!access.consumesAction()){clearPlayer(actor);return false;}
        SmithingMenu menu=new SmithingMenu(0,actor.getInventory(),ContainerLevelAccess.create(level,hit.getBlockPos()));int[] sources={playerMenuSlot(menu,actor,templateSlot),playerMenuSlot(menu,actor,baseSlot),playerMenuSlot(menu,actor,additionSlot)};int destinationMenu=playerMenuSlot(menu,actor,destination);if(sources[0]<0||sources[1]<0||sources[2]<0||destinationMenu<0){menu.removed(actor);clearPlayer(actor);return false;}
        for(int i=0;i<3;i++){menu.clicked(sources[i],0,ClickType.PICKUP,actor);menu.clicked(i,1,ClickType.PICKUP,actor);menu.clicked(sources[i],0,ClickType.PICKUP,actor);}int resultSlot=menu.getResultSlot();if(!menu.getSlot(resultSlot).hasItem()){menu.removed(actor);syncFromPlayer(actor);clearPlayer(actor);return false;}ItemStack result=menu.getSlot(resultSlot).getItem().copy();menu.clicked(resultSlot,0,ClickType.PICKUP,actor);menu.clicked(destinationMenu,0,ClickType.PICKUP,actor);menu.removed(actor);syncFromPlayer(actor);
        boolean exact=countItem(companion.inventory(),template)==template.getCount()-1&&countItem(companion.inventory(),base)==base.getCount()-1&&countItem(companion.inventory(),addition)==addition.getCount()-1&&countItem(companion.inventory(),result)>=result.getCount();clearPlayer(actor);return exact;
    }

    public boolean loadBrewingStand(ServerLevel level,BlockHitResult hit,int bottleSlot,int ingredientSlot,int fuelSlot){
        if(bottleSlot<0||ingredientSlot<0||fuelSlot<0||bottleSlot==ingredientSlot||bottleSlot==fuelSlot||ingredientSlot==fuelSlot||bottleSlot>=companion.inventory().getContainerSize()||ingredientSlot>=companion.inventory().getContainerSize()||fuelSlot>=companion.inventory().getContainerSize())return false;
        Item bottle=companion.inventory().getItem(bottleSlot).getItem(),ingredient=companion.inventory().getItem(ingredientSlot).getItem(),fuel=companion.inventory().getItem(fuelSlot).getItem();
        FakePlayer actor=player(level);syncToPlayer(actor);int handSlot=firstEmptySlot(-1);actor.getInventory().selected=handSlot<9?handSlot:0;ItemStack held=actor.getMainHandItem().copy();actor.getInventory().setItem(actor.getInventory().selected,ItemStack.EMPTY);InteractionResult access=actor.gameMode.useItemOn(actor,level,actor.getMainHandItem(),InteractionHand.MAIN_HAND,hit);actor.getInventory().setItem(actor.getInventory().selected,held);actor.closeContainer();BlockEntity blockEntity=level.getBlockEntity(hit.getBlockPos());if(!access.consumesAction()||!(blockEntity instanceof BrewingStandBlockEntity stand)){clearPlayer(actor);return false;}
        BrewingStandMenu menu=new BrewingStandMenu(0,actor.getInventory(),stand,new SimpleContainerData(2));int[] sourceSlots={bottleSlot,ingredientSlot,fuelSlot};for(int source:sourceSlots){int menuSlot=playerMenuSlot(menu,actor,source);if(menuSlot<0){menu.removed(actor);clearPlayer(actor);return false;}menu.clicked(menuSlot,0,ClickType.QUICK_MOVE,actor);}menu.removed(actor);syncFromPlayer(actor);boolean loaded=stand.getItem(0).is(bottle)&&stand.getItem(3).is(ingredient)&&stand.getItem(4).is(fuel);clearPlayer(actor);return loaded;
    }

    public boolean collectBrewingStand(ServerLevel level,BlockHitResult hit,int standSlot){
        if(standSlot<0||standSlot>2)return false;FakePlayer actor=player(level);syncToPlayer(actor);int destination=emptySlot();if(destination<0){clearPlayer(actor);return false;}actor.getInventory().selected=destination<9?destination:0;ItemStack held=actor.getMainHandItem().copy();actor.getInventory().setItem(actor.getInventory().selected,ItemStack.EMPTY);InteractionResult access=actor.gameMode.useItemOn(actor,level,actor.getMainHandItem(),InteractionHand.MAIN_HAND,hit);actor.getInventory().setItem(actor.getInventory().selected,held);actor.closeContainer();BlockEntity blockEntity=level.getBlockEntity(hit.getBlockPos());if(!access.consumesAction()||!(blockEntity instanceof BrewingStandBlockEntity stand)||stand.getItem(standSlot).isEmpty()){clearPlayer(actor);return false;}
        BrewingStandMenu menu=new BrewingStandMenu(0,actor.getInventory(),stand,new SimpleContainerData(2));int destinationMenu=playerMenuSlot(menu,actor,destination);if(destinationMenu<0){menu.removed(actor);clearPlayer(actor);return false;}ItemStack result=stand.getItem(standSlot).copy();int before=countItem(actor.getInventory(),result);menu.clicked(standSlot,0,ClickType.PICKUP,actor);menu.clicked(destinationMenu,0,ClickType.PICKUP,actor);menu.removed(actor);syncFromPlayer(actor);boolean exact=stand.getItem(standSlot).isEmpty()&&countItem(companion.inventory(),result)>=before+result.getCount();clearPlayer(actor);return exact;
    }

    private static AbstractFurnaceMenu furnaceMenu(FakePlayer actor,AbstractFurnaceBlockEntity furnace){
        SimpleContainerData data=new SimpleContainerData(4);
        if(furnace instanceof SmokerBlockEntity)return new SmokerMenu(0,actor.getInventory(),furnace,data);
        if(furnace instanceof BlastFurnaceBlockEntity)return new BlastFurnaceMenu(0,actor.getInventory(),furnace,data);
        return new FurnaceMenu(0,actor.getInventory(),furnace,data);
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
    private int emptySlot(){for(int i=0;i<companion.inventory().getContainerSize();i++)if(companion.inventory().getItem(i).isEmpty())return i;return -1;}
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
    private static int countItemType(Container container,Item target){int total=0;for(int i=0;i<container.getContainerSize();i++)if(container.getItem(i).is(target))total+=container.getItem(i).getCount();return total;}
}
