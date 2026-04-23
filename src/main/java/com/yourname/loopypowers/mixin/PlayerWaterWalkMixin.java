package com.yourname.loopypowers.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.registry.tag.FluidTags;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class PlayerWaterWalkMixin {

    @Inject(
            method = "canWalkOnFluid(Lnet/minecraft/fluid/FluidState;)Z",
            at = @At("HEAD"),
            cancellable = true
    )
    private void loopypowers$waterWalkInOverdrive(FluidState state, CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self = (LivingEntity)(Object)this;

        if (!(self instanceof PlayerEntity player)) return;
        if (!state.isIn(FluidTags.WATER)) return;

        // Use your Overdrive tag instead of speed effect
        if (player.getCommandTags().contains("overdrive")) {
            cir.setReturnValue(true);
        }
    }
}