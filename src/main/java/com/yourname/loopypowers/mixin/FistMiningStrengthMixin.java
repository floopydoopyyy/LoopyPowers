package com.yourname.loopypowers.mixin;

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
// allows strength people to break blocks with the power of an iron pickaxe
@Mixin(PlayerEntity.class)
public abstract class FistMiningStrengthMixin {

    @Inject(
            method = "canHarvest(Lnet/minecraft/block/BlockState;)Z",
            at = @At("HEAD"),
            cancellable = true
    )
    private void loopypowers$strengthFistHarvest(BlockState state, CallbackInfoReturnable<Boolean> cir) {
        PlayerEntity self = (PlayerEntity)(Object)this;

        // only empty-hand
        if (!self.getMainHandStack().isEmpty()) return;

        // only pickaxe-mineable blocks
        if (!state.isIn(BlockTags.PICKAXE_MINEABLE)) return;

        // don’t fake diamond-tier
        if (state.isIn(BlockTags.NEEDS_DIAMOND_TOOL)) return;

        // decide "has strength" by side
        boolean hasStrength;
        if (self.getWorld().isClient) {
            hasStrength = com.yourname.loopypowers.network.ClientPowerState.hasStrengthPower();
        } else if (self instanceof ServerPlayerEntity sp) {
            hasStrength = (PowerManager.getPower(sp) instanceof StrengthPower);
        } else {
            return;
        }

        if (!hasStrength) return;

        cir.setReturnValue(true);
    }
}