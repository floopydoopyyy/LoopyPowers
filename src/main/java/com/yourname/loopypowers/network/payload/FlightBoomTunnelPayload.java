package com.yourname.loopypowers.network.payload;

import com.yourname.loopypowers.Loopypowers;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Player position (+1 Y) and dash direction for reproducing the 3-point cloud tunnel. */
public record FlightBoomTunnelPayload(
        double x, double y, double z,
        double dirX, double dirY, double dirZ
) implements CustomPayload {

    public static final Id<FlightBoomTunnelPayload> ID =
            new Id<>(Identifier.of(Loopypowers.MOD_ID, "flight_boom_tunnel"));

    public static final PacketCodec<PacketByteBuf, FlightBoomTunnelPayload> CODEC =
            PacketCodec.of(FlightBoomTunnelPayload::write, FlightBoomTunnelPayload::new);

    public FlightBoomTunnelPayload(PacketByteBuf buf) {
        this(buf.readDouble(), buf.readDouble(), buf.readDouble(),
             buf.readDouble(), buf.readDouble(), buf.readDouble());
    }

    public void write(PacketByteBuf buf) {
        buf.writeDouble(x); buf.writeDouble(y); buf.writeDouble(z);
        buf.writeDouble(dirX); buf.writeDouble(dirY); buf.writeDouble(dirZ);
    }

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
