package com.yourname.loopypowers.network.payload;

import com.yourname.loopypowers.Loopypowers;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record TKGrabTargetPayload(int entityId) implements CustomPayload {

    public static final Id<TKGrabTargetPayload> ID =
            new Id<>(Identifier.of(Loopypowers.MOD_ID, "tk_grab_target"));

    public static final PacketCodec<PacketByteBuf, TKGrabTargetPayload> CODEC =
            PacketCodec.of(TKGrabTargetPayload::write, TKGrabTargetPayload::new);

    public TKGrabTargetPayload(PacketByteBuf buf) { this(buf.readInt()); }
    public void write(PacketByteBuf buf) { buf.writeInt(entityId); }

    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
