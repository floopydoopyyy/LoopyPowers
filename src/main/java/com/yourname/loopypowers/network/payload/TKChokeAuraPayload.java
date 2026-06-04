package com.yourname.loopypowers.network.payload;

import com.yourname.loopypowers.Loopypowers;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record TKChokeAuraPayload(int entityId, long time, float chokeProgress) implements CustomPayload {

    public static final Id<TKChokeAuraPayload> ID =
            new Id<>(Identifier.of(Loopypowers.MOD_ID, "tk_choke_aura"));

    public static final PacketCodec<PacketByteBuf, TKChokeAuraPayload> CODEC =
            PacketCodec.of(TKChokeAuraPayload::write, TKChokeAuraPayload::new);

    public TKChokeAuraPayload(PacketByteBuf buf) { this(buf.readInt(), buf.readLong(), buf.readFloat()); }
    public void write(PacketByteBuf buf) { buf.writeInt(entityId); buf.writeLong(time); buf.writeFloat(chokeProgress); }

    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
