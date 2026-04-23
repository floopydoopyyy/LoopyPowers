package com.yourname.loopypowers.mixin;

import com.yourname.loopypowers.item.ModItems;
import com.yourname.loopypowers.manager.PowerManager;
import com.yourname.loopypowers.power.FlightPower;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Slot.class)
public class ArmourSlotMixin { // this stops the wings from being removed

    @Inject(method = "canTakeItems(Lnet/minecraft/entity/player/PlayerEntity;)Z",
            at = @At("HEAD"), cancellable = true)
    private void loopypowers$blockTakingWings(PlayerEntity player, CallbackInfoReturnable<Boolean> cir) {
        ItemStack stack = ((Slot)(Object)this).getStack();

        if (!stack.isOf(ModItems.WINGS_OF_VALOR)) return;

        // Only lock it while the player currently has FlightPower
        if (player instanceof net.minecraft.server.network.ServerPlayerEntity sp
                && PowerManager.getPower(sp) instanceof FlightPower) {
            cir.setReturnValue(false);
        }
    }
}