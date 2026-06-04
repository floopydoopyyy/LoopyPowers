package com.yourname.loopypowers.network.payload;

import com.yourname.loopypowers.Loopypowers;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record TKDebrisOrbitPayload(double x, double y, double z, boolean isInner) implements CustomPayload {

    public static final Id<TKDebrisOrbitPayload> ID =
            new Id<>(Identifier.of(Loopypowers.MOD_ID, "tk_debris_orbit"));

    public static final PacketCodec<PacketByteBuf, TKDebrisOrbitPayload> CODEC =
            PacketCodec.of(TKDebrisOrbitPayload::write, TKDebrisOrbitPayload::new);

    public TKDebrisOrbitPayload(PacketByteBuf buf) {
        this(buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readBoolean());
    }
    public void write(PacketByteBuf buf) {
        buf.writeDouble(x); buf.writeDouble(y); buf.writeDouble(z); buf.writeBoolean(isInner);
    }

    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
