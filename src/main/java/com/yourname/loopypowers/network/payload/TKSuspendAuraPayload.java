package com.yourname.loopypowers.network.payload;

import com.yourname.loopypowers.Loopypowers;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record TKSuspendAuraPayload(int entityId, long time) implements CustomPayload {

    public static final Id<TKSuspendAuraPayload> ID =
            new Id<>(Identifier.of(Loopypowers.MOD_ID, "tk_suspend_aura"));

    public static final PacketCodec<PacketByteBuf, TKSuspendAuraPayload> CODEC =
            PacketCodec.of(TKSuspendAuraPayload::write, TKSuspendAuraPayload::new);

    public TKSuspendAuraPayload(PacketByteBuf buf) { this(buf.readInt(), buf.readLong()); }
    public void write(PacketByteBuf buf) { buf.writeInt(entityId); buf.writeLong(time); }

    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
