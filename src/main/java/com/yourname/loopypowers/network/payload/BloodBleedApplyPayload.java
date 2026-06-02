package com.yourname.loopypowers.network.payload;

import com.yourname.loopypowers.Loopypowers;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record BloodBleedApplyPayload(double x, double y, double z, int count) implements CustomPayload {

    public static final Id<BloodBleedApplyPayload> ID =
            new Id<>(Identifier.of(Loopypowers.MOD_ID, "blood_bleed_apply"));

    public static final PacketCodec<PacketByteBuf, BloodBleedApplyPayload> CODEC =
            PacketCodec.of(BloodBleedApplyPayload::write, BloodBleedApplyPayload::new);

    public BloodBleedApplyPayload(PacketByteBuf buf) {
        this(buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readInt());
    }

    public void write(PacketByteBuf buf) {
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
        buf.writeInt(count);
    }

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
