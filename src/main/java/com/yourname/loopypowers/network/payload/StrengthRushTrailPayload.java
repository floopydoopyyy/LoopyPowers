package com.yourname.loopypowers.network.payload;

import com.yourname.loopypowers.Loopypowers;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record StrengthRushTrailPayload(
        double x, double y, double z,
        int groundX, int groundY, int groundZ,
        boolean hasGroundBlock,
        float dirX, float dirZ,
        boolean showCrit
) implements CustomPayload {

    public static final Id<StrengthRushTrailPayload> ID =
            new Id<>(Identifier.of(Loopypowers.MOD_ID, "strength_rush_trail"));

    public static final PacketCodec<PacketByteBuf, StrengthRushTrailPayload> CODEC =
            PacketCodec.of(StrengthRushTrailPayload::write, StrengthRushTrailPayload::new);

    public StrengthRushTrailPayload(PacketByteBuf buf) {
        this(buf.readDouble(), buf.readDouble(), buf.readDouble(),
             buf.readInt(), buf.readInt(), buf.readInt(),
             buf.readBoolean(),
             buf.readFloat(), buf.readFloat(),
             buf.readBoolean());
    }
    public void write(PacketByteBuf buf) {
        buf.writeDouble(x); buf.writeDouble(y); buf.writeDouble(z);
        buf.writeInt(groundX); buf.writeInt(groundY); buf.writeInt(groundZ);
        buf.writeBoolean(hasGroundBlock);
        buf.writeFloat(dirX); buf.writeFloat(dirZ);
        buf.writeBoolean(showCrit);
    }

    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
