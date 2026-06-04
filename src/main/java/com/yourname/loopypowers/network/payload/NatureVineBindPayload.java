package com.yourname.loopypowers.network.payload;

import com.yourname.loopypowers.Loopypowers;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record NatureVineBindPayload(
        double fromX, double fromY, double fromZ,
        int entityId,
        double anchorX, double anchorY, double anchorZ,
        int seed
) implements CustomPayload {

    public static final Id<NatureVineBindPayload> ID =
            new Id<>(Identifier.of(Loopypowers.MOD_ID, "nature_vine_bind"));

    public static final PacketCodec<PacketByteBuf, NatureVineBindPayload> CODEC =
            PacketCodec.of(NatureVineBindPayload::write, NatureVineBindPayload::new);

    public NatureVineBindPayload(PacketByteBuf buf) {
        this(buf.readDouble(), buf.readDouble(), buf.readDouble(),
             buf.readInt(),
             buf.readDouble(), buf.readDouble(), buf.readDouble(),
             buf.readInt());
    }

    public void write(PacketByteBuf buf) {
        buf.writeDouble(fromX); buf.writeDouble(fromY); buf.writeDouble(fromZ);
        buf.writeInt(entityId);
        buf.writeDouble(anchorX); buf.writeDouble(anchorY); buf.writeDouble(anchorZ);
        buf.writeInt(seed);
    }

    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
