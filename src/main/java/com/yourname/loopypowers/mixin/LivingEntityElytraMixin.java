package com.yourname.loopypowers.mixin;

import com.yourname.loopypowers.item.ModItems;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LivingEntity.class)
public class LivingEntityElytraMixin {

    /**
     * Tells server that custom wings are also an elytra
     * Side note: fuck this.
     */
    @Redirect(
            method = "tickFallFlying",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/item/ItemStack;isOf(Lnet/minecraft/item/Item;)Z"
            )
    )
    private boolean loopypowers$acceptCustomElytra(ItemStack stack, Item item) {
        if (item == Items.ELYTRA) {
            return stack.isOf(Items.ELYTRA) || stack.isOf(ModItems.WINGS_OF_VALOR);
        }
        return stack.isOf(item);
    }
}