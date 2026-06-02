package com.yourname.loopypowers.network.payload;

import com.yourname.loopypowers.Loopypowers;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record LightningClapHitPayload(int entityId, float t) implements CustomPayload {

    public static final Id<LightningClapHitPayload> ID =
            new Id<>(Identifier.of(Loopypowers.MOD_ID, "lightning_clap_hit"));

    public static final PacketCodec<PacketByteBuf, LightningClapHitPayload> CODEC =
            PacketCodec.of(LightningClapHitPayload::write, LightningClapHitPayload::new);

    public LightningClapHitPayload(PacketByteBuf buf) { this(buf.readInt(), buf.readFloat()); }
    public void write(PacketByteBuf buf) { buf.writeInt(entityId); buf.writeFloat(t); }

    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
