package com.yourname.loopypowers.network.payload;

import com.yourname.loopypowers.Loopypowers;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record PsychicSpikeAuraPayload(int entityId) implements CustomPayload {

    public static final Id<PsychicSpikeAuraPayload> ID =
            new Id<>(Identifier.of(Loopypowers.MOD_ID, "psychic_spike_aura"));

    public static final PacketCodec<PacketByteBuf, PsychicSpikeAuraPayload> CODEC =
            PacketCodec.of(PsychicSpikeAuraPayload::write, PsychicSpikeAuraPayload::new);

    public PsychicSpikeAuraPayload(PacketByteBuf buf) {
        this(buf.readInt());
    }

    public void write(PacketByteBuf buf) {
        buf.writeInt(entityId);
    }

    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
