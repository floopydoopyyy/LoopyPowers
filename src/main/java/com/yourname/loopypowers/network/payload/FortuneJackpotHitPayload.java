package com.yourname.loopypowers.network.payload;

import com.yourname.loopypowers.Loopypowers;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record FortuneJackpotHitPayload(int entityId) implements CustomPayload {

    public static final Id<FortuneJackpotHitPayload> ID =
            new Id<>(Identifier.of(Loopypowers.MOD_ID, "fortune_jackpot_hit"));

    public static final PacketCodec<PacketByteBuf, FortuneJackpotHitPayload> CODEC =
            PacketCodec.of(FortuneJackpotHitPayload::write, FortuneJackpotHitPayload::new);

    public FortuneJackpotHitPayload(PacketByteBuf buf) {
        this(buf.readInt());
    }

    public void write(PacketByteBuf buf) {
        buf.writeInt(entityId);
    }

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
