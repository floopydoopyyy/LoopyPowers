package com.yourname.loopypowers.network.payload;

import com.yourname.loopypowers.Loopypowers;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record NatureGasTickPayload(
        double x, double y, double z,
        float radius, float halfH,
        int count, int seed
) implements CustomPayload {

    public static final Id<NatureGasTickPayload> ID =
            new Id<>(Identifier.of(Loopypowers.MOD_ID, "nature_gas_tick"));

    public static final PacketCodec<PacketByteBuf, NatureGasTickPayload> CODEC =
            PacketCodec.of(NatureGasTickPayload::write, NatureGasTickPayload::new);

    public NatureGasTickPayload(PacketByteBuf buf) {
        this(buf.readDouble(), buf.readDouble(), buf.readDouble(),
             buf.readFloat(), buf.readFloat(),
             buf.readInt(), buf.readInt());
    }

    public void write(PacketByteBuf buf) {
        buf.writeDouble(x); buf.writeDouble(y); buf.writeDouble(z);
        buf.writeFloat(radius); buf.writeFloat(halfH);
        buf.writeInt(count); buf.writeInt(seed);
    }

    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
