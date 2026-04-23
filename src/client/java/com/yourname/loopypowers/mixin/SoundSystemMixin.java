package com.yourname.loopypowers.mixin;

import com.yourname.loopypowers.StunAudioClient;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(SoundSystem.class)
public class SoundSystemMixin {

    @Redirect(
            method = "play(Lnet/minecraft/client/sound/SoundInstance;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/sound/SoundInstance;getVolume()F"
            )
    )
    private float loopypowers$stunAudioDampen(SoundInstance instance) {
        return instance.getVolume() * StunAudioClient.volumeMultiplier();
    }
}