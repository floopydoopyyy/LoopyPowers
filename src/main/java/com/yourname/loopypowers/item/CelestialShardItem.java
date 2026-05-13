package com.yourname.loopypowers.item;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

public class CelestialShardItem extends Item {

    public CelestialShardItem(Settings settings) {
        super(settings);
    }

    @Override
    public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
        // Gray = flavour text
        tooltip.add(Text.translatable("item.loopypowers.celestial_shard.tooltip.flavor")
                .formatted(Formatting.GRAY));
        // Purple = useful info
        tooltip.add(Text.translatable("item.loopypowers.celestial_shard.tooltip.info")
                .formatted(Formatting.LIGHT_PURPLE));

        super.appendTooltip(stack, context, tooltip, type);
    }
}