package com.yourname.loopypowers.network.payload;

import com.yourname.loopypowers.Loopypowers;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record IceWaveRingPayload(double cx, double cz, double y, double radius) implements CustomPayload {

    public static final Id<IceWaveRingPayload> ID =
            new Id<>(Identifier.of(Loopypowers.MOD_ID, "ice_wave_ring"));

    public static final PacketCodec<PacketByteBuf, IceWaveRingPayload> CODEC =
            PacketCodec.of(IceWaveRingPayload::write, IceWaveRingPayload::new);

    public IceWaveRingPayload(PacketByteBuf buf) {
        this(buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble());
    }

    public void write(PacketByteBuf buf) {
        buf.writeDouble(cx); buf.writeDouble(cz); buf.writeDouble(y); buf.writeDouble(radius);
    }

    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
