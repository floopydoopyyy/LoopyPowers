package com.yourname.loopypowers.network.payload;

import com.yourname.loopypowers.Loopypowers;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record LightningClapConePayload(
        double x, double y, double z,
        double fx, double fy, double fz
) implements CustomPayload {

    public static final Id<LightningClapConePayload> ID =
            new Id<>(Identifier.of(Loopypowers.MOD_ID, "lightning_clap_cone"));

    public static final PacketCodec<PacketByteBuf, LightningClapConePayload> CODEC =
            PacketCodec.of(LightningClapConePayload::write, LightningClapConePayload::new);

    public LightningClapConePayload(PacketByteBuf buf) {
        this(buf.readDouble(), buf.readDouble(), buf.readDouble(),
             buf.readDouble(), buf.readDouble(), buf.readDouble());
    }

    public void write(PacketByteBuf buf) {
        buf.writeDouble(x); buf.writeDouble(y); buf.writeDouble(z);
        buf.writeDouble(fx); buf.writeDouble(fy); buf.writeDouble(fz);
    }

    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
