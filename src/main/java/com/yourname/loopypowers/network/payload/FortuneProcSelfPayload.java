package com.yourname.loopypowers.network.payload;

import com.yourname.loopypowers.Loopypowers;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record FortuneProcSelfPayload(int entityId) implements CustomPayload {

    public static final Id<FortuneProcSelfPayload> ID =
            new Id<>(Identifier.of(Loopypowers.MOD_ID, "fortune_proc_self"));

    public static final PacketCodec<PacketByteBuf, FortuneProcSelfPayload> CODEC =
            PacketCodec.of(FortuneProcSelfPayload::write, FortuneProcSelfPayload::new);

    public FortuneProcSelfPayload(PacketByteBuf buf) {
        this(buf.readInt());
    }

    public void write(PacketByteBuf buf) {
        buf.writeInt(entityId);
    }

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
