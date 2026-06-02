package com.yourname.loopypowers.network.payload;

import com.yourname.loopypowers.Loopypowers;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record FortuneHotSeatPassPayload(int fromId, int toId) implements CustomPayload {

    public static final Id<FortuneHotSeatPassPayload> ID =
            new Id<>(Identifier.of(Loopypowers.MOD_ID, "fortune_hot_seat_pass"));

    public static final PacketCodec<PacketByteBuf, FortuneHotSeatPassPayload> CODEC =
            PacketCodec.of(FortuneHotSeatPassPayload::write, FortuneHotSeatPassPayload::new);

    public FortuneHotSeatPassPayload(PacketByteBuf buf) {
        this(buf.readInt(), buf.readInt());
    }

    public void write(PacketByteBuf buf) {
        buf.writeInt(fromId); buf.writeInt(toId);
    }

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
