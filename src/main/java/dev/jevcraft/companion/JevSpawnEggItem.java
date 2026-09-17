package dev.jevcraft.companion;

import dev.jevcraft.JevCraft;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

public final class JevSpawnEggItem extends Item {
    public JevSpawnEggItem(Properties properties) { super(properties); }
    @Override public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel() instanceof ServerLevel level) || context.getPlayer() == null) return InteractionResult.SUCCESS;
        long count = 0;
        for (ServerLevel candidate : level.getServer().getAllLevels()) for (var entity : candidate.getAllEntities())
            if (entity instanceof JevCompanion && entity.isAlive()) count++;
        if (count >= 10) { context.getPlayer().displayClientMessage(Component.literal("The world already has ten living Jevs."), false); return InteractionResult.FAIL; }
        JevCompanion entity = JevCraft.JEV.get().create(level, null, context.getClickedPos().relative(context.getClickedFace()), MobSpawnType.SPAWN_EGG, true, false);
        if (entity == null) return InteractionResult.FAIL;
        entity.setOwner(context.getPlayer().getUUID());
        entity.setCustomName(Component.literal("Jev_" + entity.getStringUUID().substring(0, 6)));
        entity.setCustomNameVisible(true);
        if (!context.getPlayer().getAbilities().instabuild) context.getItemInHand().shrink(1);
        return InteractionResult.CONSUME;
    }
}
