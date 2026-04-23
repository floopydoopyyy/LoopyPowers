package com.yourname.loopypowers.mixin;

import com.yourname.loopypowers.item.ModItems;
import com.yourname.loopypowers.manager.PowerManager;
import com.yourname.loopypowers.power.FlightPower;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerEntity.class)
public class PlayerEntityDropMixin { // this is to stop certain items being droppable.

    @Inject(method = "dropItem(Lnet/minecraft/item/ItemStack;ZZ)Lnet/minecraft/entity/ItemEntity;",
            at = @At("HEAD"), cancellable = true)
    private void loopypowers$blockDroppingWings(ItemStack stack, boolean throwRandomly, boolean retainOwnership,
                                                CallbackInfoReturnable<ItemEntity> cir) {
        PlayerEntity self = (PlayerEntity)(Object)this;

        if (stack.isOf(ModItems.WINGS_OF_VALOR)
                && self instanceof net.minecraft.server.network.ServerPlayerEntity sp
                && PowerManager.getPower(sp) instanceof FlightPower) {
            cir.setReturnValue(null); // cancel drop
        }
    }
}