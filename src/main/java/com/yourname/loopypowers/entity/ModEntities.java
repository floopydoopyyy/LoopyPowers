package com.yourname.loopypowers.entity;

import com.yourname.loopypowers.Loopypowers;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.EntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

import static com.yourname.loopypowers.Loopypowers.MOD_ID;

// ==================================
// REGISTERS ALL MODDED ENTITIES
// ==============================
// these equals things are fun


public class ModEntities {
    // FIREBALL
    public static final EntityType<PowerFireballEntity> POWER_FIREBALL = Registry.register(
            Registries.ENTITY_TYPE,
            new Identifier(MOD_ID, "power_fireball"),
            FabricEntityTypeBuilder.<PowerFireballEntity>create(SpawnGroup.MISC, PowerFireballEntity::new)
                    .dimensions(EntityDimensions.fixed(1.0f, 1.0f)) // similar-ish size to fireball
                    .trackRangeBlocks(64)
                    .trackedUpdateRate(10)
                    .build()
    );
    // BLOOD CLOT
    public static final EntityType<BloodClotEntity> BLOOD_CLOT = Registry.register(
            Registries.ENTITY_TYPE,
            new Identifier(MOD_ID, "blood_clot"),
            FabricEntityTypeBuilder.create(SpawnGroup.MISC, BloodClotEntity::new)
                    .dimensions(EntityDimensions.fixed(0.25f, 0.25f)) // IMPORTANT
                    .trackRangeChunks(4)
                    .trackedUpdateRate(10)
                    .build()
    );
    // SONIC BOLT
    public static final EntityType<SonicBoltEntity> SONIC_BOLT = Registry.register(
            Registries.ENTITY_TYPE,
            new Identifier(MOD_ID, "sonic_bolt"),
            FabricEntityTypeBuilder.<SonicBoltEntity>create(SpawnGroup.MISC, SonicBoltEntity::new)
                    .dimensions(EntityDimensions.fixed(0.25f, 0.25f)) // this size is difficult
                    .trackRangeChunks(4)
                    .trackedUpdateRate(10)
                    .build()
    );

    // SHADOW STEP
    public static final EntityType<ShadowStepEntity> SHADOW_STEP =
            Registry.register(
                    Registries.ENTITY_TYPE,
                    new Identifier(MOD_ID, "shadow_step"),
                    FabricEntityTypeBuilder.<ShadowStepEntity>create(SpawnGroup.MISC, ShadowStepEntity::new)
                            .dimensions(EntityDimensions.fixed(0.25f, 0.25f))
                            .trackRangeBlocks(64)
                            .trackedUpdateRate(10)
                            .build()
            );

    // COMPEL PROJECTILE
    public static final EntityType<CompelEntity> COMPEL_ENTITY = Registry.register(
            Registries.ENTITY_TYPE,
            new Identifier("loopypowers", "compel_entity"),
            FabricEntityTypeBuilder.<CompelEntity>create(SpawnGroup.MISC, CompelEntity::new)
                    .dimensions(EntityDimensions.fixed(0.5f, 0.5f))
                    .trackRangeBlocks(64)
                    .trackedUpdateRate(1)
                    .build()
    );
    // PUPPETRY PROJECTILE
    public static final EntityType<CompelEntity> PUPPETRY_ENTITY = Registry.register(
            Registries.ENTITY_TYPE,
            new Identifier("loopypowers", "puppetry_entity"),
            FabricEntityTypeBuilder.<CompelEntity>create(SpawnGroup.MISC, CompelEntity::new)
                    .dimensions(EntityDimensions.fixed(0.5f, 0.5f))
                    .trackRangeBlocks(64)
                    .trackedUpdateRate(1)
                    .build()
    );

    // COSMIC BLACK HOLE
    public static final EntityType<BlackHoleEntity> BLACK_HOLE_ENTITY = Registry.register(
            Registries.ENTITY_TYPE,
            new Identifier(Loopypowers.MOD_ID, "black_hole"),
            FabricEntityTypeBuilder.create(SpawnGroup.MISC, BlackHoleEntity::new)
                    .dimensions(EntityDimensions.fixed(0.5f, 0.5f))
                    .build()
    );

    // DIMENSIONAL DISPLACEMENT
    public static final EntityType<BlackHoleEntity> DISPLACE_ENTITY = Registry.register(
            Registries.ENTITY_TYPE,
            new Identifier(Loopypowers.MOD_ID, "displace_entity"),
            FabricEntityTypeBuilder.create(SpawnGroup.MISC, BlackHoleEntity::new)
                    .dimensions(EntityDimensions.fixed(0.5f, 0.5f))
                    .build()
    );

    public static void init() {
        // just forces class load
    }
}