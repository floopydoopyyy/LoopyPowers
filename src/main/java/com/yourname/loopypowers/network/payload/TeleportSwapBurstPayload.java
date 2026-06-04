package com.yourname.loopypowers.network.payload;

import com.yourname.loopypowers.Loopypowers;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record TeleportSwapBurstPayload(
        double fromX, double fromY, double fromZ,
        double toX, double toY, double toZ
) implements CustomPayload {

    public static final Id<TeleportSwapBurstPayload> ID =
            new Id<>(Identifier.of(Loopypowers.MOD_ID, "teleport_swap_burst"));

    public static final PacketCodec<PacketByteBuf, TeleportSwapBurstPayload> CODEC =
            PacketCodec.of(TeleportSwapBurstPayload::write, TeleportSwapBurstPayload::new);

    public TeleportSwapBurstPayload(PacketByteBuf buf) {
        this(buf.readDouble(), buf.readDouble(), buf.readDouble(),
             buf.readDouble(), buf.readDouble(), buf.readDouble());
    }
    public void write(PacketByteBuf buf) {
        buf.writeDouble(fromX); buf.writeDouble(fromY); buf.writeDouble(fromZ);
        buf.writeDouble(toX); buf.writeDouble(toY); buf.writeDouble(toZ);
    }

    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
