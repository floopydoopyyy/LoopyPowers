package com.yourname.loopypowers.network.payload;

import com.yourname.loopypowers.Loopypowers;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record SoundBassBlastVizPayload(double x, double y, double z, int step, long seed) implements CustomPayload {

    public static final Id<SoundBassBlastVizPayload> ID =
            new Id<>(Identifier.of(Loopypowers.MOD_ID, "sound_bass_blast_viz"));

    public static final PacketCodec<PacketByteBuf, SoundBassBlastVizPayload> CODEC =
            PacketCodec.of(SoundBassBlastVizPayload::write, SoundBassBlastVizPayload::new);

    public SoundBassBlastVizPayload(PacketByteBuf buf) {
        this(buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readInt(), buf.readLong());
    }
    public void write(PacketByteBuf buf) {
        buf.writeDouble(x); buf.writeDouble(y); buf.writeDouble(z);
        buf.writeInt(step); buf.writeLong(seed);
    }

    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
