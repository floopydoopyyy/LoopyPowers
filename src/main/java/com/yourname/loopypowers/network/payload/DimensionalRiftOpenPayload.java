package com.yourname.loopypowers.network.payload;

import com.yourname.loopypowers.Loopypowers;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Sent once when the ultimate fires. The client uses rngSeed to regenerate
 * the same crack/ring geometry as the server, stores it, and spawns the
 * opening burst particles.
 */
public record DimensionalRiftOpenPayload(int casterId, double ox, double oy, double oz, long rngSeed)
        implements CustomPayload {

    public static final Id<DimensionalRiftOpenPayload> ID =
            new Id<>(Identifier.of(Loopypowers.MOD_ID, "dimensional_rift_open"));

    public static final PacketCodec<PacketByteBuf, DimensionalRiftOpenPayload> CODEC =
            PacketCodec.of(DimensionalRiftOpenPayload::write, DimensionalRiftOpenPayload::new);

    public DimensionalRiftOpenPayload(PacketByteBuf buf) {
        this(buf.readInt(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readLong());
    }

    public void write(PacketByteBuf buf) {
        buf.writeInt(casterId);
        buf.writeDouble(ox); buf.writeDouble(oy); buf.writeDouble(oz);
        buf.writeLong(rngSeed);
    }

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
