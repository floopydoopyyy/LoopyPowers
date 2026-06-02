package com.yourname.loopypowers.network.payload;

import com.yourname.loopypowers.Loopypowers;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record BloodBleedTickPayload(double x, double y, double z) implements CustomPayload {

    public static final Id<BloodBleedTickPayload> ID =
            new Id<>(Identifier.of(Loopypowers.MOD_ID, "blood_bleed_tick"));

    public static final PacketCodec<PacketByteBuf, BloodBleedTickPayload> CODEC =
            PacketCodec.of(BloodBleedTickPayload::write, BloodBleedTickPayload::new);

    public BloodBleedTickPayload(PacketByteBuf buf) {
        this(buf.readDouble(), buf.readDouble(), buf.readDouble());
    }

    public void write(PacketByteBuf buf) {
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
    }

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
