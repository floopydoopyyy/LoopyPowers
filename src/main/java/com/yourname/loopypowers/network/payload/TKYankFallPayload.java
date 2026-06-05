package com.yourname.loopypowers.network.payload;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record TKYankFallPayload(double x, double y, double z) implements CustomPayload {
    public static final Id<TKYankFallPayload> ID = new Id<>(Identifier.of("loopypowers", "tk_yank_fall"));
    public static final PacketCodec<RegistryByteBuf, TKYankFallPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.DOUBLE, TKYankFallPayload::x,
            PacketCodecs.DOUBLE, TKYankFallPayload::y,
            PacketCodecs.DOUBLE, TKYankFallPayload::z,
            TKYankFallPayload::new
    );
    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
