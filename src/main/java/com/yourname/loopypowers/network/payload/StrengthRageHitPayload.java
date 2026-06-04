package com.yourname.loopypowers.network.payload;

import com.yourname.loopypowers.Loopypowers;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record StrengthRageHitPayload(int entityId) implements CustomPayload {

    public static final Id<StrengthRageHitPayload> ID =
            new Id<>(Identifier.of(Loopypowers.MOD_ID, "strength_rage_hit"));

    public static final PacketCodec<PacketByteBuf, StrengthRageHitPayload> CODEC =
            PacketCodec.of(StrengthRageHitPayload::write, StrengthRageHitPayload::new);

    public StrengthRageHitPayload(PacketByteBuf buf) { this(buf.readInt()); }
    public void write(PacketByteBuf buf) { buf.writeInt(entityId); }

    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
