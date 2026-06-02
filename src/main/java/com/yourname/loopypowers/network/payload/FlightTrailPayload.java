package com.yourname.loopypowers.network.payload;

import com.yourname.loopypowers.Loopypowers;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Pre-computed cloud trail position during elytra flight. */
public record FlightTrailPayload(double x, double y, double z) implements CustomPayload {

    public static final Id<FlightTrailPayload> ID =
            new Id<>(Identifier.of(Loopypowers.MOD_ID, "flight_trail"));

    public static final PacketCodec<PacketByteBuf, FlightTrailPayload> CODEC =
            PacketCodec.of(FlightTrailPayload::write, FlightTrailPayload::new);

    public FlightTrailPayload(PacketByteBuf buf) {
        this(buf.readDouble(), buf.readDouble(), buf.readDouble());
    }

    public void write(PacketByteBuf buf) {
        buf.writeDouble(x); buf.writeDouble(y); buf.writeDouble(z);
    }

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
