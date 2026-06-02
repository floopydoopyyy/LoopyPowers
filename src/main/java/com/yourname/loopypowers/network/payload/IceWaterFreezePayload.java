package com.yourname.loopypowers.network.payload;

import com.yourname.loopypowers.Loopypowers;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record IceWaterFreezePayload(double x, double y, double z, int count, boolean enchant) implements CustomPayload {

    public static final Id<IceWaterFreezePayload> ID =
            new Id<>(Identifier.of(Loopypowers.MOD_ID, "ice_water_freeze"));

    public static final PacketCodec<PacketByteBuf, IceWaterFreezePayload> CODEC =
            PacketCodec.of(IceWaterFreezePayload::write, IceWaterFreezePayload::new);

    public IceWaterFreezePayload(PacketByteBuf buf) {
        this(buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readInt(), buf.readBoolean());
    }

    public void write(PacketByteBuf buf) {
        buf.writeDouble(x); buf.writeDouble(y); buf.writeDouble(z);
        buf.writeInt(count); buf.writeBoolean(enchant);
    }

    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
