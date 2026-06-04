package com.yourname.loopypowers.network.payload;

import com.yourname.loopypowers.Loopypowers;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record TKPassiveHitPayload(int entityId) implements CustomPayload {

    public static final Id<TKPassiveHitPayload> ID =
            new Id<>(Identifier.of(Loopypowers.MOD_ID, "tk_passive_hit"));

    public static final PacketCodec<PacketByteBuf, TKPassiveHitPayload> CODEC =
            PacketCodec.of(TKPassiveHitPayload::write, TKPassiveHitPayload::new);

    public TKPassiveHitPayload(PacketByteBuf buf) { this(buf.readInt()); }
    public void write(PacketByteBuf buf) { buf.writeInt(entityId); }

    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
