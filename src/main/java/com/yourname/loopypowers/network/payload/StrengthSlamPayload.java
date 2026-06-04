package com.yourname.loopypowers.network.payload;

import com.yourname.loopypowers.Loopypowers;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record StrengthSlamPayload(
        double slamX, double slamY, double slamZ,
        int groundX, int groundY, int groundZ,
        boolean casterGrounded
) implements CustomPayload {

    public static final Id<StrengthSlamPayload> ID =
            new Id<>(Identifier.of(Loopypowers.MOD_ID, "strength_slam"));

    public static final PacketCodec<PacketByteBuf, StrengthSlamPayload> CODEC =
            PacketCodec.of(StrengthSlamPayload::write, StrengthSlamPayload::new);

    public StrengthSlamPayload(PacketByteBuf buf) {
        this(buf.readDouble(), buf.readDouble(), buf.readDouble(),
             buf.readInt(), buf.readInt(), buf.readInt(),
             buf.readBoolean());
    }
    public void write(PacketByteBuf buf) {
        buf.writeDouble(slamX); buf.writeDouble(slamY); buf.writeDouble(slamZ);
        buf.writeInt(groundX); buf.writeInt(groundY); buf.writeInt(groundZ);
        buf.writeBoolean(casterGrounded);
    }

    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
