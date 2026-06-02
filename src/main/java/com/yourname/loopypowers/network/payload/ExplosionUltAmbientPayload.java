package com.yourname.loopypowers.network.payload;

import com.yourname.loopypowers.Loopypowers;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record ExplosionUltAmbientPayload(double x, double y, double z, boolean showFlame) implements CustomPayload {

    public static final Id<ExplosionUltAmbientPayload> ID =
            new Id<>(Identifier.of(Loopypowers.MOD_ID, "explosion_ult_ambient"));

    public static final PacketCodec<PacketByteBuf, ExplosionUltAmbientPayload> CODEC =
            PacketCodec.of(ExplosionUltAmbientPayload::write, ExplosionUltAmbientPayload::new);

    public ExplosionUltAmbientPayload(PacketByteBuf buf) {
        this(buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readBoolean());
    }

    public void write(PacketByteBuf buf) {
        buf.writeDouble(x); buf.writeDouble(y); buf.writeDouble(z); buf.writeBoolean(showFlame);
    }

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
