package com.yourname.loopypowers.network.payload;

import com.yourname.loopypowers.Loopypowers;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record SoundBurstHitPayload(int entityId) implements CustomPayload {

    public static final Id<SoundBurstHitPayload> ID =
            new Id<>(Identifier.of(Loopypowers.MOD_ID, "sound_burst_hit"));

    public static final PacketCodec<PacketByteBuf, SoundBurstHitPayload> CODEC =
            PacketCodec.of(SoundBurstHitPayload::write, SoundBurstHitPayload::new);

    public SoundBurstHitPayload(PacketByteBuf buf) { this(buf.readInt()); }
    public void write(PacketByteBuf buf) { buf.writeInt(entityId); }

    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
