package com.yourname.loopypowers.network.payload;

import com.yourname.loopypowers.Loopypowers;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record SpeedDashPayload(
        double x, double y, double z,
        double lookX, double lookY, double lookZ
) implements CustomPayload {

    public static final Id<SpeedDashPayload> ID =
            new Id<>(Identifier.of(Loopypowers.MOD_ID, "speed_dash"));

    public static final PacketCodec<PacketByteBuf, SpeedDashPayload> CODEC =
            PacketCodec.of(SpeedDashPayload::write, SpeedDashPayload::new);

    public SpeedDashPayload(PacketByteBuf buf) {
        this(buf.readDouble(), buf.readDouble(), buf.readDouble(),
             buf.readDouble(), buf.readDouble(), buf.readDouble());
    }
    public void write(PacketByteBuf buf) {
        buf.writeDouble(x); buf.writeDouble(y); buf.writeDouble(z);
        buf.writeDouble(lookX); buf.writeDouble(lookY); buf.writeDouble(lookZ);
    }

    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
