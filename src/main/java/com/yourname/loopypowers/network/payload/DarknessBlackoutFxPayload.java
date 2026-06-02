package com.yourname.loopypowers.network.payload;

import com.yourname.loopypowers.Loopypowers;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Per-tick blackout sphere visuals — client reproduces the full sphere. */
public record DarknessBlackoutFxPayload(double x, double y, double z) implements CustomPayload {

    public static final Id<DarknessBlackoutFxPayload> ID =
            new Id<>(Identifier.of(Loopypowers.MOD_ID, "darkness_blackout_fx"));

    public static final PacketCodec<PacketByteBuf, DarknessBlackoutFxPayload> CODEC =
            PacketCodec.of(DarknessBlackoutFxPayload::write, DarknessBlackoutFxPayload::new);

    public DarknessBlackoutFxPayload(PacketByteBuf buf) {
        this(buf.readDouble(), buf.readDouble(), buf.readDouble());
    }

    public void write(PacketByteBuf buf) {
        buf.writeDouble(x); buf.writeDouble(y); buf.writeDouble(z);
    }

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
