package com.yourname.loopypowers.network.payload;

import com.yourname.loopypowers.Loopypowers;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record IceShatterPayload(int entityId) implements CustomPayload {

    public static final Id<IceShatterPayload> ID =
            new Id<>(Identifier.of(Loopypowers.MOD_ID, "ice_shatter"));

    public static final PacketCodec<PacketByteBuf, IceShatterPayload> CODEC =
            PacketCodec.of(IceShatterPayload::write, IceShatterPayload::new);

    public IceShatterPayload(PacketByteBuf buf) { this(buf.readInt()); }
    public void write(PacketByteBuf buf) { buf.writeInt(entityId); }

    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
