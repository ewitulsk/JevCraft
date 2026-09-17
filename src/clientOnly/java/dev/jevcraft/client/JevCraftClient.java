package dev.jevcraft.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.glfw.GLFW;

@Mod(JevCraftClient.MOD_ID)
public final class JevCraftClient {
    public static final String MOD_ID = "jevcraft_client";
    private static final KeyMapping OPEN = new KeyMapping("key.jevcraft.open", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F8, "key.categories.jevcraft");
    private static final KeyMapping STOP = new KeyMapping("key.jevcraft.stop", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F9, "key.categories.jevcraft");
    private static String goal = "";
    private static boolean active;

    public JevCraftClient(IEventBus modBus) {
        modBus.addListener(JevCraftClient::keys);
        NeoForge.EVENT_BUS.addListener(JevCraftClient::tick);
    }
    private static void keys(RegisterKeyMappingsEvent event) { event.register(OPEN); event.register(STOP); }
    private static void tick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        while (STOP.consumeClick()) stop(minecraft);
        while (OPEN.consumeClick()) minecraft.setScreen(new TakeoverScreen(goal, JevCraftClient::start, () -> stop(minecraft)));
        if (minecraft.player == null || minecraft.level == null || minecraft.player.isDeadOrDying()) stop(minecraft);
    }
    private static void start(String instruction) { goal = instruction.strip(); active = !goal.isBlank(); }
    public static void stop(Minecraft minecraft) {
        active = false;
        if (minecraft.options != null) {
            minecraft.options.keyUp.setDown(false); minecraft.options.keyDown.setDown(false);
            minecraft.options.keyLeft.setDown(false); minecraft.options.keyRight.setDown(false);
            minecraft.options.keyJump.setDown(false); minecraft.options.keyShift.setDown(false);
            minecraft.options.keySprint.setDown(false); minecraft.options.keyAttack.setDown(false); minecraft.options.keyUse.setDown(false);
        }
    }
    public static boolean active() { return active; }
    public static String goal() { return goal; }
}
