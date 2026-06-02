package com.yourname.loopypowers.network.payload;

import com.yourname.loopypowers.Loopypowers;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record FortuneDuelTetherPayload(int entityIdA, int entityIdB) implements CustomPayload {

    public static final Id<FortuneDuelTetherPayload> ID =
            new Id<>(Identifier.of(Loopypowers.MOD_ID, "fortune_duel_tether"));

    public static final PacketCodec<PacketByteBuf, FortuneDuelTetherPayload> CODEC =
            PacketCodec.of(FortuneDuelTetherPayload::write, FortuneDuelTetherPayload::new);

    public FortuneDuelTetherPayload(PacketByteBuf buf) {
        this(buf.readInt(), buf.readInt());
    }

    public void write(PacketByteBuf buf) {
        buf.writeInt(entityIdA); buf.writeInt(entityIdB);
    }

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
