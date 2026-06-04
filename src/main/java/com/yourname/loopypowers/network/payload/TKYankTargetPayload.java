package com.yourname.loopypowers.network.payload;

import com.yourname.loopypowers.Loopypowers;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record TKYankTargetPayload(int entityId) implements CustomPayload {

    public static final Id<TKYankTargetPayload> ID =
            new Id<>(Identifier.of(Loopypowers.MOD_ID, "tk_yank_target"));

    public static final PacketCodec<PacketByteBuf, TKYankTargetPayload> CODEC =
            PacketCodec.of(TKYankTargetPayload::write, TKYankTargetPayload::new);

    public TKYankTargetPayload(PacketByteBuf buf) { this(buf.readInt()); }
    public void write(PacketByteBuf buf) { buf.writeInt(entityId); }

    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
