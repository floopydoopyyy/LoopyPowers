package com.yourname.loopypowers.network.payload;

import com.yourname.loopypowers.Loopypowers;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

// type constants for center-point house rule FX
// 0 = DOUBLE_OR_NOTHING  : ENCHANT 18
// 1 = SMOKE_MACHINE_START: LARGE_SMOKE 25
// 2 = CHIP_TOSS_CENTER   : ENCHANT 18
// 3 = JACKPOT_ARM        : ENCHANT 22
public record FortuneCenterFxPayload(double x, double y, double z, int type) implements CustomPayload {

    public static final Id<FortuneCenterFxPayload> ID =
            new Id<>(Identifier.of(Loopypowers.MOD_ID, "fortune_center_fx"));

    public static final PacketCodec<PacketByteBuf, FortuneCenterFxPayload> CODEC =
            PacketCodec.of(FortuneCenterFxPayload::write, FortuneCenterFxPayload::new);

    public FortuneCenterFxPayload(PacketByteBuf buf) {
        this(buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readInt());
    }

    public void write(PacketByteBuf buf) {
        buf.writeDouble(x); buf.writeDouble(y); buf.writeDouble(z); buf.writeInt(type);
    }

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
