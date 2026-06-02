package com.yourname.loopypowers.network.payload;

import com.yourname.loopypowers.Loopypowers;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record LightningChainHitPayload(int entityId) implements CustomPayload {

    public static final Id<LightningChainHitPayload> ID =
            new Id<>(Identifier.of(Loopypowers.MOD_ID, "lightning_chain_hit"));

    public static final PacketCodec<PacketByteBuf, LightningChainHitPayload> CODEC =
            PacketCodec.of(LightningChainHitPayload::write, LightningChainHitPayload::new);

    public LightningChainHitPayload(PacketByteBuf buf) { this(buf.readInt()); }
    public void write(PacketByteBuf buf) { buf.writeInt(entityId); }

    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
