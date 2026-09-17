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
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
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
    public static void companionMountsAndDismountsVanillaVehicle(GameTestHelper helper) {
        JevCompanion entity=helper.spawn(JevCraft.JEV.get(),new BlockPos(2,1,2));
        var minecart=helper.spawn(EntityType.MINECART,new BlockPos(3,1,2));
        helper.assertTrue(entity.actions().mount(minecart),"companion could not mount reachable vanilla minecart");
        helper.assertTrue(entity.getVehicle()==minecart&&minecart.hasPassenger(entity),"vanilla riding relationship was not established");
        helper.assertTrue(entity.actions().dismount(),"companion could not dismount vanilla minecart");
        helper.assertTrue(entity.getVehicle()==null&&!minecart.hasPassenger(entity),"vanilla riding relationship survived dismount");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void twoCompanionInteractionInventoriesStayIsolated(GameTestHelper helper) {
        BlockPos supportA=new BlockPos(3,0,2),supportB=new BlockPos(3,0,5); helper.setBlock(supportA,Blocks.STONE); helper.setBlock(supportB,Blocks.STONE);
        JevCompanion first=helper.spawn(JevCraft.JEV.get(),new BlockPos(2,1,2)),second=helper.spawn(JevCraft.JEV.get(),new BlockPos(2,1,5));
        first.inventory().setItem(0,new ItemStack(Items.COBBLESTONE,2)); second.inventory().setItem(0,new ItemStack(Items.DIRT,3));
        BlockPos absoluteA=helper.absolutePos(supportA),absoluteB=helper.absolutePos(supportB);
        helper.assertTrue(first.actions().place(helper.getLevel(),new BlockHitResult(Vec3.atCenterOf(absoluteA),Direction.UP,absoluteA,false),0).consumesAction(),"first placement failed");
        helper.assertTrue(second.actions().place(helper.getLevel(),new BlockHitResult(Vec3.atCenterOf(absoluteB),Direction.UP,absoluteB,false),0).consumesAction(),"second placement failed");
        helper.assertTrue(helper.getLevel().getBlockState(absoluteA.above()).is(Blocks.COBBLESTONE)&&helper.getLevel().getBlockState(absoluteB.above()).is(Blocks.DIRT),"interaction contexts crossed block choices");
        helper.assertValueEqual(first.inventory().getItem(0).getCount(),1,"first inventory consumption was not isolated");
        helper.assertValueEqual(second.inventory().getItem(0).getCount(),2,"second inventory consumption was not isolated");
        helper.assertTrue(first.inventory().getItem(0).is(Items.COBBLESTONE)&&second.inventory().getItem(0).is(Items.DIRT),"interaction contexts crossed inventory identities");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void companionPlacesAndCollectsWaterWithBucketCallbacks(GameTestHelper helper) {
        BlockPos support=new BlockPos(3,0,2),water=support.above(); helper.setBlock(support,Blocks.STONE);
        JevCompanion entity=helper.spawn(JevCraft.JEV.get(),new BlockPos(2,1,2)); entity.inventory().setItem(0,new ItemStack(Items.WATER_BUCKET));
        BlockPos absoluteSupport=helper.absolutePos(support),absoluteWater=helper.absolutePos(water);
        helper.assertTrue(entity.actions().useItem(helper.getLevel(),new Vec3(absoluteSupport.getX()+.5,absoluteSupport.getY()+1,absoluteSupport.getZ()+.5),0).consumesAction(),"water bucket placement callback failed");
        helper.assertTrue(helper.getLevel().getFluidState(absoluteWater).is(net.minecraft.tags.FluidTags.WATER),"water bucket did not place authoritative fluid");
        helper.assertTrue(entity.inventory().getItem(0).is(Items.BUCKET),"water placement did not return an empty bucket");
        helper.assertTrue(entity.actions().useItem(helper.getLevel(),Vec3.atCenterOf(absoluteWater),0).consumesAction(),"empty bucket pickup callback failed");
        helper.assertTrue(helper.getLevel().getFluidState(absoluteWater).isEmpty(),"bucket pickup did not remove source fluid");
        helper.assertTrue(entity.inventory().getItem(0).is(Items.WATER_BUCKET),"bucket pickup did not return a water bucket");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void companionHoesAndPlantsThroughItemCallbacks(GameTestHelper helper) {
        BlockPos soil=new BlockPos(3,0,2); helper.setBlock(soil,Blocks.DIRT);
        JevCompanion entity=helper.spawn(JevCraft.JEV.get(),new BlockPos(2,1,2)); entity.inventory().setItem(0,new ItemStack(Items.IRON_HOE)); entity.inventory().setItem(1,new ItemStack(Items.WHEAT_SEEDS,2));
        BlockPos absolute=helper.absolutePos(soil); BlockHitResult hit=new BlockHitResult(Vec3.atCenterOf(absolute),Direction.UP,absolute,false);
        helper.assertTrue(entity.actions().useBlock(helper.getLevel(),hit,0).consumesAction(),"hoe use callback failed");
        helper.assertTrue(helper.getLevel().getBlockState(absolute).is(Blocks.FARMLAND),"hoe did not create authoritative farmland");
        helper.assertTrue(entity.inventory().getItem(0).getDamageValue()>0,"hoe callback did not apply durability");
        helper.assertTrue(entity.actions().useBlock(helper.getLevel(),hit,1).consumesAction(),"seed use callback failed");
        helper.assertTrue(helper.getLevel().getBlockState(absolute.above()).is(Blocks.WHEAT),"seed callback did not plant a crop");
        helper.assertValueEqual(entity.inventory().getItem(1).getCount(),1,"planting did not consume exactly one seed");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void companionTogglesLeverThroughBlockCallback(GameTestHelper helper) {
        BlockPos support=new BlockPos(3,0,2),lever=support.above(); helper.setBlock(support,Blocks.STONE);
        helper.setBlock(lever,Blocks.LEVER.defaultBlockState().setValue(BlockStateProperties.ATTACH_FACE,AttachFace.FLOOR).setValue(BlockStateProperties.HORIZONTAL_FACING,Direction.NORTH));
        JevCompanion entity=helper.spawn(JevCraft.JEV.get(),new BlockPos(2,1,2));
        BlockPos absolute=helper.absolutePos(lever);
        helper.assertTrue(!helper.getLevel().getBlockState(absolute).getValue(BlockStateProperties.POWERED),"lever started powered");
        helper.assertTrue(entity.actions().useBlock(helper.getLevel(),new BlockHitResult(Vec3.atCenterOf(absolute),Direction.UP,absolute,false),0).consumesAction(),"lever interaction callback failed");
        helper.assertTrue(helper.getLevel().getBlockState(absolute).getValue(BlockStateProperties.POWERED),"lever did not toggle authoritative powered state");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 160)
    public static void companionRangedAttackUsesOwnedVanillaArrow(GameTestHelper helper) {
        JevCompanion entity=helper.spawn(JevCraft.JEV.get(),new BlockPos(2,1,2)); entity.setOwner(UUID.randomUUID()); entity.acceptGoal("defend");
        var target=helper.spawn(EntityType.COW,new BlockPos(7,1,2)); target.setNoAi(true); float health=target.getHealth();
        entity.inventory().setItem(0,new ItemStack(Items.BOW));entity.inventory().setItem(1,new ItemStack(Items.ARROW,3));
        entity.actions().beginRangedAttack(target,0,1,entity.goalVersion());
        helper.succeedWhen(()->{
            helper.assertTrue(target.getHealth()<health,"owned arrow did not damage target");
            helper.assertValueEqual(entity.inventory().getItem(1).getCount(),2,"ranged attack did not consume exactly one arrow");
            helper.assertValueEqual(entity.inventory().getItem(0).getDamageValue(),1,"ranged attack did not damage bow exactly once");
            helper.assertTrue(entity.getLastHurtMob()==target,"projectile combat was not attributed to companion");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void stoppingMidBowDrawConsumesNothing(GameTestHelper helper) {
        JevCompanion entity=helper.spawn(JevCraft.JEV.get(),new BlockPos(2,1,2));entity.setOwner(UUID.randomUUID());entity.acceptGoal("defend");
        var target=helper.spawn(EntityType.COW,new BlockPos(7,1,2));target.setNoAi(true);float health=target.getHealth();
        entity.inventory().setItem(0,new ItemStack(Items.BOW));entity.inventory().setItem(1,new ItemStack(Items.ARROW,3));entity.actions().beginRangedAttack(target,0,1,entity.goalVersion());
        helper.runAfterDelay(10,entity::stopNow);
        helper.runAfterDelay(35,()->{
            helper.assertTrue(target.getHealth()==health,"cancelled bow draw still damaged target");
            helper.assertValueEqual(entity.inventory().getItem(1).getCount(),3,"cancelled bow draw consumed ammunition");
            helper.assertValueEqual(entity.inventory().getItem(0).getDamageValue(),0,"cancelled bow draw damaged bow");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void companionCraftsShapedToolThroughRealMenu(GameTestHelper helper) {
        BlockPos table=new BlockPos(3,1,2);helper.setBlock(table,Blocks.CRAFTING_TABLE);
        JevCompanion entity=helper.spawn(JevCraft.JEV.get(),new BlockPos(2,1,2));entity.inventory().setItem(0,new ItemStack(Items.OAK_PLANKS,3));entity.inventory().setItem(1,new ItemStack(Items.STICK,2));
        BlockPos absolute=helper.absolutePos(table);int[] pickaxe={0,0,0,-1,1,-1,-1,1,-1};
        helper.assertTrue(entity.actions().craftPattern(helper.getLevel(),new BlockHitResult(Vec3.atCenterOf(absolute),Direction.UP,absolute,false),pickaxe),"3x3 crafting menu did not craft shaped tool");
        helper.assertTrue(entity.inventory().getItem(0).isEmpty()&&entity.inventory().getItem(1).isEmpty(),"shaped recipe did not consume exact ingredients");
        int pickaxes=0;for(int i=0;i<entity.inventory().getContainerSize();i++)if(entity.inventory().getItem(i).is(Items.WOODEN_PICKAXE))pickaxes+=entity.inventory().getItem(i).getCount();
        helper.assertValueEqual(pickaxes,1,"shaped recipe did not return exactly one wooden pickaxe");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 320)
    public static void companionLoadsSmeltsAndCollectsThroughFurnaceMenu(GameTestHelper helper) {
        BlockPos furnacePos=new BlockPos(3,1,2);helper.setBlock(furnacePos,Blocks.FURNACE);JevCompanion entity=helper.spawn(JevCraft.JEV.get(),new BlockPos(2,1,2));
        entity.inventory().setItem(5,new ItemStack(Items.RAW_IRON));entity.inventory().setItem(6,new ItemStack(Items.COAL));BlockPos absolute=helper.absolutePos(furnacePos);BlockHitResult hit=new BlockHitResult(Vec3.atCenterOf(absolute),Direction.UP,absolute,false);
        helper.assertTrue(entity.actions().loadFurnace(helper.getLevel(),hit,5,6),"furnace menu did not accept input and fuel");
        helper.assertTrue(entity.inventory().getItem(5).isEmpty()&&entity.inventory().getItem(6).isEmpty(),"furnace loading did not move authoritative stacks");
        helper.succeedWhen(()->{
            helper.assertTrue(helper.getLevel().getBlockEntity(absolute) instanceof AbstractFurnaceBlockEntity,"furnace block entity disappeared");
            AbstractFurnaceBlockEntity furnace=(AbstractFurnaceBlockEntity)helper.getLevel().getBlockEntity(absolute);
            helper.assertTrue(furnace.getItem(2).is(Items.IRON_INGOT),"normal furnace ticking has not produced iron");
            helper.assertTrue(entity.actions().collectFurnace(helper.getLevel(),hit),"furnace result menu callback failed");
            int ingots=0;for(int i=0;i<entity.inventory().getContainerSize();i++)if(entity.inventory().getItem(i).is(Items.IRON_INGOT))ingots+=entity.inventory().getItem(i).getCount();
            helper.assertValueEqual(ingots,1,"furnace collection did not return exactly one iron ingot");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 240)
    public static void companionUsesSmokerAndBlastFurnaceMenus(GameTestHelper helper) {
        BlockPos smokerPos=new BlockPos(3,1,2),blastPos=new BlockPos(2,1,3);helper.setBlock(smokerPos,Blocks.SMOKER);helper.setBlock(blastPos,Blocks.BLAST_FURNACE);
        JevCompanion entity=helper.spawn(JevCraft.JEV.get(),new BlockPos(2,1,2));entity.inventory().setItem(9,new ItemStack(Items.BEEF));entity.inventory().setItem(10,new ItemStack(Items.COAL));entity.inventory().setItem(11,new ItemStack(Items.RAW_IRON));entity.inventory().setItem(12,new ItemStack(Items.COAL));
        BlockPos smokerAbsolute=helper.absolutePos(smokerPos),blastAbsolute=helper.absolutePos(blastPos);BlockHitResult smokerHit=new BlockHitResult(Vec3.atCenterOf(smokerAbsolute),Direction.UP,smokerAbsolute,false),blastHit=new BlockHitResult(Vec3.atCenterOf(blastAbsolute),Direction.UP,blastAbsolute,false);
        helper.assertTrue(entity.actions().loadFurnace(helper.getLevel(),smokerHit,9,10),"smoker-specific menu did not accept food and fuel");helper.assertTrue(entity.actions().loadFurnace(helper.getLevel(),blastHit,11,12),"blast-furnace-specific menu did not accept ore and fuel");
        helper.succeedWhen(()->{
            helper.assertTrue(helper.getLevel().getBlockEntity(smokerAbsolute) instanceof AbstractFurnaceBlockEntity&&helper.getLevel().getBlockEntity(blastAbsolute) instanceof AbstractFurnaceBlockEntity,"specialized furnace block entity disappeared");
            AbstractFurnaceBlockEntity smoker=(AbstractFurnaceBlockEntity)helper.getLevel().getBlockEntity(smokerAbsolute),blast=(AbstractFurnaceBlockEntity)helper.getLevel().getBlockEntity(blastAbsolute);
            helper.assertTrue(smoker.getItem(2).is(Items.COOKED_BEEF)&&blast.getItem(2).is(Items.IRON_INGOT),"specialized normal ticking has not produced both outputs");
            helper.assertTrue(entity.actions().collectFurnace(helper.getLevel(),smokerHit)&&entity.actions().collectFurnace(helper.getLevel(),blastHit),"specialized result collection failed");
            int beef=0,iron=0;for(int i=0;i<entity.inventory().getContainerSize();i++){if(entity.inventory().getItem(i).is(Items.COOKED_BEEF))beef+=entity.inventory().getItem(i).getCount();if(entity.inventory().getItem(i).is(Items.IRON_INGOT))iron+=entity.inventory().getItem(i).getCount();}
            helper.assertValueEqual(beef,1,"smoker output count");helper.assertValueEqual(iron,1,"blast furnace output count");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void companionFeedsAnimalsThroughNormalEntityCallbacks(GameTestHelper helper) {
        JevCompanion entity=helper.spawn(JevCraft.JEV.get(),new BlockPos(2,1,2));Cow first=helper.spawn(EntityType.COW,new BlockPos(3,1,2));Cow second=helper.spawn(EntityType.COW,new BlockPos(3,1,3));
        first.setAge(0);second.setAge(0);entity.inventory().setItem(7,new ItemStack(Items.WHEAT,2));
        helper.assertTrue(entity.actions().interactEntity(helper.getLevel(),first,7).consumesAction(),"first animal rejected normal player interaction");
        helper.assertTrue(entity.actions().interactEntity(helper.getLevel(),second,7).consumesAction(),"second animal rejected normal player interaction");
        helper.assertTrue(first.isInLove()&&second.isInLove(),"wheat interactions did not put both adult cows in love");
        helper.assertTrue(entity.inventory().getItem(7).isEmpty(),"feeding did not consume exactly two wheat");helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void companionOwnsLeadAfterNormalEntityCallback(GameTestHelper helper) {
        JevCompanion entity=helper.spawn(JevCraft.JEV.get(),new BlockPos(2,1,2));Cow cow=helper.spawn(EntityType.COW,new BlockPos(3,1,2));entity.inventory().setItem(8,new ItemStack(Items.LEAD));
        helper.assertTrue(entity.actions().interactEntity(helper.getLevel(),cow,8).consumesAction(),"lead interaction callback failed");
        helper.assertTrue(cow.getLeashHolder()==entity,"lead remained attached to the temporary player context");
        helper.assertTrue(entity.inventory().getItem(8).isEmpty(),"lead interaction did not consume the lead");helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void companionSleepsWakesAndSetsBedSpawn(GameTestHelper helper) {
        BlockPos foot=new BlockPos(3,1,2),head=foot.east();helper.setBlock(foot,Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING,Direction.EAST).setValue(BedBlock.PART,BedPart.FOOT));helper.setBlock(head,Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING,Direction.EAST).setValue(BedBlock.PART,BedPart.HEAD));
        JevCompanion entity=helper.spawn(JevCraft.JEV.get(),new BlockPos(2,1,2));BlockPos absoluteFoot=helper.absolutePos(foot),absoluteHead=helper.absolutePos(head);BlockHitResult hit=new BlockHitResult(Vec3.atCenterOf(absoluteFoot),Direction.UP,absoluteFoot,false);helper.getLevel().setDayTime(13000);
        helper.runAfterDelay(2,()->{
            helper.assertTrue(entity.actions().sleepInBed(helper.getLevel(),hit),"normal nighttime bed callback did not admit companion");helper.assertTrue(entity.isSleeping(),"companion body did not become sleeper of record");helper.assertValueEqual(entity.getSleepingPos().orElse(null),absoluteHead,"sleeping position did not resolve to bed head");helper.assertValueEqual(entity.respawnPosition(),absoluteHead,"bed callback did not set persistent companion spawn");
            helper.assertTrue(entity.actions().wakeUp(),"companion did not wake");helper.assertTrue(!entity.isSleeping(),"wake left companion sleeping");helper.assertTrue(!helper.getBlockState(head).getValue(BedBlock.OCCUPIED),"wake left bed occupied");helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "night_skip", timeoutTicks = 180)
    public static void companionParticipatesInVanillaNightSkip(GameTestHelper helper) {
        BlockPos foot=new BlockPos(3,1,2),head=foot.east();helper.setBlock(foot,Blocks.BLUE_BED.defaultBlockState().setValue(BedBlock.FACING,Direction.EAST).setValue(BedBlock.PART,BedPart.FOOT));helper.setBlock(head,Blocks.BLUE_BED.defaultBlockState().setValue(BedBlock.FACING,Direction.EAST).setValue(BedBlock.PART,BedPart.HEAD));
        JevCompanion entity=helper.spawn(JevCraft.JEV.get(),new BlockPos(2,1,2));BlockPos absolute=helper.absolutePos(foot);BlockHitResult hit=new BlockHitResult(Vec3.atCenterOf(absolute),Direction.UP,absolute,false);
        helper.getLevel().getGameRules().getRule(GameRules.RULE_PLAYERS_SLEEPING_PERCENTAGE).set(50,helper.getLevel().getServer());helper.getLevel().setDayTime(13000);
        helper.runAfterDelay(2,()->helper.assertTrue(entity.actions().sleepInBed(helper.getLevel(),hit),"companion could not begin night-skip sleep"));
        helper.runAfterDelay(115,()->{
            long time=helper.getLevel().getDayTime()%24000;helper.getLevel().getGameRules().getRule(GameRules.RULE_PLAYERS_SLEEPING_PERCENTAGE).set(100,helper.getLevel().getServer());
            helper.assertTrue(time<1000,"deep-sleep threshold did not advance to the next day");helper.assertTrue(!entity.isSleeping(),"night skip did not wake companion through bed lifecycle");helper.assertTrue(!helper.getBlockState(head).getValue(BedBlock.OCCUPIED),"night skip left companion bed occupied");helper.succeed();
        });
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
