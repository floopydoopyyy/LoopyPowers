package com.yourname.loopypowers.network.payload;

import com.yourname.loopypowers.Loopypowers;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record LightningStormTargetAuraPayload(int entityId) implements CustomPayload {

    public static final Id<LightningStormTargetAuraPayload> ID =
            new Id<>(Identifier.of(Loopypowers.MOD_ID, "lightning_storm_target_aura"));

    public static final PacketCodec<PacketByteBuf, LightningStormTargetAuraPayload> CODEC =
            PacketCodec.of(LightningStormTargetAuraPayload::write, LightningStormTargetAuraPayload::new);

    public LightningStormTargetAuraPayload(PacketByteBuf buf) { this(buf.readInt()); }
    public void write(PacketByteBuf buf) { buf.writeInt(entityId); }

    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
