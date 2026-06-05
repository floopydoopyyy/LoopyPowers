package com.yourname.loopypowers.network.payload;

import com.yourname.loopypowers.Loopypowers;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Sent every 3 ticks while pinball is active. Carries the fixed anchor used to draw the boundary ring. */
public record SpeedPinballAnchorPayload(
        double anchorX, double anchorY, double anchorZ
) implements CustomPayload {

    public static final Id<SpeedPinballAnchorPayload> ID =
            new Id<>(Identifier.of(Loopypowers.MOD_ID, "speed_pinball_anchor"));

    public static final PacketCodec<PacketByteBuf, SpeedPinballAnchorPayload> CODEC =
            PacketCodec.of(SpeedPinballAnchorPayload::write, SpeedPinballAnchorPayload::new);

    public SpeedPinballAnchorPayload(PacketByteBuf buf) {
        this(buf.readDouble(), buf.readDouble(), buf.readDouble());
    }

    public void write(PacketByteBuf buf) {
        buf.writeDouble(anchorX); buf.writeDouble(anchorY); buf.writeDouble(anchorZ);
    }

    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
