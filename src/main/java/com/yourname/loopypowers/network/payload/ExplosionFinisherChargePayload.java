package com.yourname.loopypowers.network.payload;

import com.yourname.loopypowers.Loopypowers;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record ExplosionFinisherChargePayload(double x, double y, double z, int warnLeft, boolean showLava)
        implements CustomPayload {

    public static final Id<ExplosionFinisherChargePayload> ID =
            new Id<>(Identifier.of(Loopypowers.MOD_ID, "explosion_finisher_charge"));

    public static final PacketCodec<PacketByteBuf, ExplosionFinisherChargePayload> CODEC =
            PacketCodec.of(ExplosionFinisherChargePayload::write, ExplosionFinisherChargePayload::new);

    public ExplosionFinisherChargePayload(PacketByteBuf buf) {
        this(buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readInt(), buf.readBoolean());
    }

    public void write(PacketByteBuf buf) {
        buf.writeDouble(x); buf.writeDouble(y); buf.writeDouble(z);
        buf.writeInt(warnLeft); buf.writeBoolean(showLava);
    }

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
