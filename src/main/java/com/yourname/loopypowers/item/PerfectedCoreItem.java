package com.yourname.loopypowers.item;

import com.yourname.loopypowers.manager.PowerManager;
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

public class PerfectedCoreItem extends Item {

    public PerfectedCoreItem(Settings settings) {
        super(settings);
    }

    @Override
    public boolean hasGlint(ItemStack stack) {
        return true;
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world,
                              List<Text> tooltip, TooltipContext context) {

        tooltip.add(Text.literal("A legendary item capable of completing your connection.")
                .formatted(Formatting.GRAY));

        tooltip.add(Text.literal("Upgrades a power from Level 2 to 3.")
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
        if (level > 3) {
            player.sendMessage(
                    Text.literal("§cYou have already maxed out the connection level, the item has no effect."),
                    true
            );
            return TypedActionResult.fail(stack);
        }

        // Below 2
        if (level < 2) {
            player.sendMessage(
                    Text.literal("§cYour connection level is not high enough to use this, the item has no effect."),
                    true
            );
            return TypedActionResult.fail(stack);
        }

        // apply it
        PowerManager.setLevel(player, 3);

        player.sendMessage(
                Text.literal("§dYour connection has been strengthened to Level 3."),
                false
        );

        // sound
        world.playSound(
                null,
                player.getBlockPos(),
                net.minecraft.sound.SoundEvents.ENTITY_PLAYER_LEVELUP,
                player.getSoundCategory(),
                1.0f,
                1.2f
        );

        // consume item
        if (!player.getAbilities().creativeMode) {
            stack.decrement(1);
        }

        return TypedActionResult.success(stack, world.isClient());
    }
}