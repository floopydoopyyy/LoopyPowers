package com.yourname.loopypowers.network.payload;

import com.yourname.loopypowers.Loopypowers;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record TKWallImpactPayload(int entityId) implements CustomPayload {

    public static final Id<TKWallImpactPayload> ID =
            new Id<>(Identifier.of(Loopypowers.MOD_ID, "tk_wall_impact"));

    public static final PacketCodec<PacketByteBuf, TKWallImpactPayload> CODEC =
            PacketCodec.of(TKWallImpactPayload::write, TKWallImpactPayload::new);

    public TKWallImpactPayload(PacketByteBuf buf) { this(buf.readInt()); }
    public void write(PacketByteBuf buf) { buf.writeInt(entityId); }

    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
