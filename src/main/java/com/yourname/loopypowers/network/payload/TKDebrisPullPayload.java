package com.yourname.loopypowers.network.payload;

import com.yourname.loopypowers.Loopypowers;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record TKDebrisPullPayload(int entityId, float pullX, float pullY, float pullZ) implements CustomPayload {

    public static final Id<TKDebrisPullPayload> ID =
            new Id<>(Identifier.of(Loopypowers.MOD_ID, "tk_debris_pull"));

    public static final PacketCodec<PacketByteBuf, TKDebrisPullPayload> CODEC =
            PacketCodec.of(TKDebrisPullPayload::write, TKDebrisPullPayload::new);

    public TKDebrisPullPayload(PacketByteBuf buf) {
        this(buf.readInt(), buf.readFloat(), buf.readFloat(), buf.readFloat());
    }
    public void write(PacketByteBuf buf) {
        buf.writeInt(entityId); buf.writeFloat(pullX); buf.writeFloat(pullY); buf.writeFloat(pullZ);
    }

    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
