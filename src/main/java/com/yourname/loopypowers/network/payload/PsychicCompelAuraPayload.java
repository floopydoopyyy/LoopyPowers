package com.yourname.loopypowers.network.payload;

import com.yourname.loopypowers.Loopypowers;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record PsychicCompelAuraPayload(int entityId) implements CustomPayload {

    public static final Id<PsychicCompelAuraPayload> ID =
            new Id<>(Identifier.of(Loopypowers.MOD_ID, "psychic_compel_aura"));

    public static final PacketCodec<PacketByteBuf, PsychicCompelAuraPayload> CODEC =
            PacketCodec.of(PsychicCompelAuraPayload::write, PsychicCompelAuraPayload::new);

    public PsychicCompelAuraPayload(PacketByteBuf buf) {
        this(buf.readInt());
    }

    public void write(PacketByteBuf buf) {
        buf.writeInt(entityId);
    }

    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
