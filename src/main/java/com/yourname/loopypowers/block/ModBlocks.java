package com.yourname.loopypowers.block;

import com.yourname.loopypowers.Loopypowers;
import com.yourname.loopypowers.item.CelestialBlockItem;
import net.minecraft.block.AbstractBlock; // Added this import
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.Identifier;

// registers all modded blocks
public class ModBlocks {

    // THORN VINE
    public static final Block THORN_VINE = Registry.register(
            Registries.BLOCK,
            Identifier.of(Loopypowers.MOD_ID, "thorn_vine"),
            new ThornVineBlock(
                    AbstractBlock.Settings.copy(Blocks.COBWEB)
                            .strength(2.0f, 120.0f)               // time to break
                            .sounds(BlockSoundGroup.GRASS)        // sound
                            .noCollision()                        // cam walk through
                            .nonOpaque()
            )
    );

    public static final Item THORN_VINE_ITEM = Registry.register(
            Registries.ITEM,
            Identifier.of(Loopypowers.MOD_ID, "thorn_vine"),
            new BlockItem(THORN_VINE, new Item.Settings())
    );

    public static void init() {
        // inventory items now handled by ModItemGroups - this now just forces class to load
    }

    // ICE SPIKE
    public static final Block ICE_SPIKE = Registry.register(
            Registries.BLOCK,
            Identifier.of(Loopypowers.MOD_ID, "ice_spike"),
            new IceSpikeBlock(
                    AbstractBlock.Settings.copy(Blocks.POINTED_DRIPSTONE)
                            .luminance(state -> 6)          // light level
                            .strength(1.0f)                 // time to break
                            .sounds(BlockSoundGroup.GLASS)  // sound
            )
    );

    public static final Item ICE_SPIKE_ITEM = Registry.register(
            Registries.ITEM,
            Identifier.of(Loopypowers.MOD_ID, "ice_spike"),
            new BlockItem(ICE_SPIKE, new Item.Settings())
    );

    // CASINO BARS
    public static final Block CASINO_BARS = Registry.register(
            Registries.BLOCK,
            Identifier.of(Loopypowers.MOD_ID, "casino_bars"),
            new CasinoBarsBlock(
                    AbstractBlock.Settings.copy(Blocks.IRON_BARS)
                            .strength(35.0f, 1200.0f) // very blast resistant. little less stronger than obsidian
                            .sounds(BlockSoundGroup.METAL)
            )
    );
    public static final Item CASINO_BARS_ITEM = Registry.register(
            Registries.ITEM,
            Identifier.of(Loopypowers.MOD_ID, "casino_bars"),
            new BlockItem(CASINO_BARS, new Item.Settings())
    );

    // CASINO FLOOR
    public static final Block CASINO_FLOOR = Registry.register(
            Registries.BLOCK,
            Identifier.of(Loopypowers.MOD_ID, "casino_floor"),
            new CasinoFloorBlock(
                    AbstractBlock.Settings.copy(Blocks.OBSIDIAN)
                            .strength(25.0f, 1200.0f) // very blast resistant. little less stronger than obsidian
                            .sounds(BlockSoundGroup.STONE)
            )
    );
    public static final Item CASINO_FLOOR_ITEM = Registry.register(
            Registries.ITEM,
            Identifier.of(Loopypowers.MOD_ID, "casino_floor"),
            new BlockItem(CASINO_FLOOR, new Item.Settings())
    );

    // CELESTIAL ORE
    public static final Block CELESTIAL_ORE = Registry.register(
            Registries.BLOCK,
            Identifier.of(Loopypowers.MOD_ID, "celestial_ore"),
            new CelestialOreBlock(
                    AbstractBlock.Settings.copy(Blocks.DIAMOND_ORE)
                            .strength(5.0f, 6.0f)
                            .requiresTool()
                            .luminance(state -> 12)
            )
    );
    public static final Item CELESTIAL_ORE_ITEM = Registry.register(
            Registries.ITEM,
            Identifier.of(Loopypowers.MOD_ID, "celestial_ore"),
            new BlockItem(CELESTIAL_ORE, new Item.Settings())
    );

    public static final Block DEEPSLATE_CELESTIAL_ORE = Registry.register(
            Registries.BLOCK,
            Identifier.of(Loopypowers.MOD_ID, "deepslate_celestial_ore"),
            new CelestialOreBlock(
                    AbstractBlock.Settings.copy(Blocks.DEEPSLATE_DIAMOND_ORE)
                            .strength(5.5f, 7.0f)
                            .requiresTool()
                            .luminance(state -> 12)
            )
    );

    public static final Item DEEPSLATE_CELESTIAL_ORE_ITEM = Registry.register(
            Registries.ITEM,
            Identifier.of(Loopypowers.MOD_ID, "deepslate_celestial_ore"),
            new BlockItem(DEEPSLATE_CELESTIAL_ORE, new Item.Settings())
    );

    // CELESTIAL BLOCK
    public static final Block CELESTIAL_BLOCK = Registry.register(
            Registries.BLOCK,
            Identifier.of(Loopypowers.MOD_ID, "celestial_block"),
            new CelestialBlock(
                    AbstractBlock.Settings.copy(Blocks.NETHERITE_BLOCK)
                            .strength(5.0f, 6.0f)
                            .requiresTool()
                            .luminance(state -> 12)
            )
    );
    public static final Item CELESTIAL_BLOCK_ITEM = Registry.register(
            Registries.ITEM,
            Identifier.of(Loopypowers.MOD_ID, "celestial_block"),
            new CelestialBlockItem(CELESTIAL_BLOCK, new Item.Settings())
    );
}