package com.yourname.loopypowers.network.payload;

import com.yourname.loopypowers.Loopypowers;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record StrengthRushEntityHitPayload(int entityId) implements CustomPayload {

    public static final Id<StrengthRushEntityHitPayload> ID =
            new Id<>(Identifier.of(Loopypowers.MOD_ID, "strength_rush_entity_hit"));

    public static final PacketCodec<PacketByteBuf, StrengthRushEntityHitPayload> CODEC =
            PacketCodec.of(StrengthRushEntityHitPayload::write, StrengthRushEntityHitPayload::new);

    public StrengthRushEntityHitPayload(PacketByteBuf buf) { this(buf.readInt()); }
    public void write(PacketByteBuf buf) { buf.writeInt(entityId); }

    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
