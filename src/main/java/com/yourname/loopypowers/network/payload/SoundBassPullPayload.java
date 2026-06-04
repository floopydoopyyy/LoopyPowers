package com.yourname.loopypowers.network.payload;

import com.yourname.loopypowers.Loopypowers;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record SoundBassPullPayload(double x, double y, double z) implements CustomPayload {

    public static final Id<SoundBassPullPayload> ID =
            new Id<>(Identifier.of(Loopypowers.MOD_ID, "sound_bass_pull"));

    public static final PacketCodec<PacketByteBuf, SoundBassPullPayload> CODEC =
            PacketCodec.of(SoundBassPullPayload::write, SoundBassPullPayload::new);

    public SoundBassPullPayload(PacketByteBuf buf) {
        this(buf.readDouble(), buf.readDouble(), buf.readDouble());
    }
    public void write(PacketByteBuf buf) {
        buf.writeDouble(x); buf.writeDouble(y); buf.writeDouble(z);
    }

    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
