package com.yourname.loopypowers.network.payload;

import com.yourname.loopypowers.Loopypowers;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record BloodBindDamageFxPayload(int casterId, int targetId, float intensity) implements CustomPayload {

    public static final Id<BloodBindDamageFxPayload> ID =
            new Id<>(Identifier.of(Loopypowers.MOD_ID, "blood_bind_damage_fx"));

    public static final PacketCodec<PacketByteBuf, BloodBindDamageFxPayload> CODEC =
            PacketCodec.of(BloodBindDamageFxPayload::write, BloodBindDamageFxPayload::new);

    public BloodBindDamageFxPayload(PacketByteBuf buf) {
        this(buf.readInt(), buf.readInt(), buf.readFloat());
    }

    public void write(PacketByteBuf buf) {
        buf.writeInt(casterId);
        buf.writeInt(targetId);
        buf.writeFloat(intensity);
    }

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
