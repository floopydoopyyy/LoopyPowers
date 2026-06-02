package com.yourname.loopypowers.network.payload;

import com.yourname.loopypowers.Loopypowers;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

// Includes the hit ring — both always fire together on charged hits
public record LightningHitTierPayload(int entityId, int tier) implements CustomPayload {

    public static final Id<LightningHitTierPayload> ID =
            new Id<>(Identifier.of(Loopypowers.MOD_ID, "lightning_hit_tier"));

    public static final PacketCodec<PacketByteBuf, LightningHitTierPayload> CODEC =
            PacketCodec.of(LightningHitTierPayload::write, LightningHitTierPayload::new);

    public LightningHitTierPayload(PacketByteBuf buf) { this(buf.readInt(), buf.readInt()); }
    public void write(PacketByteBuf buf) { buf.writeInt(entityId); buf.writeInt(tier); }

    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
