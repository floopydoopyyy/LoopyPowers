package com.yourname.loopypowers.network.payload;

import com.yourname.loopypowers.Loopypowers;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record FlightBoomWindupPayload(double x, double y, double z,
                                      double lookX, double lookY, double lookZ,
                                      boolean isStart) implements CustomPayload {

    public static final Id<FlightBoomWindupPayload> ID =
            new Id<>(Identifier.of(Loopypowers.MOD_ID, "flight_boom_windup"));

    public static final PacketCodec<PacketByteBuf, FlightBoomWindupPayload> CODEC =
            PacketCodec.of(FlightBoomWindupPayload::write, FlightBoomWindupPayload::new);

    public FlightBoomWindupPayload(PacketByteBuf buf) {
        this(buf.readDouble(), buf.readDouble(), buf.readDouble(),
             buf.readDouble(), buf.readDouble(), buf.readDouble(),
             buf.readBoolean());
    }

    public void write(PacketByteBuf buf) {
        buf.writeDouble(x); buf.writeDouble(y); buf.writeDouble(z);
        buf.writeDouble(lookX); buf.writeDouble(lookY); buf.writeDouble(lookZ);
        buf.writeBoolean(isStart);
    }

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
