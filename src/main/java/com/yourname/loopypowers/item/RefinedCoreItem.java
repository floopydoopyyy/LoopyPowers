package com.yourname.loopypowers.item;

import com.yourname.loopypowers.manager.PowerManager;
import com.yourname.loopypowers.ritual.RitualManager;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class RefinedCoreItem extends Item {

    public RefinedCoreItem(Settings settings) {
        super(settings);
    }

    @Override
    public boolean hasGlint(ItemStack stack) {
        return true;
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world,
                              List<Text> tooltip, TooltipContext context) {

        tooltip.add(Text.literal("A powerful item capable of strengthening your connection.")
                .formatted(Formatting.GRAY));

        tooltip.add(Text.literal("Upgrades a power from Level 1 to 2.")
                .formatted(Formatting.DARK_PURPLE));

        super.appendTooltip(stack, world, tooltip, context);
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
                    Text.literal("§cYou are powerless, the item has no effect"),
                    true
            );
            return TypedActionResult.fail(stack);
        }

        int level = PowerManager.getLevel(player);

        // too high
        if (level > 1) {
            player.sendMessage(
                    Text.literal("§cYour connection Level is already beyond this point, the item has no effect."),
                    true
            );
            return TypedActionResult.fail(stack);
        }

        // somehow below 1
        if (level < 1) {
            player.sendMessage(
                    Text.literal("§cThis is an exception: Your power level is somehow below 1.."),
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

        return TypedActionResult.success(stack, world.isClient());
    }
}