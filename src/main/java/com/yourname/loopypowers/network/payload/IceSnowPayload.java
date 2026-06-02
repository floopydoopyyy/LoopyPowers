package com.yourname.loopypowers.network.payload;

import com.yourname.loopypowers.Loopypowers;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record IceSnowPayload(double x, double y, double z, int count) implements CustomPayload {

    public static final Id<IceSnowPayload> ID =
            new Id<>(Identifier.of(Loopypowers.MOD_ID, "ice_snow"));

    public static final PacketCodec<PacketByteBuf, IceSnowPayload> CODEC =
            PacketCodec.of(IceSnowPayload::write, IceSnowPayload::new);

    public IceSnowPayload(PacketByteBuf buf) {
        this(buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readInt());
    }

    public void write(PacketByteBuf buf) {
        buf.writeDouble(x); buf.writeDouble(y); buf.writeDouble(z); buf.writeInt(count);
    }

    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
