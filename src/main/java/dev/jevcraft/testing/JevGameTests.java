package dev.jevcraft.testing;

import dev.jevcraft.JevCraft;
import dev.jevcraft.companion.JevCompanion;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

@GameTestHolder("jevcraft")
@PrefixGameTestTemplate(false)
public final class JevGameTests {
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void companionBodyInventoryAndGoalPersist(GameTestHelper helper) {
        UUID owner = UUID.randomUUID();
        JevCompanion original = helper.spawn(JevCraft.JEV.get(), new BlockPos(2, 1, 2));
        original.setOwner(owner); original.acceptGoal("follow me"); original.inventory().setItem(0, new ItemStack(Items.OAK_LOG, 4));
        CompoundTag saved = new CompoundTag(); original.addAdditionalSaveData(saved);
        JevCompanion restored = JevCraft.JEV.get().create(helper.getLevel());
        helper.assertTrue(restored != null, "registered companion type did not create");
        restored.readAdditionalSaveData(saved);
        helper.assertValueEqual(restored.owner(), owner, "owner UUID persistence");
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
}
