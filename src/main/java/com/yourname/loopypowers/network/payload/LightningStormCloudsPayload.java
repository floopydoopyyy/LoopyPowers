package com.yourname.loopypowers.network.payload;

import com.yourname.loopypowers.Loopypowers;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record LightningStormCloudsPayload(double cx, double cy, double cz) implements CustomPayload {

    public static final Id<LightningStormCloudsPayload> ID =
            new Id<>(Identifier.of(Loopypowers.MOD_ID, "lightning_storm_clouds"));

    public static final PacketCodec<PacketByteBuf, LightningStormCloudsPayload> CODEC =
            PacketCodec.of(LightningStormCloudsPayload::write, LightningStormCloudsPayload::new);

    public LightningStormCloudsPayload(PacketByteBuf buf) { this(buf.readDouble(), buf.readDouble(), buf.readDouble()); }
    public void write(PacketByteBuf buf) { buf.writeDouble(cx); buf.writeDouble(cy); buf.writeDouble(cz); }

    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
