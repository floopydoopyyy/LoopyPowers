package com.yourname.loopypowers.item;

import com.yourname.loopypowers.Loopypowers;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import static com.yourname.loopypowers.block.ModBlocks.*;
import static com.yourname.loopypowers.item.ModItems.*;

public class ModItemGroups {

    public static final ItemGroup LOOPYPOWERS_GROUP = Registry.register(
            Registries.ITEM_GROUP,
            Identifier.of(Loopypowers.MOD_ID, "loopypowers_group"),
            FabricItemGroup.builder()
                    .icon(() -> new ItemStack(CELESTIAL_SHARD)) // icon item
                    .displayName(Text.literal("Loopy's Powers"))
                    .entries((context, entries) -> {
                        // items
                        entries.add(CELESTIAL_SHARD);
                        entries.add(EMPTY_VESTIGE);
                        entries.add(POWER_VESTIGE);
                        entries.add(ELEMENTAL_VESTIGE);
                        entries.add(LIFE_VESTIGE);
                        entries.add(MOTION_VESTIGE);
                        entries.add(RUIN_VESTIGE);
                        entries.add(SPACE_VESTIGE);
                        entries.add(MIND_VESTIGE);
                        entries.add(REFINED_CORE);
                        entries.add(PERFECTED_CORE);
                        entries.add(SEVERANCE_STONE);

                        // blocks
                        entries.add(CELESTIAL_ORE_ITEM);
                        entries.add(DEEPSLATE_CELESTIAL_ORE_ITEM);
                        entries.add(CELESTIAL_BLOCK_ITEM);
                        entries.add(THORN_VINE_ITEM);
                        entries.add(ICE_SPIKE_ITEM);
                        entries.add(CASINO_BARS_ITEM);
                        entries.add(CASINO_FLOOR_ITEM);

                        // misc
                        entries.add(WINGS_OF_VALOR);
                    })
                    .build()
    );

    public static void registerItemGroups() {
        // Just calling this class loads it
    }
}