package com.yourname.loopypowers.network.payload;

import com.yourname.loopypowers.Loopypowers;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record IceSpikeTrailPayload(
        double sx, double sz, double ex, double ez,
        float progress, int seed, int yHint
) implements CustomPayload {

    public static final Id<IceSpikeTrailPayload> ID =
            new Id<>(Identifier.of(Loopypowers.MOD_ID, "ice_spike_trail"));

    public static final PacketCodec<PacketByteBuf, IceSpikeTrailPayload> CODEC =
            PacketCodec.of(IceSpikeTrailPayload::write, IceSpikeTrailPayload::new);

    public IceSpikeTrailPayload(PacketByteBuf buf) {
        this(buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(),
             buf.readFloat(), buf.readInt(), buf.readInt());
    }

    public void write(PacketByteBuf buf) {
        buf.writeDouble(sx); buf.writeDouble(sz); buf.writeDouble(ex); buf.writeDouble(ez);
        buf.writeFloat(progress); buf.writeInt(seed); buf.writeInt(yHint);
    }

    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
