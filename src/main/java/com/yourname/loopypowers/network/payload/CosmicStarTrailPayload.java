package com.yourname.loopypowers.network.payload;

import com.yourname.loopypowers.Loopypowers;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record CosmicStarTrailPayload(double x, double y, double z) implements CustomPayload {

    public static final Id<CosmicStarTrailPayload> ID =
            new Id<>(Identifier.of(Loopypowers.MOD_ID, "cosmic_star_trail"));

    public static final PacketCodec<PacketByteBuf, CosmicStarTrailPayload> CODEC =
            PacketCodec.of(CosmicStarTrailPayload::write, CosmicStarTrailPayload::new);

    public CosmicStarTrailPayload(PacketByteBuf buf) {
        this(buf.readDouble(), buf.readDouble(), buf.readDouble());
    }

    public void write(PacketByteBuf buf) {
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
    }

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
