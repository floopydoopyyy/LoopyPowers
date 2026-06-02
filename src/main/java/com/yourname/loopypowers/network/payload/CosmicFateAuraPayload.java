package com.yourname.loopypowers.network.payload;

import com.yourname.loopypowers.Loopypowers;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record CosmicFateAuraPayload(int entityId, float storedDamage, int timerTicks) implements CustomPayload {

    public static final Id<CosmicFateAuraPayload> ID =
            new Id<>(Identifier.of(Loopypowers.MOD_ID, "cosmic_fate_aura"));

    public static final PacketCodec<PacketByteBuf, CosmicFateAuraPayload> CODEC =
            PacketCodec.of(CosmicFateAuraPayload::write, CosmicFateAuraPayload::new);

    public CosmicFateAuraPayload(PacketByteBuf buf) {
        this(buf.readInt(), buf.readFloat(), buf.readInt());
    }

    public void write(PacketByteBuf buf) {
        buf.writeInt(entityId);
        buf.writeFloat(storedDamage);
        buf.writeInt(timerTicks);
    }

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
