package com.yourname.loopypowers.item;

import com.yourname.loopypowers.manager.PowerManager;
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
        if (world.isClient) {
            return TypedActionResult.pass(user.getStackInHand(hand));
        }

        if (!(user instanceof ServerPlayerEntity player)) {
            return TypedActionResult.pass(user.getStackInHand(hand));
        }


        boolean hadPower = PowerManager.getPower(player) != null;

        if (!hadPower) {
            player.sendMessage(Text.literal("§cYou do not have a power."), true);
            return TypedActionResult.pass(user.getStackInHand(hand));
        }

        // remove power
        PowerManager.removePower(player); // cleanup handled by this method

        // consume item
        if (!player.getAbilities().creativeMode) {
            user.getStackInHand(hand).decrement(1);
        }

        player.sendMessage(Text.literal("§cYour power has been stripped..."), true);

        return TypedActionResult.success(user.getStackInHand(hand));
    }
}