package dev.jevcraft.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.jevcraft.core.GoalParser;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import dev.jevcraft.inference.*;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CompletableFuture;

/** Shared client controller used by both the full and client-only artifacts. */
public final class TakeoverRuntime {
    private static final Logger LOGGER=LogUtils.getLogger();
    private static final KeyMapping OPEN = new KeyMapping("key.jevcraft.open", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F8, "key.categories.jevcraft");
    private static final KeyMapping STOP = new KeyMapping("key.jevcraft.stop", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F9, "key.categories.jevcraft");
    private static final UUID ACTOR = UUID.nameUUIDFromBytes("jevcraft:local-player".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    private static boolean initialized, active;
    private static String goal = "", status = "Stopped";
    private static long goalVersion;
    private static BlockPos target;
    private static BlockPos pickupTarget;
    private static int pickupUntilTick;
    private static CancellationToken inFlight;
    private static InferenceProvider provider;
    private static boolean hiddenTestStarted;
    private static boolean hiddenConnectStarted;
    private static int hiddenBootTicks;
    private static int nextRequestTick;

    private TakeoverRuntime() {}
    public static void initialize(IEventBus modBus) {
        if (initialized) return;
        initialized = true;
        modBus.addListener(TakeoverRuntime::keys);
        NeoForge.EVENT_BUS.addListener(TakeoverRuntime::tick);
        String route = value("JEVCRAFT_PROVIDER", "vercel_gateway");
        String key = value(route.equals("typesafe_direct") ? "TYPESAFE_API_KEY" : "AI_GATEWAY_API_KEY", "");
        if (Boolean.getBoolean("jevcraft.hiddenClientTest") && !Boolean.getBoolean("jevcraft.hiddenLiveGateway")) provider = fixedTestProvider();
        else provider = key.isBlank() || route.equals("disabled") ? null
                : route.equals("typesafe_direct") ? JsonInferenceProvider.typesafe(key) : JsonInferenceProvider.vercel(key);
    }
    private static String value(String name, String fallback) {
        String property = System.getProperty(name); if (property != null && !property.isBlank()) return property;
        String environment = System.getenv(name); return environment == null || environment.isBlank() ? fallback : environment;
    }
    private static void keys(RegisterKeyMappingsEvent event) { event.register(OPEN); event.register(STOP); }
    private static void tick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        hiddenAutoConnect(minecraft);
        while (STOP.consumeClick()) stop(minecraft, "Emergency stop");
        while (OPEN.consumeClick()) minecraft.setScreen(new TakeoverScreen(goal, TakeoverRuntime::start, () -> stop(minecraft, "Stopped")));
        hiddenTestLifecycle(minecraft);
        if (!active) return;
        if (minecraft.player == null || minecraft.level == null || minecraft.gameMode == null || minecraft.player.isDeadOrDying()) {
            stop(minecraft, "World unavailable"); return;
        }
        if (physicalOverride(minecraft)) { stop(minecraft, "Player reclaimed control"); return; }
        GoalParser.Goal parsed = new GoalParser().parse(goal);
        if (parsed.kind() != GoalParser.Kind.ACQUIRE || !parsed.subject().contains("log")) {
            stop(minecraft, "Unsupported takeover goal"); return;
        }
        if (countLogs(minecraft.player) >= parsed.quantity()) { stop(minecraft, "Goal complete"); return; }
        if (target != null && minecraft.level.getBlockState(target).isAir()) { pickupTarget=target; pickupUntilTick=minecraft.player.tickCount+120; target=null; }
        if (pickupTarget != null) {
            if (minecraft.player.tickCount > pickupUntilTick) pickupTarget=null;
            else { approachDrop(minecraft,pickupTarget); return; }
        }
        if (target == null) {
            releaseKeys(minecraft); minecraft.gameMode.stopDestroyBlock();
            if (provider == null) { status = "Missing provider credential"; return; }
            if (inFlight == null && minecraft.player.tickCount >= nextRequestTick && minecraft.player.tickCount % 10 == 0) requestTarget(minecraft, parsed);
            return;
        }
        executeMining(minecraft, target);
        hiddenTestLifecycle(minecraft);
    }
    private static void hiddenAutoConnect(Minecraft minecraft) {
        if (!Boolean.getBoolean("jevcraft.hiddenClientTest") || hiddenConnectStarted || minecraft.player != null) return;
        if (++hiddenBootTicks < 20 || minecraft.screen == null) return;
        hiddenConnectStarted=true;
        String address="127.0.0.1:"+System.getProperty("jevcraft.testPort","25575");
        ServerData data=new ServerData("JevCraft hidden test",address,ServerData.Type.OTHER);
        ConnectScreen.startConnecting(minecraft.screen,minecraft,ServerAddress.parseString(address),data,false,null);
    }
    private static void hiddenTestLifecycle(Minecraft minecraft) {
        if (!Boolean.getBoolean("jevcraft.hiddenClientTest") || minecraft.player == null || minecraft.level == null) return;
        if (!hiddenTestStarted && minecraft.player.tickCount > 40) { hiddenTestStarted=true; start("collect one oak log"); LOGGER.info("HIDDEN_TAKEOVER_CLIENT_START"); }
        if (hiddenTestStarted && countLogs(minecraft.player) >= 1) {
            stop(minecraft,"Goal complete");
            boolean released = !minecraft.options.keyUp.isDown() && !minecraft.options.keyJump.isDown() && !minecraft.options.keyAttack.isDown() && !minecraft.options.keyUse.isDown();
            if (!released) throw new IllegalStateException("takeover stop left a synthetic key held");
            LOGGER.info("HIDDEN_TAKEOVER_CLIENT_PASS"); minecraft.stop();
        } else if (hiddenTestStarted && minecraft.player.tickCount > 1200) {
            throw new IllegalStateException("hidden takeover timed out: " + status);
        }
    }
    private static boolean physicalOverride(Minecraft minecraft) {
        long window = minecraft.getWindow().getWindow();
        return InputConstants.isKeyDown(window, GLFW.GLFW_KEY_W) || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_A)
                || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_S) || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_D)
                || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_SPACE);
    }
    private static void requestTarget(Minecraft minecraft, GoalParser.Goal parsed) {
        Map<String, BlockPos> positions = visibleLogs(minecraft, 8, 32);
        if (positions.isEmpty()) { status = "No visible logs"; return; }
        Map<String, Object> choices = new LinkedHashMap<>();
        positions.forEach((id, pos) -> choices.put(id, "Mine visible log at relative position " + relative(minecraft.player, pos)));
        choices.put("wait", "Wait because no candidate is currently feasible");
        long version = goalVersion;
        InferenceRequest request = new InferenceRequest(UUID.randomUUID(), ACTOR, 1, minecraft.player.tickCount, version,
                Map.of("goal", goal, "health", minecraft.player.getHealth(), "food", minecraft.player.getFoodData().getFoodLevel(),
                        "hotbar", minecraft.player.getInventory().items.subList(0, 9).stream().map(s -> s.getHoverName().getString()).toList()),
                Map.of("action", new Question.Choice("Choose the best feasible next action for the stated goal.", choices)), Instant.now().plusSeconds(3));
        CancellationToken token = new CancellationToken(); inFlight = token; status = "Asking Jev";
        provider.evaluate(request, token).orTimeout(5, TimeUnit.SECONDS).whenComplete((response, failure) -> minecraft.execute(() -> {
            if (inFlight != token) return; inFlight = null;
            if (!active || goalVersion != version || minecraft.level == null) return;
            if (failure != null) {
                Throwable cause=failure instanceof java.util.concurrent.CompletionException && failure.getCause()!=null?failure.getCause():failure;
                int delay=100;
                if(cause instanceof ProviderException providerFailure && providerFailure.retryAfter()!=null) delay=Math.max(delay,(int)(providerFailure.retryAfter().toMillis()/50));
                nextRequestTick=minecraft.player.tickCount+delay; status="Decision unavailable; retrying later";
                LOGGER.warn("Takeover decision failed: {}",cause.getMessage()); return;
            }
            Answer answer = response.answers().get("action");
            if (answer instanceof Answer.Choice choice) {
                LOGGER.info("TAKEOVER_DECISION provider={} model={} latencyMs={} choice={}", response.provider(), response.model(), response.latency().toMillis(), choice.choice());
                BlockPos selected = positions.get(choice.choice());
                if (selected != null && minecraft.level.getBlockState(selected).is(BlockTags.LOGS) && canSee(minecraft, selected)) {
                    target = selected; status = "Mining " + relative(minecraft.player, selected);
                } else { nextRequestTick=minecraft.player.tickCount+20; status = "Jev chose to wait"; }
            }
        }));
    }
    private static Map<String, BlockPos> visibleLogs(Minecraft minecraft, int radius, int limit) {
        Map<String, BlockPos> found = new LinkedHashMap<>(); BlockPos center = minecraft.player.blockPosition();
        for (BlockPos cursor : BlockPos.betweenClosed(center.offset(-radius, -3, -radius), center.offset(radius, radius, radius))) {
            if (found.size() >= limit) break; BlockPos pos = cursor.immutable();
            if (minecraft.level.getBlockState(pos).is(BlockTags.LOGS) && canSee(minecraft, pos)) found.put("mine_" + found.size(), pos);
        }
        return found;
    }
    private static boolean canSee(Minecraft minecraft, BlockPos pos) {
        BlockHitResult hit = minecraft.level.clip(new ClipContext(minecraft.player.getEyePosition(), Vec3.atCenterOf(pos), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, minecraft.player));
        return hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(pos);
    }
    private static void executeMining(Minecraft minecraft, BlockPos pos) {
        LocalPlayer player = minecraft.player; Vec3 center = Vec3.atCenterOf(pos); Vec3 delta = center.subtract(player.getEyePosition());
        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        player.setYRot((float) (Mth.atan2(delta.z, delta.x) * 180 / Math.PI) - 90);
        player.setXRot((float) -(Mth.atan2(delta.y, horizontal) * 180 / Math.PI));
        if (player.distanceToSqr(center) > 20.25) {
            minecraft.gameMode.stopDestroyBlock(); minecraft.options.keyUp.setDown(true);
            minecraft.options.keyJump.setDown(player.horizontalCollision); status = "Approaching target"; return;
        }
        minecraft.options.keyUp.setDown(false); minecraft.options.keyJump.setDown(false);
        selectBestHotbarTool(player, minecraft.level.getBlockState(pos));
        BlockHitResult hit = minecraft.level.clip(new ClipContext(player.getEyePosition(), center, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        Direction face = hit.getType() == HitResult.Type.BLOCK ? hit.getDirection() : Direction.UP;
        minecraft.options.keyAttack.setDown(true); minecraft.gameMode.continueDestroyBlock(pos, face); player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
    }
    private static void approachDrop(Minecraft minecraft,BlockPos pos){
        LocalPlayer player=minecraft.player; Vec3 center=Vec3.atCenterOf(pos); Vec3 delta=center.subtract(player.position());
        player.setYRot((float)(Mth.atan2(delta.z,delta.x)*180/Math.PI)-90); minecraft.gameMode.stopDestroyBlock(); minecraft.options.keyAttack.setDown(false);
        boolean move=player.distanceToSqr(center)>1.0; minecraft.options.keyUp.setDown(move); minecraft.options.keyJump.setDown(move&&player.horizontalCollision); status="Collecting drop";
    }
    private static void selectBestHotbarTool(LocalPlayer player, net.minecraft.world.level.block.state.BlockState state) {
        int best = player.getInventory().selected; float speed = player.getInventory().getItem(best).getDestroySpeed(state);
        for (int i = 0; i < 9; i++) { float candidate = player.getInventory().getItem(i).getDestroySpeed(state); if (candidate > speed) { speed = candidate; best = i; } }
        player.getInventory().selected = best;
    }
    private static int countLogs(LocalPlayer player) { return player.getInventory().items.stream().filter(s -> s.is(ItemTags.LOGS)).mapToInt(net.minecraft.world.item.ItemStack::getCount).sum(); }
    private static String relative(LocalPlayer player, BlockPos pos) { return (pos.getX()-player.getBlockX())+","+(pos.getY()-player.getBlockY())+","+(pos.getZ()-player.getBlockZ()); }
    public static void start(String instruction) { Minecraft minecraft = Minecraft.getInstance(); stop(minecraft, "Goal replaced"); goal = instruction.strip(); active = !goal.isBlank(); goalVersion++; status = active ? "Preparing" : "Stopped"; }
    public static void stop(Minecraft minecraft, String reason) {
        active = false; target = null; pickupTarget=null; goalVersion++; status = reason;
        if (inFlight != null) { inFlight.cancel(); inFlight = null; }
        if (minecraft.gameMode != null) minecraft.gameMode.stopDestroyBlock(); releaseKeys(minecraft);
    }
    private static void releaseKeys(Minecraft minecraft) {
        if (minecraft.options == null) return;
        minecraft.options.keyUp.setDown(false); minecraft.options.keyDown.setDown(false); minecraft.options.keyLeft.setDown(false); minecraft.options.keyRight.setDown(false);
        minecraft.options.keyJump.setDown(false); minecraft.options.keyShift.setDown(false); minecraft.options.keySprint.setDown(false); minecraft.options.keyAttack.setDown(false); minecraft.options.keyUse.setDown(false);
    }
    public static boolean active() { return active; }
    public static String goal() { return goal; }
    public static String status() { return status; }
    private static InferenceProvider fixedTestProvider() {
        return new InferenceProvider() {
            @Override public String id(){return "hidden_test_fixture";}
            @Override public CompletableFuture<InferenceResponse> evaluate(InferenceRequest request,CancellationToken cancellation){
                Question.Choice question=(Question.Choice)request.questions().get("action");
                String choice=question.criteria().keySet().stream().filter(k->!k.equals("wait")).findFirst().orElse("wait");
                return CompletableFuture.completedFuture(new InferenceResponse(request.requestId(),id(),"fixture",Map.of("action",new Answer.Choice(choice,Map.of(choice,1.0),1.0)),new InferenceResponse.Usage(0,0),Map.of(),List.of(),java.time.Duration.ZERO));
            }
        };
    }
}
