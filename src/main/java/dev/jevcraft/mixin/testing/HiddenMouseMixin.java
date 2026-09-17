package dev.jevcraft.mixin.testing;

import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Opt-in logical mouse state without OS cursor capture or positioning. */
@Mixin(MouseHandler.class)
public abstract class HiddenMouseMixin {
    @Shadow private boolean mouseGrabbed;
    @Inject(method="grabMouse",at=@At("HEAD"),cancellable=true) private void jevcraft$grab(CallbackInfo ci){if(Boolean.getBoolean("jevcraft.hiddenClient")){mouseGrabbed=true;ci.cancel();}}
    @Inject(method="releaseMouse",at=@At("HEAD"),cancellable=true) private void jevcraft$release(CallbackInfo ci){if(Boolean.getBoolean("jevcraft.hiddenClient")){mouseGrabbed=false;ci.cancel();}}
    @Inject(method="turnPlayer",at=@At("HEAD"),cancellable=true) private void jevcraft$scriptedLook(CallbackInfo ci){if(Boolean.getBoolean("jevcraft.hiddenClient"))ci.cancel();}
}
