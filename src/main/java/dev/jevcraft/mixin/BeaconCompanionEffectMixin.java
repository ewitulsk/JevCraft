package dev.jevcraft.mixin;

import dev.jevcraft.companion.JevCompanion;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BeaconBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BeaconBlockEntity.class)
public abstract class BeaconCompanionEffectMixin {
    @Inject(method="tick",at=@At("TAIL"))
    private static void jevcraft$applyEffectsToCompanions(Level level, BlockPos pos, BlockState state, BeaconBlockEntity beacon, CallbackInfo ci){
        if(level.isClientSide||level.getGameTime()%80!=0)return;BeaconBlockEntityAccessor access=(BeaconBlockEntityAccessor)beacon;int levels=access.jevcraft$levels();Holder<MobEffect> primary=access.jevcraft$primaryPower();if(levels<=0||primary==null)return;
        double radius=levels*10+10;int duration=(9+levels*2)*20;Holder<MobEffect> secondary=access.jevcraft$secondaryPower();int amplifier=levels>=4&&primary.equals(secondary)?1:0;AABB area=new AABB(pos).inflate(radius).expandTowards(0,level.getHeight(),0);
        for(JevCompanion companion:level.getEntitiesOfClass(JevCompanion.class,area)){companion.addEffect(new MobEffectInstance(primary,duration,amplifier,true,true));if(levels>=4&&secondary!=null&&!secondary.equals(primary))companion.addEffect(new MobEffectInstance(secondary,duration,0,true,true));}
    }
}
