package com.yourname.loopypowers.network.payload;

import com.yourname.loopypowers.Loopypowers;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record NatureVineTetherPayload(
        double fromX, double fromY, double fromZ,
        double toX, double toY, double toZ,
        int seed
) implements CustomPayload {

    public static final Id<NatureVineTetherPayload> ID =
            new Id<>(Identifier.of(Loopypowers.MOD_ID, "nature_vine_tether"));

    public static final PacketCodec<PacketByteBuf, NatureVineTetherPayload> CODEC =
            PacketCodec.of(NatureVineTetherPayload::write, NatureVineTetherPayload::new);

    public NatureVineTetherPayload(PacketByteBuf buf) {
        this(buf.readDouble(), buf.readDouble(), buf.readDouble(),
             buf.readDouble(), buf.readDouble(), buf.readDouble(),
             buf.readInt());
    }

    public void write(PacketByteBuf buf) {
        buf.writeDouble(fromX); buf.writeDouble(fromY); buf.writeDouble(fromZ);
        buf.writeDouble(toX); buf.writeDouble(toY); buf.writeDouble(toZ);
        buf.writeInt(seed);
    }

    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
