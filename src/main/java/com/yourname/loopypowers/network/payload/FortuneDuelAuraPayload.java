package com.yourname.loopypowers.network.payload;

import com.yourname.loopypowers.Loopypowers;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record FortuneDuelAuraPayload(int entityId) implements CustomPayload {

    public static final Id<FortuneDuelAuraPayload> ID =
            new Id<>(Identifier.of(Loopypowers.MOD_ID, "fortune_duel_aura"));

    public static final PacketCodec<PacketByteBuf, FortuneDuelAuraPayload> CODEC =
            PacketCodec.of(FortuneDuelAuraPayload::write, FortuneDuelAuraPayload::new);

    public FortuneDuelAuraPayload(PacketByteBuf buf) {
        this(buf.readInt());
    }

    public void write(PacketByteBuf buf) {
        buf.writeInt(entityId);
    }

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
