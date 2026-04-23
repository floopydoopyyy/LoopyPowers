package com.yourname.loopypowers.item;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.world.World;
import net.minecraft.block.Block;

import java.util.List;

public class CelestialBlockItem extends BlockItem {

    public CelestialBlockItem(Block block, Settings settings) {
        super(block, settings);
    }

    @Override
    public void appendTooltip(ItemStack stack, World world, List<Text> tooltip, TooltipContext context) {
        tooltip.add(Text.translatable("block.loopypowers.celestial_block.tooltip"));
    }
}