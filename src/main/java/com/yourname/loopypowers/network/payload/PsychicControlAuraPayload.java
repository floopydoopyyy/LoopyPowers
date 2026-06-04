package com.yourname.loopypowers.network.payload;

import com.yourname.loopypowers.Loopypowers;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record PsychicControlAuraPayload(int entityId) implements CustomPayload {

    public static final Id<PsychicControlAuraPayload> ID =
            new Id<>(Identifier.of(Loopypowers.MOD_ID, "psychic_control_aura"));

    public static final PacketCodec<PacketByteBuf, PsychicControlAuraPayload> CODEC =
            PacketCodec.of(PsychicControlAuraPayload::write, PsychicControlAuraPayload::new);

    public PsychicControlAuraPayload(PacketByteBuf buf) {
        this(buf.readInt());
    }

    public void write(PacketByteBuf buf) {
        buf.writeInt(entityId);
    }

    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
