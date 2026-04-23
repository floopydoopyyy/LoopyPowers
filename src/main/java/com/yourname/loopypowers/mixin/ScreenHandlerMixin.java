package com.yourname.loopypowers.mixin;

import com.yourname.loopypowers.item.ModItems;
import com.yourname.loopypowers.manager.PowerManager;
import com.yourname.loopypowers.power.FlightPower;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ScreenHandler.class)
public class ScreenHandlerMixin { // i FUCKING HATE MIXINS

    @Inject(
            method = "onSlotClick",
            at = @At("HEAD"),
            cancellable = true
    )
    private void loopypowers$blockMovingWings(
            int slotIndex, int button, SlotActionType actionType, PlayerEntity player,
            CallbackInfo ci
    ) {
        if (player.getWorld().isClient()) return;

        if (!(player instanceof net.minecraft.server.network.ServerPlayerEntity sp)) return;
        if (!(PowerManager.getPower(sp) instanceof FlightPower)) return;

        ScreenHandler self = (ScreenHandler)(Object)this;

        // Block if cursor holding wings
        ItemStack cursor = self.getCursorStack();
        if (cursor.isOf(ModItems.WINGS_OF_VALOR)) {
            ci.cancel();
            return;
        }

        // Block if clicked slot contains wings
        if (slotIndex >= 0 && slotIndex < self.slots.size()) {
            Slot slot = self.slots.get(slotIndex);
            if (slot != null && slot.hasStack() && slot.getStack().isOf(ModItems.WINGS_OF_VALOR)) {
                ci.cancel();
            }
        }
    }
}