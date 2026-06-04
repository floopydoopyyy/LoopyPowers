package com.yourname.loopypowers.network.payload;

import com.yourname.loopypowers.Loopypowers;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record SoundBassPullHitPayload(int entityId) implements CustomPayload {

    public static final Id<SoundBassPullHitPayload> ID =
            new Id<>(Identifier.of(Loopypowers.MOD_ID, "sound_bass_pull_hit"));

    public static final PacketCodec<PacketByteBuf, SoundBassPullHitPayload> CODEC =
            PacketCodec.of(SoundBassPullHitPayload::write, SoundBassPullHitPayload::new);

    public SoundBassPullHitPayload(PacketByteBuf buf) { this(buf.readInt()); }
    public void write(PacketByteBuf buf) { buf.writeInt(entityId); }

    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
