package com.yourname.loopypowers.network.payload;

import com.yourname.loopypowers.Loopypowers;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record LightningStormStrikePayload(int entityId) implements CustomPayload {

    public static final Id<LightningStormStrikePayload> ID =
            new Id<>(Identifier.of(Loopypowers.MOD_ID, "lightning_storm_strike"));

    public static final PacketCodec<PacketByteBuf, LightningStormStrikePayload> CODEC =
            PacketCodec.of(LightningStormStrikePayload::write, LightningStormStrikePayload::new);

    public LightningStormStrikePayload(PacketByteBuf buf) { this(buf.readInt()); }
    public void write(PacketByteBuf buf) { buf.writeInt(entityId); }

    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
