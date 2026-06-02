package com.yourname.loopypowers.network.payload;

import com.yourname.loopypowers.Loopypowers;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record HealingExpelledPayload(
        double x, double y, double z,
        double dirX, double dirY, double dirZ,
        boolean hasFire
) implements CustomPayload {

    public static final Id<HealingExpelledPayload> ID =
            new Id<>(Identifier.of(Loopypowers.MOD_ID, "healing_expelled"));

    public static final PacketCodec<PacketByteBuf, HealingExpelledPayload> CODEC =
            PacketCodec.of(HealingExpelledPayload::write, HealingExpelledPayload::new);

    public HealingExpelledPayload(PacketByteBuf buf) {
        this(buf.readDouble(), buf.readDouble(), buf.readDouble(),
             buf.readDouble(), buf.readDouble(), buf.readDouble(),
             buf.readBoolean());
    }

    public void write(PacketByteBuf buf) {
        buf.writeDouble(x); buf.writeDouble(y); buf.writeDouble(z);
        buf.writeDouble(dirX); buf.writeDouble(dirY); buf.writeDouble(dirZ);
        buf.writeBoolean(hasFire);
    }

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
