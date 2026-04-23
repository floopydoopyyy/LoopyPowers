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

public class SpaceVestigeItem extends Item {

    public SpaceVestigeItem(Settings settings) {
        super(settings);
    }

    @Override
    public boolean hasGlint(ItemStack stack) {
        return true;
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world,
                              List<Text> tooltip, TooltipContext context) {

        tooltip.add(Text.literal("It hums with an unknown power...")
                .formatted(Formatting.GRAY));

        tooltip.add(Text.literal("Bestows a random space power to the subject.")
                .formatted(Formatting.DARK_PURPLE));

        tooltip.add(Text.literal("Available Powers:")
                .formatted(Formatting.GRAY));
        tooltip.add(Text.literal("Cosmic, Dimensional, Teleportation")
                .formatted(Formatting.GRAY));

        super.appendTooltip(stack, world, tooltip, context);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);

        if (!world.isClient) {
            ServerPlayerEntity player = (ServerPlayerEntity) user;

            // already has the power
            if (PowerManager.getPower(player) != null) {
                player.sendMessage(
                        Text.literal("§cYou already possess a power."),
                        true
                );
                return TypedActionResult.fail(stack);
            }

            // start ritual
            RitualManager.startRitual(player, RitualManager.RitualType.SPACE_VESTIGE);

            // consume
            if (!player.getAbilities().creativeMode) {
                stack.decrement(1);
            }
        }

        return TypedActionResult.success(stack, world.isClient());
    }
}