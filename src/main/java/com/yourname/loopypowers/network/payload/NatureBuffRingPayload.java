package com.yourname.loopypowers.network.payload;

import com.yourname.loopypowers.Loopypowers;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record NatureBuffRingPayload(double x, double y, double z, long time) implements CustomPayload {

    public static final Id<NatureBuffRingPayload> ID =
            new Id<>(Identifier.of(Loopypowers.MOD_ID, "nature_buff_ring"));

    public static final PacketCodec<PacketByteBuf, NatureBuffRingPayload> CODEC =
            PacketCodec.of(NatureBuffRingPayload::write, NatureBuffRingPayload::new);

    public NatureBuffRingPayload(PacketByteBuf buf) {
        this(buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readLong());
    }

    public void write(PacketByteBuf buf) {
        buf.writeDouble(x); buf.writeDouble(y); buf.writeDouble(z); buf.writeLong(time);
    }

    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
