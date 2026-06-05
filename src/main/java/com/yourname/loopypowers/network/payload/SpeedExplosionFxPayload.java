package com.yourname.loopypowers.network.payload;

import com.yourname.loopypowers.Loopypowers;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * One-shot explosion burst at the player's position.
 * heavy=true — entity collision; heavy=false — block collision.
 */
public record SpeedExplosionFxPayload(
        double x, double y, double z,
        boolean heavy
) implements CustomPayload {

    public static final Id<SpeedExplosionFxPayload> ID =
            new Id<>(Identifier.of(Loopypowers.MOD_ID, "speed_explosion_fx"));

    public static final PacketCodec<PacketByteBuf, SpeedExplosionFxPayload> CODEC =
            PacketCodec.of(SpeedExplosionFxPayload::write, SpeedExplosionFxPayload::new);

    public SpeedExplosionFxPayload(PacketByteBuf buf) {
        this(buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readBoolean());
    }

    public void write(PacketByteBuf buf) {
        buf.writeDouble(x); buf.writeDouble(y); buf.writeDouble(z);
        buf.writeBoolean(heavy);
    }

    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
