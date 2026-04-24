package com.yourname.loopypowers.item;

import com.yourname.loopypowers.Loopypowers;
import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class ModItems {

    // WINGS OF VALOR
    public static final Item WINGS_OF_VALOR = Registry.register(
            Registries.ITEM,
            new Identifier(Loopypowers.MOD_ID, "wings_of_valor"),
            new WingsOfValorItem(new Item.Settings().maxCount(1).maxDamage(432))
    );

    // CELESTIAL SHARD
    public static final Item CELESTIAL_SHARD = Registry.register(
            Registries.ITEM,
            new Identifier(Loopypowers.MOD_ID, "celestial_shard"),
            new CelestialShardItem(new FabricItemSettings())
    );

    // RITUAL VESTIGES
    public static final Item POWER_VESTIGE = Registry.register(
            Registries.ITEM,
            new Identifier(Loopypowers.MOD_ID, "power_vestige"),
            new PowerVestigeItem(new FabricItemSettings().maxCount(1))
    );
    public static final Item ELEMENTAL_VESTIGE = Registry.register(
            Registries.ITEM,
            new Identifier(Loopypowers.MOD_ID, "elemental_vestige"),
            new ElementalVestigeItem(new FabricItemSettings().maxCount(1))
    );
    public static final Item LIFE_VESTIGE = Registry.register(
            Registries.ITEM,
            new Identifier(Loopypowers.MOD_ID, "life_vestige"),
            new LifeVestigeItem(new FabricItemSettings().maxCount(1))
    );
    public static final Item RUIN_VESTIGE = Registry.register(
            Registries.ITEM,
            new Identifier(Loopypowers.MOD_ID, "ruin_vestige"),
            new RuinVestigeItem(new FabricItemSettings().maxCount(1))
    );
    public static final Item MOTION_VESTIGE = Registry.register(
            Registries.ITEM,
            new Identifier(Loopypowers.MOD_ID, "motion_vestige"),
            new MotionVestigeItem(new FabricItemSettings().maxCount(1))
    );
    public static final Item SPACE_VESTIGE = Registry.register(
            Registries.ITEM,
            new Identifier(Loopypowers.MOD_ID, "space_vestige"),
            new SpaceVestigeItem(new FabricItemSettings().maxCount(1))
    );
    public static final Item MIND_VESTIGE = Registry.register(
            Registries.ITEM,
            new Identifier(Loopypowers.MOD_ID, "mind_vestige"),
            new MindVestigeItem(new FabricItemSettings().maxCount(1))
    );
    // EMPTY VESTIGE
    public static final Item EMPTY_VESTIGE = Registry.register(
            Registries.ITEM,
            new Identifier(Loopypowers.MOD_ID, "empty_vestige"),
            new EmptyVestigeItem(new FabricItemSettings())
    );
    // SEVERANCE STONE
    public static final Item SEVERANCE_STONE = Registry.register(
            Registries.ITEM,
            new Identifier(Loopypowers.MOD_ID, "severance_stone"),
            new SeveranceStoneItem(new FabricItemSettings())
    );
    // CORES
    public static final Item REFINED_CORE = Registry.register(
            Registries.ITEM,
            new Identifier(Loopypowers.MOD_ID, "refined_celestial_core"),
            new RefinedCoreItem(new FabricItemSettings())
    );
    public static final Item PERFECTED_CORE = Registry.register(
            Registries.ITEM,
            new Identifier(Loopypowers.MOD_ID, "perfected_celestial_core"),
            new PerfectedCoreItem(new FabricItemSettings())
    );

    public static void register() {
        // init trigger
        ModItemGroups.registerItemGroups(); // register all items
    }
}