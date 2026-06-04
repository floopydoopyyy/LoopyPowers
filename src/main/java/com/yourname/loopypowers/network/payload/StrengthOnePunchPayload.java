package com.yourname.loopypowers.network.payload;

import com.yourname.loopypowers.Loopypowers;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record StrengthOnePunchPayload(
        double x, double y, double z,
        double dirX, double dirY, double dirZ
) implements CustomPayload {

    public static final Id<StrengthOnePunchPayload> ID =
            new Id<>(Identifier.of(Loopypowers.MOD_ID, "strength_one_punch"));

    public static final PacketCodec<PacketByteBuf, StrengthOnePunchPayload> CODEC =
            PacketCodec.of(StrengthOnePunchPayload::write, StrengthOnePunchPayload::new);

    public StrengthOnePunchPayload(PacketByteBuf buf) {
        this(buf.readDouble(), buf.readDouble(), buf.readDouble(),
             buf.readDouble(), buf.readDouble(), buf.readDouble());
    }
    public void write(PacketByteBuf buf) {
        buf.writeDouble(x); buf.writeDouble(y); buf.writeDouble(z);
        buf.writeDouble(dirX); buf.writeDouble(dirY); buf.writeDouble(dirZ);
    }

    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
