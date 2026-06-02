package com.yourname.loopypowers.network.payload;

import com.yourname.loopypowers.Loopypowers;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record LightningConfettiPayload(
        double x, double y, double z,
        double fx, double fy, double fz
) implements CustomPayload {

    public static final Id<LightningConfettiPayload> ID =
            new Id<>(Identifier.of(Loopypowers.MOD_ID, "lightning_confetti"));

    public static final PacketCodec<PacketByteBuf, LightningConfettiPayload> CODEC =
            PacketCodec.of(LightningConfettiPayload::write, LightningConfettiPayload::new);

    public LightningConfettiPayload(PacketByteBuf buf) {
        this(buf.readDouble(), buf.readDouble(), buf.readDouble(),
             buf.readDouble(), buf.readDouble(), buf.readDouble());
    }

    public void write(PacketByteBuf buf) {
        buf.writeDouble(x); buf.writeDouble(y); buf.writeDouble(z);
        buf.writeDouble(fx); buf.writeDouble(fy); buf.writeDouble(fz);
    }

    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
