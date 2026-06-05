package com.yourname.loopypowers.network.payload;

import com.yourname.loopypowers.Loopypowers;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Sent every tick while the dash or pinball travel is active. */
public record SpeedDashTrailPayload(
        double x, double y, double z,
        double vx, double vy, double vz,
        long gameTime
) implements CustomPayload {

    public static final Id<SpeedDashTrailPayload> ID =
            new Id<>(Identifier.of(Loopypowers.MOD_ID, "speed_dash_trail"));

    public static final PacketCodec<PacketByteBuf, SpeedDashTrailPayload> CODEC =
            PacketCodec.of(SpeedDashTrailPayload::write, SpeedDashTrailPayload::new);

    public SpeedDashTrailPayload(PacketByteBuf buf) {
        this(buf.readDouble(), buf.readDouble(), buf.readDouble(),
             buf.readDouble(), buf.readDouble(), buf.readDouble(),
             buf.readLong());
    }

    public void write(PacketByteBuf buf) {
        buf.writeDouble(x); buf.writeDouble(y); buf.writeDouble(z);
        buf.writeDouble(vx); buf.writeDouble(vy); buf.writeDouble(vz);
        buf.writeLong(gameTime);
    }

    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
