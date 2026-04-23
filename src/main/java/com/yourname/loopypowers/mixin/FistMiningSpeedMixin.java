package com.yourname.loopypowers.mixin;

import com.yourname.loopypowers.network.ClientPowerState;
import com.yourname.loopypowers.manager.PowerManager;
import com.yourname.loopypowers.power.StrengthPower;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerEntity.class)
public abstract class FistMiningSpeedMixin {
    // this class just makes it so the mining is faster with strength. the other class increases the strength with the fist.
    private static final float IRON_PICK_SPEED_MULT = 6.0f; // multiplier for fist
    private static final float TOOL_SPEED_MULT = 1.8f; // multiplier for all tools

    @Inject(
            method = "getBlockBreakingSpeed(Lnet/minecraft/block/BlockState;)F",
            at = @At("RETURN"),
            cancellable = true
    )
    private void loopypowers$strengthFistSpeed(BlockState state, CallbackInfoReturnable<Float> cir) {
        PlayerEntity self = (PlayerEntity)(Object)this;

        // decide "has strength power" depending on side
        boolean hasStrength;
        if (self.getWorld().isClient) {
            hasStrength = ClientPowerState.hasStrengthPower();
        } else if (self instanceof ServerPlayerEntity sp) {
            hasStrength = (PowerManager.getPower(sp) instanceof StrengthPower);
        } else {
            return;
        }
        if (!hasStrength) return;

        float current = cir.getReturnValueF();
        if (current <= 0.0f) return; // don't try to break unbreakable stuff

        // EMPTY HAND - makes it so mining speed is very fast (aimed for iron pickaxe level)
        if (self.getMainHandStack().isEmpty()) {
            if (!state.isIn(BlockTags.PICKAXE_MINEABLE)) return;
            cir.setReturnValue(current * IRON_PICK_SPEED_MULT);
            return;
        }
        // TOOL IN HAND - make it a bit quicker
        if (self.getMainHandStack().isDamageable()) {
            cir.setReturnValue(current * TOOL_SPEED_MULT);
        }
    }
}