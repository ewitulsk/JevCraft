package dev.jevcraft.client;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod(JevCraftClient.MOD_ID)
public final class JevCraftClient {
    public static final String MOD_ID = "jevcraft_client";
    public JevCraftClient(IEventBus modBus) { TakeoverRuntime.initialize(modBus); }
}
