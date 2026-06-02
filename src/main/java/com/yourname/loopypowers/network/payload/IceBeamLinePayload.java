package com.yourname.loopypowers.network.payload;

import com.yourname.loopypowers.Loopypowers;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record IceBeamLinePayload(
        double sx, double sy, double sz,
        double ex, double ey, double ez,
        long time
) implements CustomPayload {

    public static final Id<IceBeamLinePayload> ID =
            new Id<>(Identifier.of(Loopypowers.MOD_ID, "ice_beam_line"));

    public static final PacketCodec<PacketByteBuf, IceBeamLinePayload> CODEC =
            PacketCodec.of(IceBeamLinePayload::write, IceBeamLinePayload::new);

    public IceBeamLinePayload(PacketByteBuf buf) {
        this(buf.readDouble(), buf.readDouble(), buf.readDouble(),
             buf.readDouble(), buf.readDouble(), buf.readDouble(),
             buf.readLong());
    }

    public void write(PacketByteBuf buf) {
        buf.writeDouble(sx); buf.writeDouble(sy); buf.writeDouble(sz);
        buf.writeDouble(ex); buf.writeDouble(ey); buf.writeDouble(ez);
        buf.writeLong(time);
    }

    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
