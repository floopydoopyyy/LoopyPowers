package com.yourname.loopypowers.network.payload;

import com.yourname.loopypowers.Loopypowers;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record PsychicLeechPayload(int attackerId, int targetId) implements CustomPayload {

    public static final Id<PsychicLeechPayload> ID =
            new Id<>(Identifier.of(Loopypowers.MOD_ID, "psychic_leech"));

    public static final PacketCodec<PacketByteBuf, PsychicLeechPayload> CODEC =
            PacketCodec.of(PsychicLeechPayload::write, PsychicLeechPayload::new);

    public PsychicLeechPayload(PacketByteBuf buf) {
        this(buf.readInt(), buf.readInt());
    }

    public void write(PacketByteBuf buf) {
        buf.writeInt(attackerId); buf.writeInt(targetId);
    }

    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
