package com.yourname.loopypowers.network.payload;

import com.yourname.loopypowers.Loopypowers;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record FortunePoofAtPayload(double x, double y, double z, int count) implements CustomPayload {

    public static final Id<FortunePoofAtPayload> ID =
            new Id<>(Identifier.of(Loopypowers.MOD_ID, "fortune_poof_at"));

    public static final PacketCodec<PacketByteBuf, FortunePoofAtPayload> CODEC =
            PacketCodec.of(FortunePoofAtPayload::write, FortunePoofAtPayload::new);

    public FortunePoofAtPayload(PacketByteBuf buf) {
        this(buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readInt());
    }

    public void write(PacketByteBuf buf) {
        buf.writeDouble(x); buf.writeDouble(y); buf.writeDouble(z); buf.writeInt(count);
    }

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
