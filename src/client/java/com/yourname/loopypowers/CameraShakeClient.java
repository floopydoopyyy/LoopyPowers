package com.yourname.loopypowers;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.MathHelper;

import java.util.Random;

// why the FUCK doesnt this work
//

public final class CameraShakeClient {
    private static int remaining = 0;
    private static float strength = 0f;
    private static final Random RNG = new Random();

    private CameraShakeClient() {}

    public static void start(int ticks, float s) {
        System.out.println("Camera shake received: " + ticks + " strength " + s);
        remaining = Math.max(remaining, ticks); // parameters for shake
        strength = Math.max(strength, s);
    }

    public static void tick(MinecraftClient client) {
        if (remaining <= 0) return;
        if (client.player == null) return;

        remaining--;

        // makes it fade out i think hard to tell
        float fade = MathHelper.clamp(remaining / 10.0f, 0f, 1f);
        float amp = strength * fade;

        float yawJitter = (RNG.nextFloat() * 2f - 1f) * amp;
        float pitchJitter = (RNG.nextFloat() * 2f - 1f) * amp * 0.6f;

        client.player.setYaw(client.player.getYaw() + yawJitter);
        client.player.setPitch(MathHelper.clamp(client.player.getPitch() + pitchJitter, -90f, 90f));
    }
}