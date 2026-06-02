package com.yourname.loopypowers.network.payload;

import com.yourname.loopypowers.Loopypowers;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Sent every ULT_PARTICLE_INTERVAL ticks. Client uses stored geometry to spawn crack-wall + aura particles. */
public record DimensionalRiftTickPayload(int casterId) implements CustomPayload {

    public static final Id<DimensionalRiftTickPayload> ID =
            new Id<>(Identifier.of(Loopypowers.MOD_ID, "dimensional_rift_tick"));

    public static final PacketCodec<PacketByteBuf, DimensionalRiftTickPayload> CODEC =
            PacketCodec.of(DimensionalRiftTickPayload::write, DimensionalRiftTickPayload::new);

    public DimensionalRiftTickPayload(PacketByteBuf buf) {
        this(buf.readInt());
    }

    public void write(PacketByteBuf buf) {
        buf.writeInt(casterId);
    }

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
