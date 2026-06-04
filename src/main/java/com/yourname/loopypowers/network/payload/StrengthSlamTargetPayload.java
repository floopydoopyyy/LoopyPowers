package com.yourname.loopypowers.network.payload;

import com.yourname.loopypowers.Loopypowers;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record StrengthSlamTargetPayload(int entityId) implements CustomPayload {

    public static final Id<StrengthSlamTargetPayload> ID =
            new Id<>(Identifier.of(Loopypowers.MOD_ID, "strength_slam_target"));

    public static final PacketCodec<PacketByteBuf, StrengthSlamTargetPayload> CODEC =
            PacketCodec.of(StrengthSlamTargetPayload::write, StrengthSlamTargetPayload::new);

    public StrengthSlamTargetPayload(PacketByteBuf buf) { this(buf.readInt()); }
    public void write(PacketByteBuf buf) { buf.writeInt(entityId); }

    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
