package com.yourname.loopypowers.network.payload;

import com.yourname.loopypowers.Loopypowers;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record TKThrowPayload(int entityId) implements CustomPayload {

    public static final Id<TKThrowPayload> ID =
            new Id<>(Identifier.of(Loopypowers.MOD_ID, "tk_throw"));

    public static final PacketCodec<PacketByteBuf, TKThrowPayload> CODEC =
            PacketCodec.of(TKThrowPayload::write, TKThrowPayload::new);

    public TKThrowPayload(PacketByteBuf buf) { this(buf.readInt()); }
    public void write(PacketByteBuf buf) { buf.writeInt(entityId); }

    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
