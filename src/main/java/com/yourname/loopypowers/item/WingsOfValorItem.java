package com.yourname.loopypowers.item;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ElytraItem;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class WingsOfValorItem extends ElytraItem {
    public WingsOfValorItem(Settings settings) {
        super(settings);
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world,
                              List<Text> tooltip, TooltipContext context) {

        tooltip.add(Text.literal("Powerful wings fit for a God.")
                .formatted(Formatting.GRAY));
        tooltip.add(Text.literal("This item is a part of you and cannot be removed.")
                .formatted(Formatting.GRAY));
        super.appendTooltip(stack, world, tooltip, context);
    }

    @Override // sets dura to 0
    public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
        super.inventoryTick(stack, world, entity, slot, selected);

        if (world.isClient()) return;
        if (!(entity instanceof LivingEntity living)) return;

        if (living.getEquippedStack(EquipmentSlot.CHEST) == stack) {
            if (stack.getDamage() != 0) stack.setDamage(0);
        }
    }
}