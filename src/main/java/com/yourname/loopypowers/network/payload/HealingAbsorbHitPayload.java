package com.yourname.loopypowers.network.payload;

import com.yourname.loopypowers.Loopypowers;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record HealingAbsorbHitPayload(double x, double y, double z, float intensity) implements CustomPayload {

    public static final Id<HealingAbsorbHitPayload> ID =
            new Id<>(Identifier.of(Loopypowers.MOD_ID, "healing_absorb_hit"));

    public static final PacketCodec<PacketByteBuf, HealingAbsorbHitPayload> CODEC =
            PacketCodec.of(HealingAbsorbHitPayload::write, HealingAbsorbHitPayload::new);

    public HealingAbsorbHitPayload(PacketByteBuf buf) {
        this(buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readFloat());
    }

    public void write(PacketByteBuf buf) {
        buf.writeDouble(x); buf.writeDouble(y); buf.writeDouble(z); buf.writeFloat(intensity);
    }

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
