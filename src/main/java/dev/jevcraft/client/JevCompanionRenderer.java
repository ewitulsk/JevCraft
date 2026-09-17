package dev.jevcraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.jevcraft.JevCraft;
import dev.jevcraft.companion.JevCompanion;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = JevCraft.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class JevCompanionRenderer extends MobRenderer<JevCompanion, PlayerModel<JevCompanion>> {
    private static final ResourceLocation SKIN = ResourceLocation.withDefaultNamespace("textures/entity/player/wide/steve.png");
    public JevCompanionRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), .5f);
    }
    @Override public ResourceLocation getTextureLocation(JevCompanion entity) { return SKIN; }
    @SubscribeEvent public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(JevCraft.JEV.get(), JevCompanionRenderer::new);
    }
}
