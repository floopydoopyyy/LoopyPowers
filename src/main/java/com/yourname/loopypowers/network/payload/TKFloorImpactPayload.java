package com.yourname.loopypowers.network.payload;

import com.yourname.loopypowers.Loopypowers;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record TKFloorImpactPayload(int entityId) implements CustomPayload {

    public static final Id<TKFloorImpactPayload> ID =
            new Id<>(Identifier.of(Loopypowers.MOD_ID, "tk_floor_impact"));

    public static final PacketCodec<PacketByteBuf, TKFloorImpactPayload> CODEC =
            PacketCodec.of(TKFloorImpactPayload::write, TKFloorImpactPayload::new);

    public TKFloorImpactPayload(PacketByteBuf buf) { this(buf.readInt()); }
    public void write(PacketByteBuf buf) { buf.writeInt(entityId); }

    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
