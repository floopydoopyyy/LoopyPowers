package com.yourname.loopypowers.network.payload;

import com.yourname.loopypowers.Loopypowers;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record IceShatterReadyPayload(int entityId, double baseAng, boolean enchant) implements CustomPayload {

    public static final Id<IceShatterReadyPayload> ID =
            new Id<>(Identifier.of(Loopypowers.MOD_ID, "ice_shatter_ready"));

    public static final PacketCodec<PacketByteBuf, IceShatterReadyPayload> CODEC =
            PacketCodec.of(IceShatterReadyPayload::write, IceShatterReadyPayload::new);

    public IceShatterReadyPayload(PacketByteBuf buf) { this(buf.readInt(), buf.readDouble(), buf.readBoolean()); }
    public void write(PacketByteBuf buf) { buf.writeInt(entityId); buf.writeDouble(baseAng); buf.writeBoolean(enchant); }

    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
