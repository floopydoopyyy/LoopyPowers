package com.yourname.loopypowers.network.payload;

import com.yourname.loopypowers.Loopypowers;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Tells the client to discard stored rift geometry for this caster. */
public record DimensionalRiftEndPayload(int casterId) implements CustomPayload {

    public static final Id<DimensionalRiftEndPayload> ID =
            new Id<>(Identifier.of(Loopypowers.MOD_ID, "dimensional_rift_end"));

    public static final PacketCodec<PacketByteBuf, DimensionalRiftEndPayload> CODEC =
            PacketCodec.of(DimensionalRiftEndPayload::write, DimensionalRiftEndPayload::new);

    public DimensionalRiftEndPayload(PacketByteBuf buf) {
        this(buf.readInt());
    }

    public void write(PacketByteBuf buf) {
        buf.writeInt(casterId);
    }

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
