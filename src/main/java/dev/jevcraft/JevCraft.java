package dev.jevcraft;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.logging.LogUtils;
import dev.jevcraft.companion.JevCompanion;
import dev.jevcraft.companion.JevSpawnEggItem;
import dev.jevcraft.companion.MovingChunkTickets;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.*;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.registries.*;
import net.minecraft.core.registries.Registries;
import org.slf4j.Logger;

import java.util.Locale;

@Mod(JevCraft.MOD_ID)
public final class JevCraft {
    public static final String MOD_ID = "jevcraft";
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);
    public static final DeferredHolder<EntityType<?>, EntityType<JevCompanion>> JEV = ENTITIES.register("jev_companion", () ->
            EntityType.Builder.<JevCompanion>of(JevCompanion::new, MobCategory.CREATURE).sized(.6f, 1.8f)
                    .clientTrackingRange(10).updateInterval(2).build("jevcraft:jev_companion"));
    public static final DeferredItem<JevSpawnEggItem> JEV_EGG = ITEMS.registerItem("jev_spawn_egg", JevSpawnEggItem::new,
            new Item.Properties().stacksTo(1).rarity(Rarity.RARE));

    public JevCraft(IEventBus modBus) {
        ENTITIES.register(modBus); ITEMS.register(modBus);
        modBus.addListener(this::attributes);
        modBus.addListener(this::creativeTab);
        modBus.addListener(MovingChunkTickets::register);
        NeoForge.EVENT_BUS.addListener(this::commands);
        NeoForge.EVENT_BUS.addListener(this::login);
        LOGGER.info("JevCraft initialized; credentials are read only from host configuration");
    }

    private void attributes(EntityAttributeCreationEvent event) {
        event.put(JEV.get(), Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 20).add(Attributes.MOVEMENT_SPEED, .25)
                .add(Attributes.ATTACK_DAMAGE, 2).add(Attributes.FOLLOW_RANGE, 32).build());
    }
    private void creativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) event.accept(JEV_EGG.get());
    }
    private void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.isCreative()) return;
        var data = player.getPersistentData();
        if (data.getBoolean("jevcraft:starter_egg_delivered")) return;
        ItemStack egg = new ItemStack(JEV_EGG.get());
        if (player.getInventory().add(egg)) {
            data.putBoolean("jevcraft:starter_egg_delivered", true);
            player.displayClientMessage(Component.literal("You received your one-time Jev Spawn Egg."), false);
        }
    }
    private void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("jev")
                .then(Commands.literal("stop").then(Commands.argument("name", StringArgumentType.word()).executes(context -> {
                    var companion = find(context.getSource().getServer(), StringArgumentType.getString(context, "name"));
                    if (companion == null) return 0;
                    ServerPlayer sender = context.getSource().getPlayerOrException();
                    if (!companion.canCommand(sender)) { context.getSource().sendFailure(Component.literal("You are not authorized for that Jev.")); return 0; }
                    companion.stopNow(); context.getSource().sendSuccess(() -> Component.literal("Stopped " + companion.getName().getString()), false); return 1;
                })))
                .then(Commands.literal("goal").then(Commands.argument("name", StringArgumentType.word())
                        .then(Commands.argument("instruction", StringArgumentType.greedyString()).executes(context -> {
                            var companion = find(context.getSource().getServer(), StringArgumentType.getString(context, "name"));
                            if (companion == null) return 0;
                            ServerPlayer sender = context.getSource().getPlayerOrException();
                            if (!companion.canCommand(sender)) { context.getSource().sendFailure(Component.literal("You are not authorized for that Jev.")); return 0; }
                            companion.acceptGoal(StringArgumentType.getString(context, "instruction"));
                            context.getSource().sendSuccess(() -> Component.literal(companion.getName().getString() + ": goal accepted"), false); return 1;
                        })))));
    }
    private static JevCompanion find(net.minecraft.server.MinecraftServer server, String name) {
        String target = name.toLowerCase(Locale.ROOT);
        for (var level : server.getAllLevels()) for (var entity : level.getAllEntities())
            if (entity instanceof JevCompanion jev && jev.getName().getString().toLowerCase(Locale.ROOT).equals(target)) return jev;
        return null;
    }
}
