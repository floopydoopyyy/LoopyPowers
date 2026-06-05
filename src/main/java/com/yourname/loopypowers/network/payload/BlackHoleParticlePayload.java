package com.yourname.loopypowers.network.payload;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record BlackHoleParticlePayload(int entityId, float lifeProgress) implements CustomPayload {
    public static final Id<BlackHoleParticlePayload> ID = new Id<>(Identifier.of("loopypowers", "bh_particles"));
    public static final PacketCodec<RegistryByteBuf, BlackHoleParticlePayload> CODEC = PacketCodec.tuple(
            PacketCodecs.INTEGER, BlackHoleParticlePayload::entityId,
            PacketCodecs.FLOAT, BlackHoleParticlePayload::lifeProgress,
            BlackHoleParticlePayload::new
    );
    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
