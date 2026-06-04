package com.yourname.loopypowers.network.payload;

import com.yourname.loopypowers.Loopypowers;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record PsychicSpikeImpactPayload(int entityId) implements CustomPayload {

    public static final Id<PsychicSpikeImpactPayload> ID =
            new Id<>(Identifier.of(Loopypowers.MOD_ID, "psychic_spike_impact"));

    public static final PacketCodec<PacketByteBuf, PsychicSpikeImpactPayload> CODEC =
            PacketCodec.of(PsychicSpikeImpactPayload::write, PsychicSpikeImpactPayload::new);

    public PsychicSpikeImpactPayload(PacketByteBuf buf) {
        this(buf.readInt());
    }

    public void write(PacketByteBuf buf) {
        buf.writeInt(entityId);
    }

    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
