package com.yourname.loopypowers.network.payload;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record NatureCageFxPayload(int cx, int cy, int cz, int radius, int thickness) implements CustomPayload {
    public static final Id<NatureCageFxPayload> ID = new Id<>(Identifier.of("loopypowers", "nature_cage_fx"));
    public static final PacketCodec<RegistryByteBuf, NatureCageFxPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.INTEGER, NatureCageFxPayload::cx,
            PacketCodecs.INTEGER, NatureCageFxPayload::cy,
            PacketCodecs.INTEGER, NatureCageFxPayload::cz,
            PacketCodecs.INTEGER, NatureCageFxPayload::radius,
            PacketCodecs.INTEGER, NatureCageFxPayload::thickness,
            NatureCageFxPayload::new
    );
    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
