package com.yourname.loopypowers.network.payload;

import com.yourname.loopypowers.Loopypowers;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record IceBeamChargePayload(double mx, double my, double mz, int age) implements CustomPayload {

    public static final Id<IceBeamChargePayload> ID =
            new Id<>(Identifier.of(Loopypowers.MOD_ID, "ice_beam_charge"));

    public static final PacketCodec<PacketByteBuf, IceBeamChargePayload> CODEC =
            PacketCodec.of(IceBeamChargePayload::write, IceBeamChargePayload::new);

    public IceBeamChargePayload(PacketByteBuf buf) {
        this(buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readInt());
    }

    public void write(PacketByteBuf buf) {
        buf.writeDouble(mx); buf.writeDouble(my); buf.writeDouble(mz); buf.writeInt(age);
    }

    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
