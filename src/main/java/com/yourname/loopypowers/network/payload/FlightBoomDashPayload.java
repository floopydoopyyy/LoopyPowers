package com.yourname.loopypowers.network.payload;

import com.yourname.loopypowers.Loopypowers;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record FlightBoomDashPayload(double x, double y, double z,
                                    double dirX, double dirY, double dirZ,
                                    boolean isStart) implements CustomPayload {

    public static final Id<FlightBoomDashPayload> ID =
            new Id<>(Identifier.of(Loopypowers.MOD_ID, "flight_boom_dash"));

    public static final PacketCodec<PacketByteBuf, FlightBoomDashPayload> CODEC =
            PacketCodec.of(FlightBoomDashPayload::write, FlightBoomDashPayload::new);

    public FlightBoomDashPayload(PacketByteBuf buf) {
        this(buf.readDouble(), buf.readDouble(), buf.readDouble(),
             buf.readDouble(), buf.readDouble(), buf.readDouble(),
             buf.readBoolean());
    }

    public void write(PacketByteBuf buf) {
        buf.writeDouble(x); buf.writeDouble(y); buf.writeDouble(z);
        buf.writeDouble(dirX); buf.writeDouble(dirY); buf.writeDouble(dirZ);
        buf.writeBoolean(isStart);
    }

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
