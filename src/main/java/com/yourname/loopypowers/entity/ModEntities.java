package com.yourname.loopypowers.entity;

import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.EntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

import static com.yourname.loopypowers.Loopypowers.MOD_ID;

public class ModEntities {

    private static <T extends net.minecraft.entity.Entity> EntityType<T> register(String name, EntityType.Builder<T> builder) {
        Identifier id = Identifier.of(MOD_ID, name);
        return Registry.register(Registries.ENTITY_TYPE, id, builder.build(name));
    }

    // FIREBALL - Using explicit lambda to resolve constructor error
    public static final EntityType<PowerFireballEntity> POWER_FIREBALL = register("power_fireball",
            EntityType.Builder.create((EntityType<PowerFireballEntity> type, net.minecraft.world.World world) -> new PowerFireballEntity(type, world), SpawnGroup.MISC)
                    .dimensions(1.0f, 1.0f)
                    .maxTrackingRange(4)
                    .trackingTickInterval(10)
    );

    // BLOOD CLOT
    public static final EntityType<BloodClotEntity> BLOOD_CLOT = register("blood_clot",
            EntityType.Builder.create(BloodClotEntity::new, SpawnGroup.MISC)
                    .dimensions(0.25f, 0.25f)
                    .maxTrackingRange(4)
                    .trackingTickInterval(10)
    );

    // SONIC BOLT
    public static final EntityType<SonicBoltEntity> SONIC_BOLT = register("sonic_bolt",
            EntityType.Builder.create(SonicBoltEntity::new, SpawnGroup.MISC)
                    .dimensions(0.25f, 0.25f)
                    .maxTrackingRange(4)
                    .trackingTickInterval(10)
    );

    // SHADOW STEP
    public static final EntityType<ShadowStepEntity> SHADOW_STEP = register("shadow_step",
            EntityType.Builder.create(ShadowStepEntity::new, SpawnGroup.MISC)
                    .dimensions(0.25f, 0.25f)
                    .maxTrackingRange(4)
                    .trackingTickInterval(10)
    );

    // COMPEL PROJECTILE
    public static final EntityType<CompelEntity> COMPEL_ENTITY = register("compel_entity",
            EntityType.Builder.create(CompelEntity::new, SpawnGroup.MISC)
                    .dimensions(0.5f, 0.5f)
                    .maxTrackingRange(4)
                    .trackingTickInterval(1)
    );

    // PUPPETRY PROJECTILE
    public static final EntityType<PuppetryEntity> PUPPETRY_ENTITY = register("puppetry_entity",
            EntityType.Builder.create(PuppetryEntity::new, SpawnGroup.MISC)
                    .dimensions(0.5f, 0.5f)
                    .maxTrackingRange(4)
                    .trackingTickInterval(1)
    );

    // COSMIC BLACK HOLE
    public static final EntityType<BlackHoleEntity> BLACK_HOLE_ENTITY = register("black_hole",
            EntityType.Builder.create(BlackHoleEntity::new, SpawnGroup.MISC)
                    .dimensions(0.5f, 0.5f)
    );

    // DIMENSIONAL DISPLACEMENT - FIXED: Changed type from BlackHoleEntity to DisplaceEntity
    public static final EntityType<DisplaceEntity> DISPLACE_ENTITY = register("displace_entity",
            EntityType.Builder.create(DisplaceEntity::new, SpawnGroup.MISC)
                    .dimensions(0.5f, 0.5f)
    );

    public static void init() {
        // Class load hook
    }
}