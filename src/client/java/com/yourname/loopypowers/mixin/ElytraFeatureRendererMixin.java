package com.yourname.loopypowers.mixin;

import com.yourname.loopypowers.Loopypowers;
import com.yourname.loopypowers.item.ModItems;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.ElytraFeatureRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ElytraFeatureRenderer.class)
public class ElytraFeatureRendererMixin {

    @Unique
    private static final Identifier WINGS_OF_VALOR_TEXTURE =
            new Identifier(Loopypowers.MOD_ID, "textures/entity/wings_of_valor.png");

    /**
     * makes elytra model appear.
     */
    @Redirect(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/item/ItemStack;isOf(Lnet/minecraft/item/Item;)Z"
            )
    )
    private boolean loopypowers$acceptWingsOfValor(ItemStack stack, Item item) {
        if (item == Items.ELYTRA) {
            return stack.isOf(Items.ELYTRA) || stack.isOf(ModItems.WINGS_OF_VALOR);
        }
        return stack.isOf(item);
    }

    /**
     * replaces the elytra with the new texture
     * fuck this.
     */
    @ModifyVariable(
            method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;ILnet/minecraft/entity/LivingEntity;FFFFFF)V",
            at = @At("STORE"),
            ordinal = 0
    )
    private Identifier loopypowers$swapElytraTexture(
            Identifier original,
            MatrixStack matrices,
            VertexConsumerProvider vertexConsumers,
            int light,
            LivingEntity entity,
            float limbAngle,
            float limbDistance,
            float tickDelta,
            float animationProgress,
            float headYaw,
            float headPitch
    ) {
        ItemStack chest = entity.getEquippedStack(EquipmentSlot.CHEST);
        if (chest.isOf(ModItems.WINGS_OF_VALOR)) {
            return WINGS_OF_VALOR_TEXTURE;
        }
        return original;
    }
}