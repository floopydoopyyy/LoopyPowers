package com.yourname.loopypowers.network.payload;

import com.yourname.loopypowers.Loopypowers;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Shared per-tick particle payload for all rituals.
 * stage=0 means completion burst; stage=1-4 are ritual stages.
 * ritualId identifies which ritual's FxClient handler to use.
 */
public record RitualFxPayload(
        byte ritualId,
        double x, double y, double z,
        int stage, int stageTick,
        float progress,
        long time
) implements CustomPayload {

    public static final Id<RitualFxPayload> ID =
            new Id<>(Identifier.of(Loopypowers.MOD_ID, "ritual_fx"));

    public static final PacketCodec<PacketByteBuf, RitualFxPayload> CODEC =
            PacketCodec.of(RitualFxPayload::write, RitualFxPayload::new);

    public RitualFxPayload(PacketByteBuf buf) {
        this(buf.readByte(),
             buf.readDouble(), buf.readDouble(), buf.readDouble(),
             buf.readInt(), buf.readInt(),
             buf.readFloat(),
             buf.readLong());
    }

    public void write(PacketByteBuf buf) {
        buf.writeByte(ritualId);
        buf.writeDouble(x); buf.writeDouble(y); buf.writeDouble(z);
        buf.writeInt(stage); buf.writeInt(stageTick);
        buf.writeFloat(progress);
        buf.writeLong(time);
    }

    @Override public Id<? extends CustomPayload> getId() { return ID; }

    // Ritual ID constants
    public static final byte ELEMENTAL      = 0;
    public static final byte LIFE           = 1;
    public static final byte MIND           = 2;
    public static final byte MOTION         = 3;
    public static final byte PERFECTED      = 4;
    public static final byte POWER          = 5;
    public static final byte POWER_UPGRADE  = 6;
    public static final byte RUIN           = 7;
    public static final byte SEVERANCE      = 8;
    public static final byte SPACE          = 9;
}
