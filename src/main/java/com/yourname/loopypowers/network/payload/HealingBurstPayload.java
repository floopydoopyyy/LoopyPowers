package com.yourname.loopypowers.network.payload;

import com.yourname.loopypowers.Loopypowers;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record HealingBurstPayload(double x, double y, double z, float stored) implements CustomPayload {

    public static final Id<HealingBurstPayload> ID =
            new Id<>(Identifier.of(Loopypowers.MOD_ID, "healing_burst"));

    public static final PacketCodec<PacketByteBuf, HealingBurstPayload> CODEC =
            PacketCodec.of(HealingBurstPayload::write, HealingBurstPayload::new);

    public HealingBurstPayload(PacketByteBuf buf) {
        this(buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readFloat());
    }

    public void write(PacketByteBuf buf) {
        buf.writeDouble(x); buf.writeDouble(y); buf.writeDouble(z); buf.writeFloat(stored);
    }

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
