package com.yourname.loopypowers.network.payload;

import com.yourname.loopypowers.Loopypowers;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record SoundBassFinalRemotePayload(int entityId) implements CustomPayload {

    public static final Id<SoundBassFinalRemotePayload> ID =
            new Id<>(Identifier.of(Loopypowers.MOD_ID, "sound_bass_final_remote"));

    public static final PacketCodec<PacketByteBuf, SoundBassFinalRemotePayload> CODEC =
            PacketCodec.of(SoundBassFinalRemotePayload::write, SoundBassFinalRemotePayload::new);

    public SoundBassFinalRemotePayload(PacketByteBuf buf) { this(buf.readInt()); }
    public void write(PacketByteBuf buf) { buf.writeInt(entityId); }

    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
