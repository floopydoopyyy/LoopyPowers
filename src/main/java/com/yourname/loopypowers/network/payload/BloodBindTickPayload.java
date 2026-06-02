package com.yourname.loopypowers.network.payload;

import com.yourname.loopypowers.Loopypowers;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record BloodBindTickPayload(int casterId, int targetId, float strain) implements CustomPayload {

    public static final Id<BloodBindTickPayload> ID =
            new Id<>(Identifier.of(Loopypowers.MOD_ID, "blood_bind_tick"));

    public static final PacketCodec<PacketByteBuf, BloodBindTickPayload> CODEC =
            PacketCodec.of(BloodBindTickPayload::write, BloodBindTickPayload::new);

    public BloodBindTickPayload(PacketByteBuf buf) {
        this(buf.readInt(), buf.readInt(), buf.readFloat());
    }

    public void write(PacketByteBuf buf) {
        buf.writeInt(casterId);
        buf.writeInt(targetId);
        buf.writeFloat(strain);
    }

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
