package com.yourname.loopypowers.network.payload;

import com.yourname.loopypowers.Loopypowers;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record IceFreezeStagePayload(int entityId, int stage, boolean spark) implements CustomPayload {

    public static final Id<IceFreezeStagePayload> ID =
            new Id<>(Identifier.of(Loopypowers.MOD_ID, "ice_freeze_stage"));

    public static final PacketCodec<PacketByteBuf, IceFreezeStagePayload> CODEC =
            PacketCodec.of(IceFreezeStagePayload::write, IceFreezeStagePayload::new);

    public IceFreezeStagePayload(PacketByteBuf buf) { this(buf.readInt(), buf.readInt(), buf.readBoolean()); }
    public void write(PacketByteBuf buf) { buf.writeInt(entityId); buf.writeInt(stage); buf.writeBoolean(spark); }

    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
