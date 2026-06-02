package com.yourname.loopypowers.network.payload;

import com.yourname.loopypowers.Loopypowers;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record FortuneHouseRoofPayload(double centerX, double y, double centerZ, double radius) implements CustomPayload {

    public static final Id<FortuneHouseRoofPayload> ID =
            new Id<>(Identifier.of(Loopypowers.MOD_ID, "fortune_house_roof"));

    public static final PacketCodec<PacketByteBuf, FortuneHouseRoofPayload> CODEC =
            PacketCodec.of(FortuneHouseRoofPayload::write, FortuneHouseRoofPayload::new);

    public FortuneHouseRoofPayload(PacketByteBuf buf) {
        this(buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble());
    }

    public void write(PacketByteBuf buf) {
        buf.writeDouble(centerX); buf.writeDouble(y); buf.writeDouble(centerZ); buf.writeDouble(radius);
    }

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
