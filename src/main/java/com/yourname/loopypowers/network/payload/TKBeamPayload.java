package com.yourname.loopypowers.network.payload;

import com.yourname.loopypowers.Loopypowers;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record TKBeamPayload(
        double fromX, double fromY, double fromZ,
        double toX, double toY, double toZ
) implements CustomPayload {

    public static final Id<TKBeamPayload> ID =
            new Id<>(Identifier.of(Loopypowers.MOD_ID, "tk_beam"));

    public static final PacketCodec<PacketByteBuf, TKBeamPayload> CODEC =
            PacketCodec.of(TKBeamPayload::write, TKBeamPayload::new);

    public TKBeamPayload(PacketByteBuf buf) {
        this(buf.readDouble(), buf.readDouble(), buf.readDouble(),
             buf.readDouble(), buf.readDouble(), buf.readDouble());
    }
    public void write(PacketByteBuf buf) {
        buf.writeDouble(fromX); buf.writeDouble(fromY); buf.writeDouble(fromZ);
        buf.writeDouble(toX); buf.writeDouble(toY); buf.writeDouble(toZ);
    }

    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
