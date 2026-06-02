package com.yourname.loopypowers.network.payload;

import com.yourname.loopypowers.Loopypowers;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

// type constants for entity-relative house FX
// 0  = INSIDE_JOIN     : ENCHANT 8   at getHeight()*0.6
// 1  = HOT_SEAT_START  : ENCHANT 18  at getHeight()*0.7
// 2  = HOT_SEAT_TICK   : ENCHANT 10 + CRIT 2  at getHeight()*0.6
// 3  = HOT_SEAT_DETONATE: CLOUD 18   at getHeight()*0.5
// 4  = ROULETTE_PICK   : CRIT 16     at getHeight()*0.6
// 5  = SPOTLIGHT_START : ENCHANT 18  at getHeight()*0.7
// 6  = SPOTLIGHT_TICK  : CRIT 2      at getHeight()*0.8
// 7  = PRISON_PUSH     : ELECTRIC_SPARK 10 at +1.0
// 8  = PRISON_YANK     : ENCHANTED_HIT 30 at +1.0
// 9  = CARD_COUNTER    : ENCHANT 22  at +1.0
// 10 = SMOKE_MACHINE   : SMOKE 8     at getHeight()*0.6
// 11 = CHIP_TOSS_ENTITY: CRIT 10     at getHeight()*0.5
public record FortuneEntityFxPayload(int entityId, int type) implements CustomPayload {

    public static final Id<FortuneEntityFxPayload> ID =
            new Id<>(Identifier.of(Loopypowers.MOD_ID, "fortune_entity_fx"));

    public static final PacketCodec<PacketByteBuf, FortuneEntityFxPayload> CODEC =
            PacketCodec.of(FortuneEntityFxPayload::write, FortuneEntityFxPayload::new);

    public FortuneEntityFxPayload(PacketByteBuf buf) {
        this(buf.readInt(), buf.readInt());
    }

    public void write(PacketByteBuf buf) {
        buf.writeInt(entityId); buf.writeInt(type);
    }

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
