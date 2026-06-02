package com.yourname.loopypowers.network.payload;

import com.yourname.loopypowers.Loopypowers;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record LightningChainTrailPayload(
        double fx, double fy, double fz,
        double tx, double ty, double tz
) implements CustomPayload {

    public static final Id<LightningChainTrailPayload> ID =
            new Id<>(Identifier.of(Loopypowers.MOD_ID, "lightning_chain_trail"));

    public static final PacketCodec<PacketByteBuf, LightningChainTrailPayload> CODEC =
            PacketCodec.of(LightningChainTrailPayload::write, LightningChainTrailPayload::new);

    public LightningChainTrailPayload(PacketByteBuf buf) {
        this(buf.readDouble(), buf.readDouble(), buf.readDouble(),
             buf.readDouble(), buf.readDouble(), buf.readDouble());
    }

    public void write(PacketByteBuf buf) {
        buf.writeDouble(fx); buf.writeDouble(fy); buf.writeDouble(fz);
        buf.writeDouble(tx); buf.writeDouble(ty); buf.writeDouble(tz);
    }

    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
