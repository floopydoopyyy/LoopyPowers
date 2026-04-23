package com.yourname.loopypowers.mixin;

import com.yourname.loopypowers.manager.PowerManager;
import com.yourname.loopypowers.power.ExplosionPower;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(LivingEntity.class)
public abstract class EntityExplodeResistMixin {

    private static final float EXPLOSION_MULT = 0.20f; // amount of damage resisted

    @Unique
    @ModifyVariable(
            method = "damage(Lnet/minecraft/entity/damage/DamageSource;F)Z",
            at = @At("HEAD"),
            index = 2,          // 0=this, 1=DamageSource, 2=float amount
            argsOnly = false    // IMPORTANT for index=2 + signature below
    )
    private float loopypowers$reduceExplosionDamage(float amount, DamageSource source) {
        LivingEntity self = (LivingEntity) (Object) this;

        // only server-side players
        if (!(self instanceof ServerPlayerEntity player)) return amount;

        // only explosion damage
        if (!source.isIn(DamageTypeTags.IS_EXPLOSION)) return amount;

        // only if player currently has ExplosionPower
        if (!(PowerManager.getPower(player) instanceof ExplosionPower)) return amount;

        return amount * EXPLOSION_MULT;
    }
}