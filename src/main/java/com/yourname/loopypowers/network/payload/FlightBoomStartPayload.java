package com.yourname.loopypowers.network.payload;

import com.yourname.loopypowers.Loopypowers;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record FlightBoomStartPayload(double x, double y, double z) implements CustomPayload {

    public static final Id<FlightBoomStartPayload> ID =
            new Id<>(Identifier.of(Loopypowers.MOD_ID, "flight_boom_start"));

    public static final PacketCodec<PacketByteBuf, FlightBoomStartPayload> CODEC =
            PacketCodec.of(FlightBoomStartPayload::write, FlightBoomStartPayload::new);

    public FlightBoomStartPayload(PacketByteBuf buf) {
        this(buf.readDouble(), buf.readDouble(), buf.readDouble());
    }

    public void write(PacketByteBuf buf) {
        buf.writeDouble(x); buf.writeDouble(y); buf.writeDouble(z);
    }

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
