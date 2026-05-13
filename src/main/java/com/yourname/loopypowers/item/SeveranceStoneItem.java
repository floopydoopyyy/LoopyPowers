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

public class SeveranceStoneItem extends Item {

    public SeveranceStoneItem(Settings settings) {
        super(settings);
    }

    @Override
    public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable("item.loopypowers.severance_stone.tooltip.flavor")
                .formatted(Formatting.GRAY));

        tooltip.add(Text.translatable("item.loopypowers.severance_stone.tooltip.desc")
                .formatted(Formatting.RED));
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (world.isClient) {
            return TypedActionResult.pass(stack);
        }

        if (!(user instanceof ServerPlayerEntity player)) {
            return TypedActionResult.pass(stack);
        }

        // No power - Translatable
        if (PowerManager.getPower(player) == null) {
            player.sendMessage(Text.translatable("message.loopypowers.severance_stone.no_power").formatted(Formatting.RED), true);
            return TypedActionResult.pass(stack);
        }

        // don't start multiple rituals - Translatable
        if (RitualManager.isActive(player)) {
            player.sendMessage(Text.translatable("message.loopypowers.ritual.already_active").formatted(Formatting.RED), true);
            return TypedActionResult.pass(stack);
        }

        // begin and consume
        RitualManager.startRitual(player, RitualManager.RitualType.SEVERANCE_RITUAL);

        // consume
        if (!player.getAbilities().creativeMode) {
            stack.decrement(1);
        }

        return TypedActionResult.success(stack);
    }
}