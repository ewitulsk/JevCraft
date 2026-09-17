package dev.jevcraft.mixin;

import dev.jevcraft.companion.CompanionSleepStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.SleepStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(ServerLevel.class)
abstract class ServerLevelSleepMixin {
    @Redirect(method="tick",at=@At(value="INVOKE",target="Lnet/minecraft/server/players/SleepStatus;areEnoughSleeping(I)Z"))
    private boolean jevcraft$includeCompanionsSleeping(SleepStatus vanilla,int percentage){
        ServerLevel level=(ServerLevel)(Object)this;
        return CompanionSleepStatus.hasCompanions(level)?CompanionSleepStatus.enough(level,percentage,false):vanilla.areEnoughSleeping(percentage);
    }

    @Redirect(method="tick",at=@At(value="INVOKE",target="Lnet/minecraft/server/players/SleepStatus;areEnoughDeepSleeping(ILjava/util/List;)Z"))
    private boolean jevcraft$includeCompanionsDeepSleeping(SleepStatus vanilla,int percentage,List<ServerPlayer> players){
        ServerLevel level=(ServerLevel)(Object)this;
        return CompanionSleepStatus.hasCompanions(level)?CompanionSleepStatus.enough(level,percentage,true):vanilla.areEnoughDeepSleeping(percentage,players);
    }

    @Inject(method="wakeUpAllPlayers",at=@At("TAIL"))
    private void jevcraft$wakeCompanions(CallbackInfo callback){CompanionSleepStatus.wakeCompanions((ServerLevel)(Object)this);}
}
