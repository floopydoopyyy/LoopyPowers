package com.yourname.loopypowers.network.payload;

import com.yourname.loopypowers.Loopypowers;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record DimensionalDisplaceFxPayload(double x, double y, double z) implements CustomPayload {

    public static final Id<DimensionalDisplaceFxPayload> ID =
            new Id<>(Identifier.of(Loopypowers.MOD_ID, "dimensional_displace_fx"));

    public static final PacketCodec<PacketByteBuf, DimensionalDisplaceFxPayload> CODEC =
            PacketCodec.of(DimensionalDisplaceFxPayload::write, DimensionalDisplaceFxPayload::new);

    public DimensionalDisplaceFxPayload(PacketByteBuf buf) {
        this(buf.readDouble(), buf.readDouble(), buf.readDouble());
    }

    public void write(PacketByteBuf buf) {
        buf.writeDouble(x); buf.writeDouble(y); buf.writeDouble(z);
    }

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
