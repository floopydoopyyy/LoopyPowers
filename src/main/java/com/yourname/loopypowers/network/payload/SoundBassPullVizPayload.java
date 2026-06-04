package com.yourname.loopypowers.network.payload;

import com.yourname.loopypowers.Loopypowers;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record SoundBassPullVizPayload(double x, double y, double z, float radius, boolean innerShell) implements CustomPayload {

    public static final Id<SoundBassPullVizPayload> ID =
            new Id<>(Identifier.of(Loopypowers.MOD_ID, "sound_bass_pull_viz"));

    public static final PacketCodec<PacketByteBuf, SoundBassPullVizPayload> CODEC =
            PacketCodec.of(SoundBassPullVizPayload::write, SoundBassPullVizPayload::new);

    public SoundBassPullVizPayload(PacketByteBuf buf) {
        this(buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readFloat(), buf.readBoolean());
    }
    public void write(PacketByteBuf buf) {
        buf.writeDouble(x); buf.writeDouble(y); buf.writeDouble(z);
        buf.writeFloat(radius); buf.writeBoolean(innerShell);
    }

    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
