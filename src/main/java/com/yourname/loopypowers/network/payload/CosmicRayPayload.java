package com.yourname.loopypowers.network.payload;

import com.yourname.loopypowers.Loopypowers;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record CosmicRayPayload(
        double startX, double startY, double startZ,
        double endX, double endY, double endZ
) implements CustomPayload {

    public static final Id<CosmicRayPayload> ID =
            new Id<>(Identifier.of(Loopypowers.MOD_ID, "cosmic_ray"));

    public static final PacketCodec<PacketByteBuf, CosmicRayPayload> CODEC =
            PacketCodec.of(CosmicRayPayload::write, CosmicRayPayload::new);

    public CosmicRayPayload(PacketByteBuf buf) {
        this(buf.readDouble(), buf.readDouble(), buf.readDouble(),
             buf.readDouble(), buf.readDouble(), buf.readDouble());
    }

    public void write(PacketByteBuf buf) {
        buf.writeDouble(startX); buf.writeDouble(startY); buf.writeDouble(startZ);
        buf.writeDouble(endX);   buf.writeDouble(endY);   buf.writeDouble(endZ);
    }

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
