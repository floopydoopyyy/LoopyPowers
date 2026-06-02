package com.yourname.loopypowers.network.payload;

import com.yourname.loopypowers.Loopypowers;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Victim body-mid position + attacker facing XZ for the directional smoke streak. */
public record DarknessBackstabFxPayload(double x, double y, double z, double dirX, double dirZ) implements CustomPayload {

    public static final Id<DarknessBackstabFxPayload> ID =
            new Id<>(Identifier.of(Loopypowers.MOD_ID, "darkness_backstab_fx"));

    public static final PacketCodec<PacketByteBuf, DarknessBackstabFxPayload> CODEC =
            PacketCodec.of(DarknessBackstabFxPayload::write, DarknessBackstabFxPayload::new);

    public DarknessBackstabFxPayload(PacketByteBuf buf) {
        this(buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble());
    }

    public void write(PacketByteBuf buf) {
        buf.writeDouble(x); buf.writeDouble(y); buf.writeDouble(z);
        buf.writeDouble(dirX); buf.writeDouble(dirZ);
    }

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
