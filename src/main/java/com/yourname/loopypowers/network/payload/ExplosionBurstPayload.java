package com.yourname.loopypowers.network.payload;

import com.yourname.loopypowers.Loopypowers;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Explosion visual: EXPLOSION_EMITTER + campfire smoke + lava, sized by power. */
public record ExplosionBurstPayload(double x, double y, double z, float power) implements CustomPayload {

    public static final Id<ExplosionBurstPayload> ID =
            new Id<>(Identifier.of(Loopypowers.MOD_ID, "explosion_burst"));

    public static final PacketCodec<PacketByteBuf, ExplosionBurstPayload> CODEC =
            PacketCodec.of(ExplosionBurstPayload::write, ExplosionBurstPayload::new);

    public ExplosionBurstPayload(PacketByteBuf buf) {
        this(buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readFloat());
    }

    public void write(PacketByteBuf buf) {
        buf.writeDouble(x); buf.writeDouble(y); buf.writeDouble(z); buf.writeFloat(power);
    }

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
