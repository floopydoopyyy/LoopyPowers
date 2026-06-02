package com.yourname.loopypowers.network.payload;

import com.yourname.loopypowers.Loopypowers;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record LightningSuperchargeAuraPayload(
        double x, double y, double z,
        long time,
        boolean spark, boolean endRod
) implements CustomPayload {

    public static final Id<LightningSuperchargeAuraPayload> ID =
            new Id<>(Identifier.of(Loopypowers.MOD_ID, "lightning_supercharge_aura"));

    public static final PacketCodec<PacketByteBuf, LightningSuperchargeAuraPayload> CODEC =
            PacketCodec.of(LightningSuperchargeAuraPayload::write, LightningSuperchargeAuraPayload::new);

    public LightningSuperchargeAuraPayload(PacketByteBuf buf) {
        this(buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readLong(), buf.readBoolean(), buf.readBoolean());
    }

    public void write(PacketByteBuf buf) {
        buf.writeDouble(x); buf.writeDouble(y); buf.writeDouble(z);
        buf.writeLong(time); buf.writeBoolean(spark); buf.writeBoolean(endRod);
    }

    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
