package com.yourname.loopypowers.item;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ElytraItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;

import java.util.List;

public class WingsOfValorItem extends ElytraItem {

    public WingsOfValorItem(Settings settings) {
        super(settings);
    }

    @Override
    public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
        // Gray = flavour text
        tooltip.add(Text.translatable("item.loopypowers.wings_of_valor.tooltip.flavor")
                .formatted(Formatting.GRAY));
        // Light red = warning
        tooltip.add(Text.translatable("item.loopypowers.wings_of_valor.tooltip.warning")
                .formatted(Formatting.RED));

        super.appendTooltip(stack, context, tooltip, type);
    }

    @Override
    public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
        super.inventoryTick(stack, world, entity, slot, selected);

        if (world.isClient()) return;
        if (!(entity instanceof LivingEntity living)) return;

        if (living.getEquippedStack(EquipmentSlot.CHEST) == stack) {
            if (stack.getDamage() != 0) stack.setDamage(0);
        }
    }
}