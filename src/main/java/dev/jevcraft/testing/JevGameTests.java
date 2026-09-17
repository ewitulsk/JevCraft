package dev.jevcraft.testing;

import dev.jevcraft.JevCraft;
import dev.jevcraft.companion.JevCompanion;
import dev.jevcraft.companion.JevInferenceHost;
import dev.jevcraft.companion.JevWorldData;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

@GameTestHolder("jevcraft")
@PrefixGameTestTemplate(false)
public final class JevGameTests {
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void companionBodyInventoryAndGoalPersist(GameTestHelper helper) {
        UUID owner = UUID.randomUUID(), administrator = UUID.randomUUID();
        JevCompanion original = helper.spawn(JevCraft.JEV.get(), new BlockPos(2, 1, 2));
        original.setOwner(owner); original.addAdministrator(administrator); original.acceptGoal("follow me"); original.inventory().setItem(0, new ItemStack(Items.OAK_LOG, 4));
        CompoundTag saved = new CompoundTag(); original.addAdditionalSaveData(saved);
        JevCompanion restored = JevCraft.JEV.get().create(helper.getLevel());
        helper.assertTrue(restored != null, "registered companion type did not create");
        restored.readAdditionalSaveData(saved);
        helper.assertValueEqual(restored.owner(), owner, "owner UUID persistence");
        helper.assertTrue(restored.administrators().contains(administrator), "administrator UUID persistence");
        helper.assertValueEqual(restored.currentGoal(), "follow me", "goal persistence");
        helper.assertValueEqual(restored.inventory().getItem(0).getCount(), 4, "inventory persistence");
        original.stopNow(); helper.assertTrue(original.currentGoal().isEmpty(), "stop did not synchronously clear goal");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void companionIsLivingPersistentAndPlayerSized(GameTestHelper helper) {
        JevCompanion entity = helper.spawn(JevCraft.JEV.get(), new BlockPos(2, 1, 2));
        helper.assertTrue(entity.isAlive(), "companion did not spawn alive");
        helper.assertValueEqual((int) entity.getMaxHealth(), 20, "player-like max health");
        helper.assertTrue(entity.requiresCustomPersistence(), "companion may despawn when owners are absent");
        helper.assertTrue(entity.getBbHeight() >= 1.7f && entity.getBbHeight() <= 1.9f, "companion is not player-sized");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 240)
    public static void companionMinesThroughPlayerInteractionContext(GameTestHelper helper) {
        BlockPos relativeTarget = new BlockPos(3, 1, 2);
        helper.setBlock(relativeTarget, Blocks.OAK_LOG);
        JevCompanion entity = helper.spawn(JevCraft.JEV.get(), new BlockPos(2, 1, 2));
        entity.setOwner(UUID.randomUUID()); entity.acceptGoal("collect four logs");
        entity.inventory().setItem(0, new ItemStack(Items.WOODEN_AXE));
        BlockPos absoluteTarget = helper.absolutePos(relativeTarget);
        entity.actions().beginMine(absoluteTarget, entity.goalVersion());
        helper.succeedWhen(() -> helper.assertTrue(helper.getLevel().getBlockState(absoluteTarget).isAir(),
                "companion did not mine the log; result=" + entity.actions().lastResult() + " progress=" + entity.actions().miningProgress()));
    }

    @GameTest(template = "empty", timeoutTicks = 20000)
    public static void liveJevSelectsAndMinesVisibleGoalBlock(GameTestHelper helper) {
        if (!JevInferenceHost.instance().enabled()) { helper.succeed(); return; }
        BlockPos relativeTarget = new BlockPos(3, 1, 2);
        helper.setBlock(relativeTarget, Blocks.OAK_LOG);
        JevCompanion entity = helper.spawn(JevCraft.JEV.get(), new BlockPos(2, 1, 2));
        entity.setOwner(UUID.randomUUID()); entity.inventory().setItem(0, new ItemStack(Items.WOODEN_AXE));
        entity.acceptGoal("collect four oak logs");
        BlockPos absoluteTarget = helper.absolutePos(relativeTarget);
        helper.succeedWhen(() -> helper.assertTrue(helper.getLevel().getBlockState(absoluteTarget).isAir(),
                "live Jev did not select and mine the visible log; action=" + entity.actions().lastResult()));
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void companionPlacesThroughNormalUsePipeline(GameTestHelper helper) {
        BlockPos support = new BlockPos(3, 0, 2), destination = support.above();
        helper.setBlock(support, Blocks.STONE);
        JevCompanion entity = helper.spawn(JevCraft.JEV.get(), new BlockPos(2, 1, 2));
        entity.inventory().setItem(0, new ItemStack(Items.COBBLESTONE, 2));
        BlockPos absoluteSupport = helper.absolutePos(support);
        var result = entity.actions().place(helper.getLevel(), new BlockHitResult(Vec3.atCenterOf(absoluteSupport), Direction.UP, absoluteSupport, false), 0);
        helper.assertTrue(result.consumesAction(), "normal use pipeline rejected placement");
        helper.assertTrue(helper.getLevel().getBlockState(helper.absolutePos(destination)).is(Blocks.COBBLESTONE), "placement did not change authoritative world state");
        helper.assertValueEqual(entity.inventory().getItem(0).getCount(), 1, "placement did not consume exactly one block");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void companionCombatUsesDedicatedPlayerContext(GameTestHelper helper) {
        JevCompanion entity = helper.spawn(JevCraft.JEV.get(), new BlockPos(2, 1, 2));
        var zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(3, 1, 2));
        entity.inventory().setItem(0, new ItemStack(Items.IRON_SWORD));
        float before = zombie.getHealth();
        helper.assertTrue(entity.actions().attack(helper.getLevel(), zombie, 0), "dedicated player context did not deal combat damage");
        helper.assertTrue(zombie.getHealth() < before, "combat result was not authoritative");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void companionDepositsThroughRealChestMenu(GameTestHelper helper) {
        BlockPos chestPos = new BlockPos(3, 1, 2);
        helper.setBlock(chestPos, Blocks.CHEST);
        JevCompanion entity = helper.spawn(JevCraft.JEV.get(), new BlockPos(2, 1, 2));
        entity.inventory().setItem(5, new ItemStack(Items.OAK_LOG, 7));
        BlockPos absolute = helper.absolutePos(chestPos);
        boolean moved = entity.actions().depositStack(helper.getLevel(),
                new BlockHitResult(Vec3.atCenterOf(absolute), Direction.UP, absolute, false), 5);
        helper.assertTrue(moved, "menu transaction did not report a verified transfer");
        helper.assertTrue(entity.inventory().getItem(5).isEmpty(), "source stack was not removed");
        helper.assertTrue(helper.getLevel().getBlockEntity(absolute) instanceof ChestBlockEntity, "chest block entity missing");
        ChestBlockEntity chest = (ChestBlockEntity) helper.getLevel().getBlockEntity(absolute);
        helper.assertValueEqual(chest.getItem(0).getCount(), 7, "chest did not receive exactly seven logs");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void companionCraftsThroughRealCraftingMenu(GameTestHelper helper) {
        BlockPos tablePos = new BlockPos(3, 1, 2);
        helper.setBlock(tablePos, Blocks.CRAFTING_TABLE);
        JevCompanion entity = helper.spawn(JevCraft.JEV.get(), new BlockPos(2, 1, 2));
        entity.inventory().setItem(5, new ItemStack(Items.OAK_LOG, 2));
        BlockPos absolute = helper.absolutePos(tablePos);
        boolean crafted = entity.actions().craftSingleIngredient(helper.getLevel(),
                new BlockHitResult(Vec3.atCenterOf(absolute), Direction.UP, absolute, false), 5);
        helper.assertTrue(crafted, "crafting menu transaction did not complete");
        helper.assertValueEqual(entity.inventory().getItem(5).getCount(), 1, "craft consumed the wrong number of logs");
        int planks = 0;
        for (int i = 0; i < entity.inventory().getContainerSize(); i++)
            if (entity.inventory().getItem(i).is(Items.OAK_PLANKS)) planks += entity.inventory().getItem(i).getCount();
        helper.assertValueEqual(planks, 4, "craft did not produce four oak planks");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void companionFoodUsesItemConsumptionCallbacks(GameTestHelper helper) {
        JevCompanion entity = helper.spawn(JevCraft.JEV.get(), new BlockPos(2, 1, 2));
        CompoundTag hungry = new CompoundTag(); entity.addAdditionalSaveData(hungry); hungry.putInt("Food", 10); entity.readAdditionalSaveData(hungry);
        entity.inventory().setItem(0, new ItemStack(Items.BREAD, 2));
        helper.assertTrue(entity.consumeFood(0), "edible item was not consumed");
        helper.assertValueEqual(entity.inventory().getItem(0).getCount(), 1, "food stack did not decrement exactly once");
        helper.assertTrue(entity.foodLevel() > 10 && entity.foodLevel() <= 20, "nutrition did not update bounded companion food");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void chatProvenanceAndBoundsPersist(GameTestHelper helper) {
        JevCompanion entity = helper.spawn(JevCraft.JEV.get(), new BlockPos(2, 1, 2));
        UUID sender = UUID.randomUUID();
        for (int i = 0; i < 40; i++) entity.observeChat(sender, "message-" + i, i % 2 == 0, false);
        helper.assertValueEqual(entity.recentChat().size(), 32, "chat history is not bounded");
        helper.assertValueEqual(entity.recentChat().get(0).text(), "message-8", "chat history did not evict oldest entries");
        CompoundTag saved = new CompoundTag(); entity.addAdditionalSaveData(saved);
        JevCompanion restored = JevCraft.JEV.get().create(helper.getLevel());
        helper.assertTrue(restored != null, "registered companion type did not create"); restored.readAdditionalSaveData(saved);
        helper.assertValueEqual(restored.recentChat().size(), 32, "chat history did not persist");
        helper.assertTrue(restored.recentChat().get(0).authorized(), "authorization provenance did not persist");
        helper.assertValueEqual(restored.recentChat().get(0).sender(), sender, "authenticated sender UUID did not persist");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void worldRosterEnforcesTenAndUniqueNames(GameTestHelper helper) {
        JevWorldData data = JevWorldData.get(helper.getLevel().getServer());
        java.util.List<UUID> ids = new java.util.ArrayList<>();
        for (int i = 0; i < 10; i++) {
            UUID id = UUID.randomUUID(); ids.add(id);
            helper.assertTrue(data.register(id, "Jev"), "roster rejected slot " + i);
            helper.assertTrue(data.name(id) != null, "roster did not assign a name");
        }
        helper.assertValueEqual(data.livingCount(), 10, "world roster count");
        helper.assertTrue(!data.register(UUID.randomUUID(), "Overflow"), "world roster accepted an eleventh living Jev");
        helper.assertValueEqual(ids.stream().map(data::name).map(String::toLowerCase).distinct().count(), 10L, "world roster names are not unique");
        UUID player = UUID.randomUUID();
        helper.assertValueEqual(data.grant(player), JevWorldData.GrantState.PENDING, "new grant state");
        data.grantDelivered(player); data.grantConsumed(player);
        helper.assertValueEqual(data.grant(player), JevWorldData.GrantState.CONSUMED, "consumed grant must never reissue");
        ids.forEach(data::unregister);
        helper.succeed();
    }
}
