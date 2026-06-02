package com.yourname.loopypowers.network.payload;

import com.yourname.loopypowers.Loopypowers;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record HealingAbsorbTickPayload(double x, double y, double z, float intensity, boolean endRod) implements CustomPayload {

    public static final Id<HealingAbsorbTickPayload> ID =
            new Id<>(Identifier.of(Loopypowers.MOD_ID, "healing_absorb_tick"));

    public static final PacketCodec<PacketByteBuf, HealingAbsorbTickPayload> CODEC =
            PacketCodec.of(HealingAbsorbTickPayload::write, HealingAbsorbTickPayload::new);

    public HealingAbsorbTickPayload(PacketByteBuf buf) {
        this(buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readFloat(), buf.readBoolean());
    }

    public void write(PacketByteBuf buf) {
        buf.writeDouble(x); buf.writeDouble(y); buf.writeDouble(z);
        buf.writeFloat(intensity); buf.writeBoolean(endRod);
    }

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
