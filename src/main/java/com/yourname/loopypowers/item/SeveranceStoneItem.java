package com.yourname.loopypowers.item;

import com.yourname.loopypowers.manager.PowerManager;
import com.yourname.loopypowers.ritual.RitualManager;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class SeveranceStoneItem extends Item {

    public SeveranceStoneItem(Settings settings) {
        super(settings);
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world,
                              List<Text> tooltip, TooltipContext context) {

        tooltip.add(Text.literal("It emits a cursed energy.")
                .formatted(Formatting.GRAY));

        tooltip.add(Text.literal("Using this item will remove your current power.")
                .formatted(Formatting.RED));

        super.appendTooltip(stack, world, tooltip, context);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (world.isClient) {
            return TypedActionResult.pass(user.getStackInHand(hand));
        }

        if (!(user instanceof ServerPlayerEntity player)) {
            return TypedActionResult.pass(user.getStackInHand(hand));
        }

        // No power
        if (PowerManager.getPower(player) == null) {
            player.sendMessage(Text.literal("§cYou do not have a power."), true);
            return TypedActionResult.pass(user.getStackInHand(hand));
        }

        // don't start multiple rituals
        if (RitualManager.isActive(player)) {
            player.sendMessage(Text.literal("§cA ritual is already in progress."), true);
            return TypedActionResult.pass(user.getStackInHand(hand));
        }

        // begin and consume
        RitualManager.startRitual(player, RitualManager.RitualType.SEVERANCE_RITUAL);

        // consume
        if (!player.getAbilities().creativeMode) {
            stack.decrement(1);
        }

        return TypedActionResult.success(user.getStackInHand(hand));
    }
}