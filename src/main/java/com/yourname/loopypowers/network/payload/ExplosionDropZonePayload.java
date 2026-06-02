package com.yourname.loopypowers.network.payload;

import com.yourname.loopypowers.Loopypowers;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Rotating flame ring projected on the ground below the airborne player. */
public record ExplosionDropZonePayload(
        double groundX, double groundY, double groundZ,
        double radius, double offsetAngle
) implements CustomPayload {

    public static final Id<ExplosionDropZonePayload> ID =
            new Id<>(Identifier.of(Loopypowers.MOD_ID, "explosion_drop_zone"));

    public static final PacketCodec<PacketByteBuf, ExplosionDropZonePayload> CODEC =
            PacketCodec.of(ExplosionDropZonePayload::write, ExplosionDropZonePayload::new);

    public ExplosionDropZonePayload(PacketByteBuf buf) {
        this(buf.readDouble(), buf.readDouble(), buf.readDouble(),
             buf.readDouble(), buf.readDouble());
    }

    public void write(PacketByteBuf buf) {
        buf.writeDouble(groundX); buf.writeDouble(groundY); buf.writeDouble(groundZ);
        buf.writeDouble(radius);  buf.writeDouble(offsetAngle);
    }

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
