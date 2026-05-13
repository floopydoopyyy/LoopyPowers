package com.yourname.loopypowers.item;

import com.yourname.loopypowers.manager.PowerManager;
import com.yourname.loopypowers.ritual.RitualManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

import java.util.List;

public class MotionVestigeItem extends Item {

    public MotionVestigeItem(Settings settings) {
        super(settings);
    }

    @Override
    public boolean hasGlint(ItemStack stack) {
        return true;
    }

    // 1.21.1 Tooltip Update
    @Override
    public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable("item.loopypowers.motion_vestige.tooltip.flavor")
                .formatted(Formatting.GRAY));

        tooltip.add(Text.translatable("item.loopypowers.motion_vestige.tooltip.desc")
                .formatted(Formatting.LIGHT_PURPLE));

        tooltip.add(Text.translatable("item.loopypowers.vestige.tooltip.available")
                .formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("item.loopypowers.motion_vestige.tooltip.powers")
                .formatted(Formatting.GRAY));
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);

        if (!world.isClient) {
            ServerPlayerEntity player = (ServerPlayerEntity) user;

            // already has the power - Translatable
            if (PowerManager.getPower(player) != null) {
                player.sendMessage(
                        Text.translatable("message.loopypowers.vestige.already_has_power").formatted(Formatting.RED),
                        true
                );
                return TypedActionResult.fail(stack);
            }

            // start ritual
            RitualManager.startRitual(player, RitualManager.RitualType.MOTION_VESTIGE);

            // consume
            if (!player.getAbilities().creativeMode) {
                stack.decrement(1);
            }
        }

        return TypedActionResult.success(stack);
    }
}