package com.yourname.loopypowers.network.payload;

import com.yourname.loopypowers.Loopypowers;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record CosmicDetonateTickPayload(int entityId) implements CustomPayload {

    public static final Id<CosmicDetonateTickPayload> ID =
            new Id<>(Identifier.of(Loopypowers.MOD_ID, "cosmic_detonate_tick"));

    public static final PacketCodec<PacketByteBuf, CosmicDetonateTickPayload> CODEC =
            PacketCodec.of(CosmicDetonateTickPayload::write, CosmicDetonateTickPayload::new);

    public CosmicDetonateTickPayload(PacketByteBuf buf) {
        this(buf.readInt());
    }

    public void write(PacketByteBuf buf) {
        buf.writeInt(entityId);
    }

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
