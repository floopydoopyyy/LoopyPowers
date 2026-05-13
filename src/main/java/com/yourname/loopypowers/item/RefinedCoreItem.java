package com.yourname.loopypowers.item;

import com.yourname.loopypowers.manager.PowerManager;
import com.yourname.loopypowers.ritual.RitualManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

import java.util.List;

public class RefinedCoreItem extends Item {

    public RefinedCoreItem(Settings settings) {
        super(settings);
    }

    @Override
    public boolean hasGlint(ItemStack stack) {
        return true;
    }

    // 1.21.1 Tooltip Update
    @Override
    public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable("item.loopypowers.refined_celestial_core.tooltip.desc")
                .formatted(Formatting.GRAY));

        tooltip.add(Text.translatable("item.loopypowers.refined_celestial_core.tooltip.effect")
                .formatted(Formatting.LIGHT_PURPLE));
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);

        if (world.isClient) {
            return TypedActionResult.success(stack);
        }

        if (!(user instanceof net.minecraft.server.network.ServerPlayerEntity player)) {
            return TypedActionResult.pass(stack);
        }

        // no power
        if (PowerManager.getPower(player) == null) {
            player.sendMessage(
                    Text.translatable("message.loopypowers.refined_core.no_power").formatted(Formatting.RED),
                    true
            );
            return TypedActionResult.fail(stack);
        }

        int level = PowerManager.getLevel(player);

        // too high
        if (level > 1) {
            player.sendMessage(
                    Text.translatable("message.loopypowers.refined_core.level_too_high").formatted(Formatting.RED),
                    true
            );
            return TypedActionResult.fail(stack);
        }

        // somehow below 1
        if (level < 1) {
            player.sendMessage(
                    Text.translatable("message.loopypowers.refined_core.exception_low").formatted(Formatting.RED),
                    true
            );
            return TypedActionResult.fail(stack);
        }

        // apply it
        RitualManager.startRitual(player, RitualManager.RitualType.REFINED_UPGRADE);

        // consume item
        if (!player.getAbilities().creativeMode) {
            stack.decrement(1);
        }

        return TypedActionResult.success(stack);
    }
}