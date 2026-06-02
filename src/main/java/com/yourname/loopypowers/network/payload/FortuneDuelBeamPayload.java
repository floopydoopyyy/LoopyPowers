package com.yourname.loopypowers.network.payload;

import com.yourname.loopypowers.Loopypowers;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record FortuneDuelBeamPayload(
        double startX, double startY, double startZ,
        double endX, double endY, double endZ
) implements CustomPayload {

    public static final Id<FortuneDuelBeamPayload> ID =
            new Id<>(Identifier.of(Loopypowers.MOD_ID, "fortune_duel_beam"));

    public static final PacketCodec<PacketByteBuf, FortuneDuelBeamPayload> CODEC =
            PacketCodec.of(FortuneDuelBeamPayload::write, FortuneDuelBeamPayload::new);

    public FortuneDuelBeamPayload(PacketByteBuf buf) {
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
