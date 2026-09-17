package dev.jevcraft.testing;

import dev.jevcraft.JevCraft;
import dev.jevcraft.companion.JevCompanion;
import dev.jevcraft.companion.JevInferenceHost;
import dev.jevcraft.companion.JevWorldData;
import dev.jevcraft.companion.MovingChunkTickets;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.server.level.ServerPlayer;
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
        original.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET)); original.setExperience(7, 91, .4f);
        original.setOperatorTeleportAllowed(true); original.setRespawnPoint(helper.getLevel().dimension(), helper.absolutePos(new BlockPos(4, 1, 4)), 45);
        CompoundTag saved = new CompoundTag(); original.addAdditionalSaveData(saved);
        JevCompanion restored = JevCraft.JEV.get().create(helper.getLevel());
        helper.assertTrue(restored != null, "registered companion type did not create");
        restored.readAdditionalSaveData(saved);
        helper.assertValueEqual(restored.owner(), owner, "owner UUID persistence");
        helper.assertTrue(restored.administrators().contains(administrator), "administrator UUID persistence");
        helper.assertValueEqual(restored.currentGoal(), "follow me", "goal persistence");
        helper.assertValueEqual(restored.inventory().getItem(0).getCount(), 4, "inventory persistence");
        helper.assertTrue(restored.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).is(Items.IRON_HELMET), "equipment persistence");
        helper.assertValueEqual(restored.experienceLevel(), 7, "experience level persistence"); helper.assertValueEqual(restored.totalExperience(), 91, "total experience persistence");
        helper.assertTrue(Math.abs(restored.experienceProgress() - .4f) < .001f, "experience progress persistence");
        helper.assertTrue(restored.operatorTeleportAllowed(), "operator teleport flag persistence"); helper.assertValueEqual(restored.respawnDimension(), helper.getLevel().dimension(), "spawn dimension persistence");
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
    public static void teleportRequiresSeparateOperatorCapability(GameTestHelper helper) {
        JevCompanion entity = helper.spawn(JevCraft.JEV.get(), new BlockPos(2, 1, 2));
        Vec3 destination = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(5, 1, 2)));
        helper.assertTrue(!entity.actions().teleport(helper.getLevel(), destination), "ownership-free body received teleport authority");
        entity.setOperatorTeleportAllowed(true);
        helper.assertTrue(entity.actions().teleport(helper.getLevel(), destination), "operator-authorized teleport failed");
        helper.assertTrue(entity.position().distanceToSqr(destination) < .01, "teleport did not reach verified destination");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void portalTransferMovesTickingRegionBetweenDimensions(GameTestHelper helper) {
        var server=helper.getLevel().getServer(); var overworld=helper.getLevel(); var nether=server.getLevel(Level.NETHER);
        helper.assertTrue(nether!=null,"Nether level was unavailable");
        int overworldBefore=MovingChunkTickets.tickingTicketCount(overworld),netherBefore=MovingChunkTickets.tickingTicketCount(nether);
        JevCompanion original=helper.spawn(JevCraft.JEV.get(),new BlockPos(2,1,2));
        JevWorldData.get(server).register(original.getUUID(),"PortalJev");
        helper.assertValueEqual(MovingChunkTickets.tickingTicketCount(overworld),overworldBefore+25,"source ticking region was not activated");
        Vec3 destination=new Vec3(8.5,80,8.5);
        Entity transferred=original.changeDimension(new DimensionTransition(nether,destination,Vec3.ZERO,0,0,DimensionTransition.PLACE_PORTAL_TICKET));
        helper.assertTrue(transferred instanceof JevCompanion,"portal transition did not recreate the companion");
        helper.assertTrue(transferred.level()==nether&&transferred.getUUID().equals(original.getUUID()),"portal transition lost dimension or identity");
        helper.assertValueEqual(MovingChunkTickets.tickingTicketCount(overworld),overworldBefore,"source ticking region leaked after portal transition");
        helper.assertValueEqual(MovingChunkTickets.tickingTicketCount(nether),netherBefore+25,"destination ticking region was not activated");
        transferred.discard();
        helper.runAfterDelay(2,()->{
            JevWorldData.get(server).unregister(transferred.getUUID());
            helper.assertValueEqual(MovingChunkTickets.tickingTicketCount(nether),netherBefore,"destination ticking region leaked after removal");
            helper.succeed();
        });
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

    @GameTest(template = "empty", timeoutTicks = 100, batch = "roster")
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

    @GameTest(template = "empty", timeoutTicks = 240, batch = "lifecycle")
    public static void deathDropsOnceAndRespawnsPersistentIdentity(GameTestHelper helper) {
        JevWorldData data = JevWorldData.get(helper.getLevel().getServer());
        int initialRosterSize = data.livingCount(); UUID id = UUID.randomUUID(), owner = UUID.randomUUID(), admin = UUID.randomUUID();
        JevCompanion original = JevCraft.JEV.get().create(helper.getLevel());
        helper.assertTrue(original != null, "registered companion type did not create");
        original.setUUID(id); original.setOwner(owner); original.addAdministrator(admin); original.setCustomName(net.minecraft.network.chat.Component.literal("RespawnJev"));
        original.acceptGoal("follow me"); original.observeChat(owner, "persistent memory", true, true); original.inventory().setItem(0, new ItemStack(Items.OAK_LOG, 3));
        BlockPos spawn = helper.absolutePos(new BlockPos(2, 1, 2)); original.moveTo(spawn.getX() + .5, spawn.getY(), spawn.getZ() + .5, 0, 0);
        original.setRespawnPoint(helper.getLevel().dimension(), spawn, 30);
        helper.assertTrue(data.register(id, "RespawnJev"), "could not reserve lifecycle roster slot");
        helper.assertTrue(helper.getLevel().addFreshEntity(original), "could not add lifecycle Jev");
        original.kill();
        helper.assertValueEqual(data.pendingRespawnCount(), 1, "death did not enqueue one respawn");
        helper.assertValueEqual(data.livingCount(), initialRosterSize + 1, "death released the persistent roster slot");
        helper.succeedWhen(() -> {
            JevCompanion replacement = null;
            for (var candidate : helper.getLevel().getEntitiesOfClass(JevCompanion.class, new net.minecraft.world.phys.AABB(spawn).inflate(16)))
                if (candidate.getUUID().equals(id) && candidate != original) { replacement = candidate; break; }
            helper.assertTrue(replacement != null && replacement.isAlive(), "same-UUID Jev did not respawn");
            helper.assertValueEqual(replacement.owner(), owner, "respawn lost owner");
            helper.assertTrue(replacement.administrators().contains(admin), "respawn lost administrator");
            helper.assertValueEqual(replacement.currentGoal(), "follow me", "respawn lost goal");
            helper.assertValueEqual(replacement.recentChat().size(), 1, "respawn lost memory");
            helper.assertTrue(replacement.inventory().isEmpty(), "dropped inventory was also restored");
            int dropped = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new net.minecraft.world.phys.AABB(spawn).inflate(16)).stream()
                    .filter(item -> item.getItem().is(Items.OAK_LOG)).mapToInt(item -> item.getItem().getCount()).sum();
            helper.assertValueEqual(dropped, 3, "inventory did not drop exactly once");
            helper.assertValueEqual(data.pendingRespawnCount(), 0, "completed respawn remained queued");
            data.unregister(id);
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void nativeMessageRoutesByJevNameAndChecksAuthority(GameTestHelper helper) {
        ServerPlayer sender = helper.makeMockServerPlayerInLevel();
        JevCompanion entity = helper.spawn(JevCraft.JEV.get(), new BlockPos(2, 1, 2));
        entity.setCustomName(net.minecraft.network.chat.Component.literal("JevMsgTest")); entity.setOwner(sender.getUUID());
        helper.getLevel().getServer().getCommands().performPrefixedCommand(sender.createCommandSourceStack(), "jev admin JevMsgTest add test-mock-player");
        helper.assertTrue(entity.administrators().contains(sender.getUUID()), "owner could not add a UUID administrator");
        helper.getLevel().getServer().getCommands().performPrefixedCommand(sender.createCommandSourceStack(), "jev operator JevMsgTest enable");
        helper.assertTrue(!entity.operatorTeleportAllowed(), "ordinary owner granted operator teleport authority");
        helper.getLevel().getServer().getCommands().performPrefixedCommand(helper.getLevel().getServer().createCommandSourceStack(), "jev operator JevMsgTest enable");
        helper.assertTrue(entity.operatorTeleportAllowed(), "server operator could not grant teleport authority");
        helper.getLevel().getServer().getCommands().performPrefixedCommand(sender.createCommandSourceStack(), "jev setspawn JevMsgTest");
        helper.assertValueEqual(entity.respawnPosition(), sender.blockPosition(), "authorized owner could not set companion spawn");
        helper.getLevel().getServer().getCommands().performPrefixedCommand(sender.createCommandSourceStack(), "msg JevMsgTest follow me");
        helper.assertValueEqual(entity.currentGoal(), "follow me", "native /msg did not route to the named Jev");
        helper.assertTrue(entity.recentChat().get(entity.recentChat().size()-1).privateMessage(), "private message provenance missing");
        helper.getLevel().getServer().getCommands().performPrefixedCommand(sender.createCommandSourceStack(), "jev admin JevMsgTest remove test-mock-player");
        helper.assertTrue(!entity.administrators().contains(sender.getUUID()), "owner could not remove a UUID administrator");
        entity.stopNow(); entity.setOwner(UUID.randomUUID());
        helper.getLevel().getServer().getCommands().performPrefixedCommand(sender.createCommandSourceStack(), "msg JevMsgTest mine logs");
        helper.assertTrue(entity.currentGoal().isEmpty(), "unauthorized native /msg became an executable goal");
        int observations = entity.recentChat().size();
        helper.getLevel().getServer().getCommands().performPrefixedCommand(sender.createCommandSourceStack(), "msg test-mock-player ordinary player message");
        helper.assertValueEqual(entity.recentChat().size(), observations, "ordinary player /msg was intercepted as a Jev message");
        helper.succeed();
    }
}
