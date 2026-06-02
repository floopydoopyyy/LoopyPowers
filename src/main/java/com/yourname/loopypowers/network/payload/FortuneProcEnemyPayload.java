package com.yourname.loopypowers.network.payload;

import com.yourname.loopypowers.Loopypowers;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record FortuneProcEnemyPayload(int entityId) implements CustomPayload {

    public static final Id<FortuneProcEnemyPayload> ID =
            new Id<>(Identifier.of(Loopypowers.MOD_ID, "fortune_proc_enemy"));

    public static final PacketCodec<PacketByteBuf, FortuneProcEnemyPayload> CODEC =
            PacketCodec.of(FortuneProcEnemyPayload::write, FortuneProcEnemyPayload::new);

    public FortuneProcEnemyPayload(PacketByteBuf buf) {
        this(buf.readInt());
    }

    public void write(PacketByteBuf buf) {
        buf.writeInt(entityId);
    }

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
