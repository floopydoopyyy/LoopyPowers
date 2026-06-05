package com.yourname.loopypowers.network.payload;

import com.yourname.loopypowers.Loopypowers;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record SoundUltBeamPayload(
        double fromX, double fromY, double fromZ,
        double toX, double toY, double toZ,
        long seed
) implements CustomPayload {

    public static final Id<SoundUltBeamPayload> ID =
            new Id<>(Identifier.of(Loopypowers.MOD_ID, "sound_ult_beam"));

    public static final PacketCodec<PacketByteBuf, SoundUltBeamPayload> CODEC =
            PacketCodec.of(SoundUltBeamPayload::write, SoundUltBeamPayload::new);

    public SoundUltBeamPayload(PacketByteBuf buf) {
        this(buf.readDouble(), buf.readDouble(), buf.readDouble(),
             buf.readDouble(), buf.readDouble(), buf.readDouble(),
             buf.readLong());
    }
    public void write(PacketByteBuf buf) {
        buf.writeDouble(fromX); buf.writeDouble(fromY); buf.writeDouble(fromZ);
        buf.writeDouble(toX); buf.writeDouble(toY); buf.writeDouble(toZ);
        buf.writeLong(seed);
    }

    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
