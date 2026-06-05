package com.yourname.loopypowers.network.payload;

import com.yourname.loopypowers.Loopypowers;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Fired once per dash cast (and once per pinball launch). */
public record SpeedDashCastPayload(
        double x, double y, double z,
        double lookX, double lookY, double lookZ
) implements CustomPayload {

    public static final Id<SpeedDashCastPayload> ID =
            new Id<>(Identifier.of(Loopypowers.MOD_ID, "speed_dash_cast"));

    public static final PacketCodec<PacketByteBuf, SpeedDashCastPayload> CODEC =
            PacketCodec.of(SpeedDashCastPayload::write, SpeedDashCastPayload::new);

    public SpeedDashCastPayload(PacketByteBuf buf) {
        this(buf.readDouble(), buf.readDouble(), buf.readDouble(),
             buf.readDouble(), buf.readDouble(), buf.readDouble());
    }

    public void write(PacketByteBuf buf) {
        buf.writeDouble(x); buf.writeDouble(y); buf.writeDouble(z);
        buf.writeDouble(lookX); buf.writeDouble(lookY); buf.writeDouble(lookZ);
    }

    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
