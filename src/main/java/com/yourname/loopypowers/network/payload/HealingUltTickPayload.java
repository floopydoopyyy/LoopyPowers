package com.yourname.loopypowers.network.payload;

import com.yourname.loopypowers.Loopypowers;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record HealingUltTickPayload(double x, double y, double z, boolean endRod) implements CustomPayload {

    public static final Id<HealingUltTickPayload> ID =
            new Id<>(Identifier.of(Loopypowers.MOD_ID, "healing_ult_tick"));

    public static final PacketCodec<PacketByteBuf, HealingUltTickPayload> CODEC =
            PacketCodec.of(HealingUltTickPayload::write, HealingUltTickPayload::new);

    public HealingUltTickPayload(PacketByteBuf buf) {
        this(buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readBoolean());
    }

    public void write(PacketByteBuf buf) {
        buf.writeDouble(x); buf.writeDouble(y); buf.writeDouble(z); buf.writeBoolean(endRod);
    }

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
