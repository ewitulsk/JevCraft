package dev.jevcraft.mixin;

import net.minecraft.world.inventory.ContainerData;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.level.block.entity.BeaconBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(BeaconBlockEntity.class)
public interface BeaconBlockEntityAccessor {
    @Accessor("dataAccess")
    ContainerData jevcraft$dataAccess();

    @Accessor("levels")
    int jevcraft$levels();

    @Accessor("primaryPower")
    Holder<MobEffect> jevcraft$primaryPower();

    @Accessor("secondaryPower")
    Holder<MobEffect> jevcraft$secondaryPower();
}
