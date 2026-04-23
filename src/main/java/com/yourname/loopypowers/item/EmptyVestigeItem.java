package com.yourname.loopypowers.item;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import java.util.List;

public class EmptyVestigeItem extends Item {

    public EmptyVestigeItem(Settings settings) {
        super(settings);
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world,
                              List<Text> tooltip, TooltipContext context) {

        tooltip.add(Text.literal("Built to hold something powerful.")
                .formatted(Formatting.GRAY));
        super.appendTooltip(stack, world, tooltip, context);
    }
}