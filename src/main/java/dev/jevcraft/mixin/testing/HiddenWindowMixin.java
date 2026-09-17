package dev.jevcraft.mixin.testing;

import com.mojang.blaze3d.platform.Window;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Opt-in test process only: the Minecraft window is never shown or focused. */
@Mixin(Window.class)
public abstract class HiddenWindowMixin {
    @Shadow private boolean fullscreen;
    @Inject(method="<init>", at=@At(value="INVOKE", target="Lnet/neoforged/fml/loading/ImmediateWindowHandler;setupMinecraftWindow(Ljava/util/function/IntSupplier;Ljava/util/function/IntSupplier;Ljava/util/function/Supplier;Ljava/util/function/LongSupplier;)J"))
    private void jevcraft$hideBeforeCreation(CallbackInfo ci) {
        if (!Boolean.getBoolean("jevcraft.hiddenClient")) return;
        fullscreen=false; GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,GLFW.GLFW_FALSE); GLFW.glfwWindowHint(GLFW.GLFW_FOCUSED,GLFW.GLFW_FALSE); GLFW.glfwWindowHint(GLFW.GLFW_FOCUS_ON_SHOW,GLFW.GLFW_FALSE);
    }
    @Inject(method="setMode",at=@At("HEAD"),cancellable=true)
    private void jevcraft$noMonitorChanges(CallbackInfo ci){if(Boolean.getBoolean("jevcraft.hiddenClient")){fullscreen=false;ci.cancel();}}
}
