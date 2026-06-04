package com.yourname.loopypowers.network.payload;

import com.yourname.loopypowers.Loopypowers;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record SoundResonanceBurstPayload(int entityId) implements CustomPayload {

    public static final Id<SoundResonanceBurstPayload> ID =
            new Id<>(Identifier.of(Loopypowers.MOD_ID, "sound_resonance_burst"));

    public static final PacketCodec<PacketByteBuf, SoundResonanceBurstPayload> CODEC =
            PacketCodec.of(SoundResonanceBurstPayload::write, SoundResonanceBurstPayload::new);

    public SoundResonanceBurstPayload(PacketByteBuf buf) { this(buf.readInt()); }
    public void write(PacketByteBuf buf) { buf.writeInt(entityId); }

    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
