package com.yourname.loopypowers.network.payload;

import com.yourname.loopypowers.Loopypowers;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record FireUltChargePayload(double x, double y, double z, float progress, boolean nearEnd)
        implements CustomPayload {

    public static final Id<FireUltChargePayload> ID =
            new Id<>(Identifier.of(Loopypowers.MOD_ID, "fire_ult_charge"));

    public static final PacketCodec<PacketByteBuf, FireUltChargePayload> CODEC =
            PacketCodec.of(FireUltChargePayload::write, FireUltChargePayload::new);

    public FireUltChargePayload(PacketByteBuf buf) {
        this(buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readFloat(), buf.readBoolean());
    }

    public void write(PacketByteBuf buf) {
        buf.writeDouble(x); buf.writeDouble(y); buf.writeDouble(z);
        buf.writeFloat(progress); buf.writeBoolean(nearEnd);
    }

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
