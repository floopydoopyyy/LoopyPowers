package com.yourname.loopypowers.network.payload;

import com.yourname.loopypowers.Loopypowers;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record LightningHitBasicPayload(int entityId) implements CustomPayload {

    public static final Id<LightningHitBasicPayload> ID =
            new Id<>(Identifier.of(Loopypowers.MOD_ID, "lightning_hit_basic"));

    public static final PacketCodec<PacketByteBuf, LightningHitBasicPayload> CODEC =
            PacketCodec.of(LightningHitBasicPayload::write, LightningHitBasicPayload::new);

    public LightningHitBasicPayload(PacketByteBuf buf) { this(buf.readInt()); }
    public void write(PacketByteBuf buf) { buf.writeInt(entityId); }

    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
