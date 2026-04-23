package com.yourname.loopypowers.generation;
import com.yourname.loopypowers.Loopypowers;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.world.gen.GenerationStep;
import net.minecraft.world.gen.feature.PlacedFeature;

public class ModOreGeneration {
    // jsons in resources handle rarity
    // placed_feature - rarity, spawn locations
    // configured_feature - vein size, air exposure

    public static void generateOres() {

        RegistryKey<PlacedFeature> placedFeature = RegistryKey.of(
                RegistryKeys.PLACED_FEATURE,
                new Identifier(Loopypowers.MOD_ID, "deepslate_celestial_ore")
        );

        BiomeModifications.addFeature(
                BiomeSelectors.foundInOverworld(),
                GenerationStep.Feature.UNDERGROUND_ORES,
                placedFeature
        );
    }
}