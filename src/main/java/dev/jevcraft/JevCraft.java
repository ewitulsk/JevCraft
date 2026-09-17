package dev.jevcraft;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.logging.LogUtils;
import dev.jevcraft.client.TakeoverRuntime;
import dev.jevcraft.companion.JevCompanion;
import dev.jevcraft.companion.JevSpawnEggItem;
import dev.jevcraft.companion.MovingChunkTickets;
import dev.jevcraft.companion.JevWorldData;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.core.BlockPos;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.world.chunk.ForcedChunkManager;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.CommandEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.registries.*;
import net.minecraft.core.registries.Registries;
import org.slf4j.Logger;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;

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
    private boolean hiddenServerPassed;
    private int persistenceShutdownTicks = -1;
    private int persistenceReloadTicks = -1;
    private int remoteTickTestTicks = -1;
    private static final UUID REMOTE_TEST_ACTOR = UUID.fromString("089f86c5-1cfa-48bb-a28a-da209b531ff5");
    private BlockPos remoteTestFurnace;

    public JevCraft(IEventBus modBus) {
        ENTITIES.register(modBus); ITEMS.register(modBus);
        modBus.addListener(this::attributes);
        modBus.addListener(this::creativeTab);
        modBus.addListener(MovingChunkTickets::register);
        NeoForge.EVENT_BUS.addListener(this::commands);
        NeoForge.EVENT_BUS.addListener(this::command);
        NeoForge.EVENT_BUS.addListener(this::login);
        NeoForge.EVENT_BUS.addListener(this::entityJoined);
        NeoForge.EVENT_BUS.addListener(this::playerTick);
        NeoForge.EVENT_BUS.addListener(this::chat);
        NeoForge.EVENT_BUS.addListener(this::serverStarted);
        NeoForge.EVENT_BUS.addListener(this::serverTick);
        if (FMLEnvironment.dist == Dist.CLIENT) TakeoverRuntime.initialize(modBus);
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
        if (event.getEntity() instanceof ServerPlayer player) {
            if (Boolean.getBoolean("jevcraft.hiddenClientTest")) {
                var level=player.serverLevel();
                for(int x=-3;x<=4;x++) for(int z=-3;z<=3;z++) level.setBlockAndUpdate(new BlockPos(x,1,z),Blocks.STONE.defaultBlockState());
                level.setBlockAndUpdate(new BlockPos(3,2,0),Blocks.OAK_LOG.defaultBlockState());
                player.teleportTo(level,.5,2,.5,Set.of(),0,0); player.getInventory().add(new ItemStack(Items.WOODEN_AXE));
                LOGGER.info("HIDDEN_TAKEOVER_SERVER_READY");
            } else deliverStarterEgg(player);
        }
    }
    private void entityJoined(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof JevCompanion jev && event.getLevel() instanceof net.minecraft.server.level.ServerLevel level)
            jev.activateChunkTickets(level);
    }
    private void playerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (Boolean.getBoolean("jevcraft.hiddenClientTest")) {
                if (!hiddenServerPassed && player.serverLevel().getBlockState(new BlockPos(3,2,0)).isAir()) { hiddenServerPassed=true; LOGGER.info("HIDDEN_TAKEOVER_SERVER_PASS"); }
            } else if (player.tickCount % 20 == 0) deliverStarterEgg(player);
        }
    }
    private void deliverStarterEgg(ServerPlayer player) {
        if (player.isCreative()) return;
        JevWorldData data = JevWorldData.get(player.getServer());
        if (data.grant(player.getUUID()) != JevWorldData.GrantState.PENDING) return;
        ItemStack egg = new ItemStack(JEV_EGG.get());
        if (player.getInventory().add(egg)) {
            data.grantDelivered(player.getUUID());
            player.displayClientMessage(Component.literal("You received your one-time Jev Spawn Egg."), false);
        }
    }
    private void serverStarted(ServerStartedEvent event) {
        String phase = System.getProperty("jevcraft.persistenceTest", ""); if (phase.isBlank()) return;
        var server = event.getServer(); var level = server.overworld();
        UUID actor = UUID.fromString("79eaf327-3320-4b72-9b45-e2666f089c42");
        UUID owner = UUID.fromString("aa628850-ec31-4cc0-8d88-b9a3a13cd5d9");
        UUID admin = UUID.fromString("11548867-8593-497c-99a3-48bce967ce00");
        UUID grantPlayer = UUID.fromString("1cf589fd-ecda-4a65-8ab1-497b184387fe");
        UUID respawnActor = UUID.fromString("d8199b62-5f13-4683-8bd4-c6318ec971eb");
        if (phase.equals("create")) {
            JevCompanion jev = JEV.get().create(level); if (jev == null) throw new IllegalStateException("persistence fixture entity creation failed");
            jev.setUUID(actor); jev.setCustomName(Component.literal("PersistJev")); jev.setOwner(owner); jev.addAdministrator(admin);
            jev.acceptGoal("follow me"); jev.inventory().setItem(0, new ItemStack(Items.OAK_LOG, 7)); jev.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
            jev.setExperience(7, 91, .4f); jev.setOperatorTeleportAllowed(true); jev.observeChat(owner, "remember this", true, true); jev.setHealth(13);
            BlockPos spawn = level.getSharedSpawnPos(); jev.moveTo(spawn.getX()+.5, spawn.getY()+1, spawn.getZ()+.5, 0, 0);
            jev.setRespawnPoint(level.dimension(), spawn.above(), 45);
            if (!level.addFreshEntity(jev)) throw new IllegalStateException("persistence fixture entity add failed");
            JevWorldData data=JevWorldData.get(server); if(!data.register(actor,"PersistJev"))throw new IllegalStateException("persistence fixture roster add failed"); data.grant(grantPlayer); data.grantDelivered(grantPlayer);
            JevCompanion queued = JEV.get().create(level); if (queued == null) throw new IllegalStateException("respawn persistence fixture creation failed");
            queued.setUUID(respawnActor); queued.setOwner(owner); queued.addAdministrator(admin); queued.acceptGoal("resume after restart"); queued.observeChat(owner, "durable respawn memory", true, true);
            CompoundTag queuedState = new CompoundTag(); queued.addAdditionalSaveData(queuedState); queuedState.putLong("RespawnFallbackPos", spawn.asLong());
            if (!data.register(respawnActor, "RestartJev")) throw new IllegalStateException("respawn persistence roster add failed");
            data.queueRespawn(respawnActor, queuedState, level.getGameTime() + 60);
            LOGGER.info("PERSISTENCE_CREATE_READY");
            persistenceShutdownTicks = 40;
        } else if (phase.equals("reload")) {
            persistenceReloadTicks = 40;
        } else if (phase.equals("remote")) {
            BlockPos spawn = level.getSharedSpawnPos();
            BlockPos body = new BlockPos(spawn.getX() + 40 * 16, spawn.getY() + 1, spawn.getZ());
            level.getChunkAt(body);
            JevCompanion jev = JEV.get().create(level); if (jev == null) throw new IllegalStateException("remote fixture entity creation failed");
            jev.setUUID(REMOTE_TEST_ACTOR); jev.setOwner(owner); jev.setCustomName(Component.literal("RemoteJev"));
            jev.moveTo(body.getX() + .5, body.getY(), body.getZ() + .5, 0, 0);
            if (!level.addFreshEntity(jev)) throw new IllegalStateException("remote fixture entity add failed");
            if (!JevWorldData.get(server).register(REMOTE_TEST_ACTOR, "RemoteJev")) throw new IllegalStateException("remote fixture roster add failed");
            remoteTestFurnace = body.offset(2, 0, 0);
            level.setBlockAndUpdate(remoteTestFurnace, Blocks.FURNACE.defaultBlockState());
            if (!(level.getBlockEntity(remoteTestFurnace) instanceof AbstractFurnaceBlockEntity furnace)) throw new IllegalStateException("remote furnace missing");
            furnace.setItem(0, new ItemStack(Items.IRON_ORE)); furnace.setItem(1, new ItemStack(Items.COAL));
            remoteTickTestTicks = 260;
            LOGGER.info("REMOTE_TICK_CREATE_READY");
        }
    }
    private void serverTick(ServerTickEvent.Post event) {
        JevWorldData.get(event.getServer()).tickRespawns(event.getServer());
        if (persistenceReloadTicks >= 0 && --persistenceReloadTicks == 0) {
            persistenceReloadTicks = -1;
            verifyPersistenceReload(event.getServer());
            persistenceShutdownTicks = 40;
        }
        if (remoteTickTestTicks >= 0 && --remoteTickTestTicks == 0) {
            remoteTickTestTicks = -1;
            verifyRemoteTicking(event.getServer());
            persistenceShutdownTicks = 40;
        }
        if (persistenceShutdownTicks < 0 || --persistenceShutdownTicks > 0) return;
        persistenceShutdownTicks = -1;
        LOGGER.info("PERSISTENCE_TEST_STOPPING");
        event.getServer().halt(false);
    }
    private void verifyRemoteTicking(net.minecraft.server.MinecraftServer server) {
        if (!server.getPlayerList().getPlayers().isEmpty()) throw new IllegalStateException("remote test unexpectedly had a player");
        JevCompanion jev = null;
        for (var entity : server.overworld().getAllEntities()) if (entity instanceof JevCompanion candidate && candidate.getUUID().equals(REMOTE_TEST_ACTOR)) { jev = candidate; break; }
        if (jev == null || jev.tickCount < 200) throw new IllegalStateException("remote Jev did not keep ticking without players");
        if (!(server.overworld().getBlockEntity(remoteTestFurnace) instanceof AbstractFurnaceBlockEntity furnace)
                || !furnace.getItem(2).is(Items.IRON_INGOT)) throw new IllegalStateException("remote furnace did not complete while no players were present");
        if (!ForcedChunkManager.hasForcedChunks(server.overworld())) throw new IllegalStateException("remote ticking ticket was not retained");
        LOGGER.info("REMOTE_TICK_PASS entityTicks={} furnaceOutput={}", jev.tickCount, furnace.getItem(2).getCount());
    }
    private void verifyPersistenceReload(net.minecraft.server.MinecraftServer server) {
        UUID actor = UUID.fromString("79eaf327-3320-4b72-9b45-e2666f089c42");
        UUID owner = UUID.fromString("aa628850-ec31-4cc0-8d88-b9a3a13cd5d9");
        UUID admin = UUID.fromString("11548867-8593-497c-99a3-48bce967ce00");
        UUID grantPlayer = UUID.fromString("1cf589fd-ecda-4a65-8ab1-497b184387fe");
        UUID respawnActor = UUID.fromString("d8199b62-5f13-4683-8bd4-c6318ec971eb");
        JevCompanion jev = null;
        JevCompanion respawned = null;
        for (var entity : server.overworld().getAllEntities()) if (entity instanceof JevCompanion candidate && candidate.getUUID().equals(actor)) { jev = candidate; break; }
        for (var entity : server.overworld().getAllEntities()) if (entity instanceof JevCompanion candidate && candidate.getUUID().equals(respawnActor)) { respawned = candidate; break; }
        if (jev == null) throw new IllegalStateException("persisted Jev was not restored in loaded spawn chunks");
        if (respawned == null || !owner.equals(respawned.owner()) || !respawned.administrators().contains(admin)
                || !"resume after restart".equals(respawned.currentGoal()) || respawned.recentChat().size() != 1)
            throw new IllegalStateException("queued Jev did not respawn with durable identity after restart");
        JevWorldData data = JevWorldData.get(server);
        if (!owner.equals(jev.owner()) || !jev.administrators().contains(admin) || !"follow me".equals(jev.currentGoal())
                || jev.inventory().getItem(0).getCount() != 7 || jev.recentChat().size() != 1 || Math.abs(jev.getHealth() - 13) > 0.01
                || !jev.getItemBySlot(EquipmentSlot.HEAD).is(Items.IRON_HELMET) || jev.experienceLevel() != 7 || jev.totalExperience() != 91
                || Math.abs(jev.experienceProgress() - .4f) > .001 || !jev.operatorTeleportAllowed() || !server.overworld().dimension().equals(jev.respawnDimension())
                || !"PersistJev".equals(data.name(actor)) || data.grant(grantPlayer) != JevWorldData.GrantState.DELIVERED
                || !ForcedChunkManager.hasForcedChunks(server.overworld()) || data.pendingRespawnCount() != 0
                || !"RestartJev".equals(data.name(respawnActor)))
            throw new IllegalStateException("persisted Jev state did not round-trip");
        LOGGER.info("PERSISTENCE_RELOAD_PASS");
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
                        }))))
                .then(Commands.literal("msg").then(Commands.argument("name", StringArgumentType.word())
                        .then(Commands.argument("message", StringArgumentType.greedyString()).executes(context -> {
                            var companion = find(context.getSource().getServer(), StringArgumentType.getString(context, "name"));
                            if (companion == null) { context.getSource().sendFailure(Component.literal("No loaded Jev has that name.")); return 0; }
                            ServerPlayer sender = context.getSource().getPlayerOrException();
                            String message = StringArgumentType.getString(context, "message");
                            boolean authorized = companion.canCommand(sender);
                            companion.observeChat(sender.getUUID(), message, authorized, true);
                            if (!authorized) { context.getSource().sendFailure(Component.literal(companion.getName().getString() + ": you are not authorized to assign goals")); return 0; }
                            companion.acceptGoal(message);
                            context.getSource().sendSuccess(() -> Component.literal(companion.getName().getString() + ": I accepted your goal."), false);
                            return 1;
                        })))));
        event.getDispatcher().register(Commands.literal("jev").then(Commands.literal("admin")
                .then(Commands.argument("name", StringArgumentType.word())
                        .then(Commands.literal("add").then(Commands.argument("player", EntityArgument.player()).executes(context -> changeAdmin(context, true))))
                        .then(Commands.literal("remove").then(Commands.argument("player", EntityArgument.player()).executes(context -> changeAdmin(context, false)))))));
        event.getDispatcher().register(Commands.literal("jev")
                .then(Commands.literal("operator").then(Commands.argument("name", StringArgumentType.word())
                        .then(Commands.literal("enable").executes(context -> changeOperatorCapability(context, true)))
                        .then(Commands.literal("disable").executes(context -> changeOperatorCapability(context, false)))))
                .then(Commands.literal("setspawn").then(Commands.argument("name", StringArgumentType.word()).executes(this::setCompanionSpawn))));
        event.getDispatcher().register(Commands.literal("msg")
                .then(Commands.argument("jevName", StringArgumentType.word())
                        .suggests((context, builder) -> { for (JevCompanion jev : companions(context.getSource().getServer())) builder.suggest(jev.getName().getString()); return builder.buildFuture(); })
                        .then(Commands.argument("message", StringArgumentType.greedyString()).executes(context -> directMessage(context)))));
    }
    private int changeAdmin(com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> context, boolean add)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        JevCompanion companion = find(context.getSource().getServer(), StringArgumentType.getString(context, "name"));
        if (companion == null) return 0;
        ServerPlayer sender = context.getSource().getPlayerOrException();
        if (!companion.canManageAccess(sender)) { context.getSource().sendFailure(Component.literal("Only the owner or a server operator can manage Jev administrators.")); return 0; }
        ServerPlayer target = EntityArgument.getPlayer(context, "player");
        boolean changed = add ? companion.addAdministrator(target.getUUID()) : companion.removeAdministrator(target.getUUID());
        context.getSource().sendSuccess(() -> Component.literal((add ? "Added " : "Removed ") + target.getGameProfile().getName() + " as administrator."), false);
        return changed ? 1 : 0;
    }
    private int changeOperatorCapability(com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> context, boolean allowed) {
        JevCompanion companion = find(context.getSource().getServer(), StringArgumentType.getString(context, "name"));
        if (companion == null) return 0;
        if (!context.getSource().hasPermission(2)) { context.getSource().sendFailure(Component.literal("Only a server operator can change Jev teleport authority.")); return 0; }
        companion.setOperatorTeleportAllowed(allowed);
        context.getSource().sendSuccess(() -> Component.literal("Teleport authority " + (allowed ? "enabled" : "disabled") + " for " + companion.getName().getString()), true);
        return 1;
    }
    private int setCompanionSpawn(com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        JevCompanion companion = find(context.getSource().getServer(), StringArgumentType.getString(context, "name"));
        if (companion == null) return 0;
        ServerPlayer sender = context.getSource().getPlayerOrException();
        if (!companion.canCommand(sender)) { context.getSource().sendFailure(Component.literal("You are not authorized for that Jev.")); return 0; }
        companion.setRespawnPoint(sender.level().dimension(), sender.blockPosition(), sender.getYRot());
        context.getSource().sendSuccess(() -> Component.literal("Respawn point set for " + companion.getName().getString()), false); return 1;
    }
    private int directMessage(com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        JevCompanion companion = find(context.getSource().getServer(), StringArgumentType.getString(context, "jevName"));
        if (companion == null) return 0;
        ServerPlayer sender = context.getSource().getPlayerOrException(); String message = StringArgumentType.getString(context, "message");
        boolean authorized = companion.canCommand(sender); companion.observeChat(sender.getUUID(), message, authorized, true);
        if (!authorized) { context.getSource().sendFailure(Component.literal(companion.getName().getString() + ": you are not authorized to assign goals")); return 0; }
        companion.acceptGoal(message); context.getSource().sendSuccess(() -> Component.literal(companion.getName().getString() + ": I accepted your goal."), false); return 1;
    }
    private void command(CommandEvent event) {
        String input = event.getParseResults().getReader().getString();
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("^(?:msg|tell|w)\\s+(\\S+)\\s+(.+)$", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(input);
        if (!matcher.matches()) return;
        var source = event.getParseResults().getContext().getSource();
        JevCompanion companion = find(source.getServer(), matcher.group(1));
        if (companion == null) return;
        event.setCanceled(true);
        ServerPlayer sender;
        try { sender = source.getPlayerOrException(); } catch (com.mojang.brigadier.exceptions.CommandSyntaxException ignored) { source.sendFailure(Component.literal("Only players can message a Jev.")); return; }
        String message = matcher.group(2); boolean authorized = companion.canCommand(sender);
        companion.observeChat(sender.getUUID(), message, authorized, true);
        if (!authorized) { source.sendFailure(Component.literal(companion.getName().getString() + ": you are not authorized to assign goals")); return; }
        companion.acceptGoal(message); source.sendSuccess(() -> Component.literal(companion.getName().getString() + ": I accepted your goal."), false);
    }
    private static Iterable<JevCompanion> companions(net.minecraft.server.MinecraftServer server) {
        java.util.List<JevCompanion> result = new java.util.ArrayList<>();
        for (var level : server.getAllLevels()) for (var entity : level.getAllEntities()) if (entity instanceof JevCompanion jev) result.add(jev);
        return result;
    }
    private void chat(ServerChatEvent event) {
        ServerPlayer sender = event.getPlayer();
        String text = event.getRawText();
        for (var level : sender.getServer().getAllLevels()) for (var entity : level.getAllEntities())
            if (entity instanceof JevCompanion jev)
                jev.observeChat(sender.getUUID(), text, jev.canCommand(sender), false);
    }
    private static JevCompanion find(net.minecraft.server.MinecraftServer server, String name) {
        String target = name.toLowerCase(Locale.ROOT);
        for (var level : server.getAllLevels()) for (var entity : level.getAllEntities())
            if (entity instanceof JevCompanion jev && jev.getName().getString().toLowerCase(Locale.ROOT).equals(target)) return jev;
        return null;
    }
}
