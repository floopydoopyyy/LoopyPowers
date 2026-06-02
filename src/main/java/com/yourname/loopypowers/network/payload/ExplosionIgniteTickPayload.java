package com.yourname.loopypowers.network.payload;

import com.yourname.loopypowers.Loopypowers;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record ExplosionIgniteTickPayload(double x, double y, double z, float progress, boolean largesmoke)
        implements CustomPayload {

    public static final Id<ExplosionIgniteTickPayload> ID =
            new Id<>(Identifier.of(Loopypowers.MOD_ID, "explosion_ignite_tick"));

    public static final PacketCodec<PacketByteBuf, ExplosionIgniteTickPayload> CODEC =
            PacketCodec.of(ExplosionIgniteTickPayload::write, ExplosionIgniteTickPayload::new);

    public ExplosionIgniteTickPayload(PacketByteBuf buf) {
        this(buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readFloat(), buf.readBoolean());
    }

    public void write(PacketByteBuf buf) {
        buf.writeDouble(x); buf.writeDouble(y); buf.writeDouble(z);
        buf.writeFloat(progress); buf.writeBoolean(largesmoke);
    }

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
