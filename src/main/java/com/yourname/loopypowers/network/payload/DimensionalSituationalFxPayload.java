package com.yourname.loopypowers.network.payload;

import com.yourname.loopypowers.Loopypowers;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Situational dimension particle effects.
 * type: 0=fall, 1=drown, 2=freeze, 3=levitate, 4=blind,
 *       5=poison, 6=hunger, 7=fire, 8=slowness,
 *       9=easterEggLava, 10=easterEggPoof, 11=easterEggSplash
 */
public record DimensionalSituationalFxPayload(double x, double y, double z, int type) implements CustomPayload {

    public static final Id<DimensionalSituationalFxPayload> ID =
            new Id<>(Identifier.of(Loopypowers.MOD_ID, "dimensional_situational_fx"));

    public static final PacketCodec<PacketByteBuf, DimensionalSituationalFxPayload> CODEC =
            PacketCodec.of(DimensionalSituationalFxPayload::write, DimensionalSituationalFxPayload::new);

    public DimensionalSituationalFxPayload(PacketByteBuf buf) {
        this(buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readInt());
    }

    public void write(PacketByteBuf buf) {
        buf.writeDouble(x); buf.writeDouble(y); buf.writeDouble(z); buf.writeInt(type);
    }

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
