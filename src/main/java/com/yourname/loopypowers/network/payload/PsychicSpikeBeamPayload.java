package com.yourname.loopypowers.network.payload;

import com.yourname.loopypowers.Loopypowers;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record PsychicSpikeBeamPayload(
        double fromX, double fromY, double fromZ,
        double toX, double toY, double toZ,
        int segments, float offset
) implements CustomPayload {

    public static final Id<PsychicSpikeBeamPayload> ID =
            new Id<>(Identifier.of(Loopypowers.MOD_ID, "psychic_spike_beam"));

    public static final PacketCodec<PacketByteBuf, PsychicSpikeBeamPayload> CODEC =
            PacketCodec.of(PsychicSpikeBeamPayload::write, PsychicSpikeBeamPayload::new);

    public PsychicSpikeBeamPayload(PacketByteBuf buf) {
        this(buf.readDouble(), buf.readDouble(), buf.readDouble(),
             buf.readDouble(), buf.readDouble(), buf.readDouble(),
             buf.readInt(), buf.readFloat());
    }

    public void write(PacketByteBuf buf) {
        buf.writeDouble(fromX); buf.writeDouble(fromY); buf.writeDouble(fromZ);
        buf.writeDouble(toX); buf.writeDouble(toY); buf.writeDouble(toZ);
        buf.writeInt(segments); buf.writeFloat(offset);
    }

    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
