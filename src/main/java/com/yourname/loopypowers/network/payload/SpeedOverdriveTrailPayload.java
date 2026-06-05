package com.yourname.loopypowers.network.payload;

import com.yourname.loopypowers.Loopypowers;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record SpeedOverdriveTrailPayload(
        double x, double y, double z,
        double velX, double velY, double velZ,
        long gameTime
) implements CustomPayload {

    public static final Id<SpeedOverdriveTrailPayload> ID =
            new Id<>(Identifier.of(Loopypowers.MOD_ID, "speed_overdrive_trail"));

    public static final PacketCodec<PacketByteBuf, SpeedOverdriveTrailPayload> CODEC =
            PacketCodec.of(SpeedOverdriveTrailPayload::write, SpeedOverdriveTrailPayload::new);

    public SpeedOverdriveTrailPayload(PacketByteBuf buf) {
        this(buf.readDouble(), buf.readDouble(), buf.readDouble(),
             buf.readDouble(), buf.readDouble(), buf.readDouble(),
             buf.readLong());
    }

    public void write(PacketByteBuf buf) {
        buf.writeDouble(x); buf.writeDouble(y); buf.writeDouble(z);
        buf.writeDouble(velX); buf.writeDouble(velY); buf.writeDouble(velZ);
        buf.writeLong(gameTime);
    }

    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
