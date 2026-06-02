package com.yourname.loopypowers.network.payload;

import com.yourname.loopypowers.Loopypowers;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record LightningTargetConfettiPayload(int entityId, float t) implements CustomPayload {

    public static final Id<LightningTargetConfettiPayload> ID =
            new Id<>(Identifier.of(Loopypowers.MOD_ID, "lightning_target_confetti"));

    public static final PacketCodec<PacketByteBuf, LightningTargetConfettiPayload> CODEC =
            PacketCodec.of(LightningTargetConfettiPayload::write, LightningTargetConfettiPayload::new);

    public LightningTargetConfettiPayload(PacketByteBuf buf) { this(buf.readInt(), buf.readFloat()); }
    public void write(PacketByteBuf buf) { buf.writeInt(entityId); buf.writeFloat(t); }

    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
