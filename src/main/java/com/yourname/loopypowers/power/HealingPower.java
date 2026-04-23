package com.yourname.loopypowers.power;

import com.yourname.loopypowers.damage.ModDamageTypes;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;

import java.util.Iterator;

import static com.yourname.loopypowers.power.BloodPower.ACTIVE_BLEEDS;

public class HealingPower implements Power {

    /* ============================================================
       CONSTANTS / TAGS
       ============================================================ */

    private static final String PASSIVE_TAG = "hf_regen_delay_";
    private static final String ABSORB_TAG = "hf_absorb_";
    private static final String ULT_TAG = "hf_ult_";


    /* ============================================================
       BASIC
       ============================================================ */

    @Override
    public void onAssign(ServerPlayerEntity player) {
        // remove any existing passive tags (safety)
        removeTagPrefix(player, PASSIVE_TAG);
        // enable passive
        player.getCommandTags().add(PASSIVE_TAG + PASSIVE_DELAY);
    }

    @Override
    public void onTick(ServerPlayerEntity player) {
        handlePassive(player);
        handleAbsorb(player);
        handleUltimate(player);
    }

    @Override
    public void onHit(ServerPlayerEntity attacker, LivingEntity target) {
        float ls = getLifesteal(attacker); // ult lifesteal
        if (ls > 0) {
            attacker.heal(ls * 2.0f); // tweak multiplier if needed
        }
    }

    /* ============================================================
       PASSIVE
       ============================================================ */

    private static final int PASSIVE_DELAY = 100; // delay before healing
    private static final float HEALING = 0.25f; // amount healed per tick

    private void handlePassive(ServerPlayerEntity player) {
        Iterator<String> it = player.getCommandTags().iterator();
        String newTag = null;

        while (it.hasNext()) {
            String tag = it.next();

            if (tag.startsWith(PASSIVE_TAG)) {
                int ticks = Integer.parseInt(tag.substring(PASSIVE_TAG.length())) - 1;
                it.remove();

                if (ticks <= 0) {
                    float maxHeal = player.getMaxHealth();

                    // small continuous heal
                    player.heal(HEALING); // not regeneration as they would not regen when hungry

                    // keep tag to reapply healing
                    newTag = PASSIVE_TAG + 0;
                    break;
                }

                newTag = PASSIVE_TAG + ticks;
                break;
            }
        }

        if (newTag != null) {
            player.getCommandTags().add(newTag);
        }
    }

    // called from global damage hook
    public static void resetPassiveDelay(ServerPlayerEntity player) {
        removeTagPrefix(player, PASSIVE_TAG);
        player.getCommandTags().add(PASSIVE_TAG + PASSIVE_DELAY);
    }

    /* ============================================================
       PRIMARY
       ============================================================ */

    @Override
    public void activatePrimary(ServerPlayerEntity player) {
        ServerWorld w = player.getServerWorld();

        int removed = 0; // how many debuffs purged

        // POWER-BASED DEBUFFS

        // blood
        removed += removeBleed(player) ? 1 : 0;

        // these are cleared first as they may have associated status effects
        removed += removeTagEffects(player,
                // sound
                "sd_resonated_",
                "sd_dampened_",
                "sd_stun_lock_",
                // ice
                "ice_frz_p_",   // freeze points
                "ice_frz_d_"   // decay timer
                //"ice_frz_i_"    // immunity timer
        );

        // Copy effects first (avoids concurrent modification issues)
        for (StatusEffectInstance effect : new java.util.ArrayList<>(player.getStatusEffects())) {

            if (effect.getEffectType().isBeneficial()) continue;

            player.removeStatusEffect(effect.getEffectType());
            removed++;
        }

        if (player.isOnFire()) {
            player.extinguish(); // removes fire
            removed++;
        }

        // Heal based on removed effects
        float healAmount = 4.0f + (removed * 2.0f);
        // cap the heals
        if (healAmount > 10) {
            healAmount = 10;
        }
        player.heal(healAmount);

        // particles
        w.spawnParticles(
                ParticleTypes.ELECTRIC_SPARK,
                player.getX(),
                player.getBodyY(0.5),
                player.getZ(),
                40,
                0.6, 0.8, 0.6,
                0.05
        );

        w.spawnParticles(
                ParticleTypes.FLASH,
                player.getX(),
                player.getBodyY(0.5),
                player.getZ(),
                3,
                0.7, 0.9, 0.7,
                0.1
        );

        // sound
        w.playSound(null, player.getBlockPos(),
                SoundEvents.ENTITY_PLAYER_LEVELUP,
                player.getSoundCategory(),
                0.8f, 0.6f);

        w.playSound(null, player.getBlockPos(),
                SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE,
                player.getSoundCategory(),
                0.6f, 1.2f);

        // effects
        player.addStatusEffect(new StatusEffectInstance(
                StatusEffects.GLOWING,
                10,
                0,
                true, false, true
        ));


        player.swingHand(Hand.MAIN_HAND, true);
    }

    // cleanse power-based effects
    private static int removeTagEffects(ServerPlayerEntity player, String... prefixes) {
        int removed = 0;

        Iterator<String> it = player.getCommandTags().iterator();

        while (it.hasNext()) {
            String tag = it.next();

            for (String prefix : prefixes) {
                if (tag.startsWith(prefix)) {
                    it.remove();
                    removed++;
                    break;
                }
            }
        }

        return removed;
    }

    public static boolean removeBleed(LivingEntity e) {
        return ACTIVE_BLEEDS.remove(e.getUuid()) != null;
    }

    /* ============================================================
       SECONDARY
       ============================================================ */

    // Damage
    private static final float BURST_BASE_DAMAGE = 4.0f;
    private static final float BURST_DAMAGE_PER_STORED = 0.6f;
    private static final float BURST_MAX_SCALING = 20.0f; // cap

    // Expelled debuff tuning
    private static final int EXPELLED_DURATION = 60; // 3 seconds
    private static final int EXPELLED_AMPLIFIER = 0; // always low level

    // Radius
    private static final double BURST_RADIUS = 5.0;

    // Knockback
    private static final float KB_BASE = 0.2f;          // minimum push
    private static final float KB_PER_STORED = 0.03f;   // scaling per absorbed point
    private static final float KB_MAX = 1.2f;           // cap
    private static final float KB_VERTICAL = 0.35f;     //  lift
    private static final float KB_MIN_FALLOFF = 0.2f;   // prevents zero knockback at edge

    // visual
    private static final int VFX_BASE_PARTICLES = 12;
    private static final int VFX_PARTICLES_PER_STORED = 1;
    private static final int VFX_MAX_PARTICLES = 50;

    private static final float VFX_SHINE_THRESHOLD = 8.0f;
    private static final float VFX_SUPER_SHINE_THRESHOLD = 16.0f;

    private static final int EXPELLED_PARTICLES = 6;
    private static final double EXPELLED_PARTICLE_SPREAD = 0.4;

    private static final String ABSORB_STORED = "hf_absorb_stored_";

    @Override
    public void activateSecondary(ServerPlayerEntity player) {
        // reset old absorbtions
        removeTagPrefix(player, ABSORB_STORED);
        setStoredAbsorb(player, 0f);
        // apply tag
        player.getCommandTags().add(ABSORB_TAG + ABSORB_DURATION);
    }

    private void handleAbsorb(ServerPlayerEntity player) {
        Iterator<String> it = player.getCommandTags().iterator();
        String newTag = null;

        while (it.hasNext()) {
            String tag = it.next();

            if (tag.startsWith(ABSORB_TAG) && !tag.startsWith(ABSORB_STORED)) {

                // constant fx
                ServerWorld w = player.getServerWorld();

                w.spawnParticles(
                        ParticleTypes.ELECTRIC_SPARK,
                        player.getX(),
                        player.getBodyY(0.5),
                        player.getZ(),
                        2,                  // per tick
                        0.5, 0.6, 0.5,      // spread
                        0.02
                );

                int ticks;
                try {
                    ticks = Integer.parseInt(tag.substring(ABSORB_TAG.length())) - 1;
                } catch (NumberFormatException e) {
                    // skip bad tag instead of crashing
                    it.remove();
                    continue;
                }

                it.remove();

                if (ticks <= 0) {
                    releaseBurst(player);
                    return;
                }

                newTag = ABSORB_TAG + ticks;

                player.addStatusEffect(new StatusEffectInstance(
                        StatusEffects.SLOWNESS,
                        10,
                        3,
                        true, false, true
                ));

                break;
            }
        }

        if (newTag != null) {
            player.getCommandTags().add(newTag);
        }
    }

    private static void addToStoredAbsorb(ServerPlayerEntity p, float amount) {
        float current = getStoredAbsorb(p);
        setStoredAbsorb(p, current + amount);
    }

    private static float getStoredAbsorb(ServerPlayerEntity p) {
        for (String tag : p.getCommandTags()) {
            if (tag.startsWith(ABSORB_STORED)) {
                try {
                    return Float.parseFloat(tag.substring(ABSORB_STORED.length()));
                } catch (Exception ignored) {}
            }
        }
        return 0f;
    }

    private static void setStoredAbsorb(ServerPlayerEntity p, float value) {
        removeTagPrefix(p, ABSORB_STORED);
        p.getCommandTags().add(ABSORB_STORED + value);
    }

    private void releaseBurst(ServerPlayerEntity player) {
        ServerWorld w = player.getServerWorld();

        float stored = getStoredAbsorb(player);

        // cap scaling
        float capped = Math.min(stored, BURST_MAX_SCALING);

        // damage
        float burstDamage = BURST_BASE_DAMAGE + (capped * BURST_DAMAGE_PER_STORED);

        // knockback scaling
        float kbStrength = KB_BASE + (capped * KB_PER_STORED);
        if (kbStrength > KB_MAX) kbStrength = KB_MAX;

        double radius = BURST_RADIUS;

       // VISUAL SCALING

        int particleCount = VFX_BASE_PARTICLES + (int)(capped * VFX_PARTICLES_PER_STORED);
        if (particleCount > VFX_MAX_PARTICLES) particleCount = VFX_MAX_PARTICLES;

        // main explosion
        double px = player.getX();
        double py = player.getBodyY(0.5);
        double pz = player.getZ();

        int emitterCount = 1 + (int)(capped * 0.25f); // how many - should be rounded
        if (emitterCount > 6) emitterCount = 6;       // cap
        double spread = 0.5 + (capped * 0.1); // spread

        // main explosion
        for (int i = 0; i < emitterCount; i++) {

            double ox = (w.random.nextDouble() * 2 - 1) * spread;
            double oy = (w.random.nextDouble() * 2 - 1) * spread * 0.6; // less vertical
            double oz = (w.random.nextDouble() * 2 - 1) * spread;

            w.spawnParticles(
                    ParticleTypes.EXPLOSION_EMITTER,
                    px + ox,
                    py + oy,
                    pz + oz,
                    1,      // always 1 for emitter
                    0, 0, 0,
                    0
            );
        }

        // mid-tier shine
        if (capped >= VFX_SHINE_THRESHOLD) {
            spawnBurst(w, px, py, pz,
                    40,
                    0.25,
                    0.8,
                    ParticleTypes.END_ROD);
        }

        if (capped >= VFX_SUPER_SHINE_THRESHOLD) {
            spawnBurst(w, px, py, pz,
                    10,
                    0.35,
                    0.8,
                    ParticleTypes.FLASH);

            spawnBurst(w, px, py, pz,
                    20,
                    0.3,
                    0.5,
                    ParticleTypes.TOTEM_OF_UNDYING);
        }

        // DAMAGE + KNOCKBACK
        for (LivingEntity e : w.getEntitiesByClass(
                LivingEntity.class,
                player.getBoundingBox().expand(radius),
                LivingEntity::isAlive)) {

            if (e == player) continue;

            // damage
            e.damage(player.getDamageSources().magic(), burstDamage);

            // knockback
            double dx = e.getX() - player.getX();
            double dz = e.getZ() - player.getZ();

            double distSq = dx * dx + dz * dz;

            if (distSq > 0.0001) {
                double dist = Math.sqrt(distSq);

                dx /= dist;
                dz /= dist;

                double falloff = 1.0 - (dist / radius);
                falloff = Math.max(KB_MIN_FALLOFF, falloff);

                double finalKb = kbStrength * falloff;

                e.addVelocity(
                        dx * finalKb,
                        KB_VERTICAL,
                        dz * finalKb
                );

                e.velocityModified = true;
            }

            // cleanse
            applyAndCleanse(player, e);
        }

        // clear stored
        removeTagPrefix(player, ABSORB_STORED);

       //SOUND SCALING
        float pitch = 0.9f + (capped * 0.02f);
        if (pitch > 1.5f) pitch = 1.5f;

        w.playSound(null, player.getBlockPos(),
                SoundEvents.ENTITY_GENERIC_EXPLODE,
                player.getSoundCategory(),
                1.0f, pitch);
    }

    private void applyAndCleanse(ServerPlayerEntity player, LivingEntity target) {
        ServerWorld w = player.getServerWorld();

        // Copy player's effects to avoid concurrent modification
        java.util.List<StatusEffectInstance> effects =
                new java.util.ArrayList<>(player.getStatusEffects());

        for (StatusEffectInstance effect : effects) {
            // only take negative effects
            if (effect.getEffectType().isBeneficial()) continue;

            // apply weakened version
            target.addStatusEffect(new StatusEffectInstance(
                    effect.getEffectType(),
                    EXPELLED_DURATION,
                    EXPELLED_AMPLIFIER,
                    true, false, true
            ));

            // remove from player
            player.removeStatusEffect(effect.getEffectType());

            // more visuals
            double dx = target.getX() - player.getX();
            double dy = target.getBodyY(0.5) - player.getBodyY(0.5);
            double dz = target.getZ() - player.getZ();

            double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (len > 0.0001) {
                dx /= len;
                dy /= len;
                dz /= len;

                for (int i = 0; i < EXPELLED_PARTICLES; i++) {
                    w.spawnParticles(
                            ParticleTypes.SMOKE,
                            player.getX(),
                            player.getBodyY(0.5),
                            player.getZ(),
                            0,
                            dx * 0.4,
                            dy * 0.4,
                            dz * 0.4,
                            1.0
                    );
                }
            }
        }

        // also treat fire like an effect
        if (player.isOnFire()) {
            player.extinguish();

            target.setOnFireFor(2);

            w.spawnParticles(
                    ParticleTypes.FLAME,
                    player.getX(),
                    player.getBodyY(0.5),
                    player.getZ(),
                    8,
                    0.3, 0.4, 0.3,
                    0.02
            );
        }
    }

    private void spawnBurst(ServerWorld w,
                            double x, double y, double z,
                            int count,
                            double speed,
                            double spread,
                            net.minecraft.particle.ParticleEffect particle) {

        for (int i = 0; i < count; i++) {

            // random direction (biased outward)
            double dx = w.random.nextGaussian();
            double dy = w.random.nextGaussian() * 0.6; // less vertical clustering
            double dz = w.random.nextGaussian();

            double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (len < 0.0001) continue;

            dx /= len;
            dy /= len;
            dz /= len;

            // offset
            double distance = spread * (0.5 + w.random.nextDouble()); // should avoid clustering

            double px = x + dx * distance;
            double py = y + dy * distance;
            double pz = z + dz * distance;

            // velocity variation below:
            double velocityScale = speed * (0.8 + w.random.nextDouble() * 0.7);

            double vx = dx * velocityScale;
            double vy = dy * velocityScale;
            double vz = dz * velocityScale;

            w.spawnParticles(
                    particle,
                    px, py, pz,
                    0,
                    vx, vy, vz,
                    1.0
            );
        }
    }

    public static boolean handleAbsorbDamage(ServerPlayerEntity player, float amount) {

        // check if absorb is active
        for (String tag : player.getCommandTags()) {
            if (tag.startsWith(ABSORB_TAG)) {

                // split damage
                float absorbed = amount * 0.8f;
                float applied = amount * 0.2f;

                // store absorbed portion
                addToStoredAbsorb(player, absorbed);

                // uses custom damage type to avoid recursion
                player.damage(
                        player.getDamageSources().create(ModDamageTypes.ABSORB),
                        applied
                );

                // fx
                player.getServerWorld().spawnParticles(
                        ParticleTypes.TOTEM_OF_UNDYING,
                        player.getX(), player.getBodyY(0.5), player.getZ(),
                        2, 0.2, 0.3, 0.2, 0.01
                );

                return true; // cancel original damage
            }
        }
        return false;
    }

    /* ============================================================
       ULTIMATE - ADAPTIVE SURVIVABILITY
       ============================================================ */

    private static final String ULT_PHASE = "hf_ult_phase_";
    private static final String LS_TAG = "hf_ls_";
    private static final String SMOOTH_TAG = "hf_smooth_";

    private static final int ABSORB_DURATION = 40;
    private static final int ULT_DURATION = 180;

    private static final int EFFECT_REFRESH = 30;

    // lifesteal scaling
    private static final float LS_HIGH = 0.35f;
    private static final float LS_MID = 0.25f;

    // smoothing (damage reduction %)
    private static final float SMOOTH_MID = 0.15f;
    private static final float SMOOTH_LOW = 0.25f;

    // cleansing interval
    private static final int CLEANSE_INTERVAL = 20;

    // 5 = 100%, 1 = 20%
    private int getPhase(float hp) {
        if (hp >= 1.0f) return 5;     // Overflow
        if (hp >= 0.8f) return 4;     // Exceptional
        if (hp >= 0.6f) return 3;     // Equilibrium
        if (hp >= 0.4f) return 2;     // Inadequate
        return 1;                     // Exposed
    }

    @Override
    public void activateUltimate(ServerPlayerEntity player) {
        player.getCommandTags().add(ULT_TAG + ULT_DURATION);
    }

    private void handleUltimate(ServerPlayerEntity player) {
        Iterator<String> it = player.getCommandTags().iterator();
        String newTag = null;

        while (it.hasNext()) {
            String tag = it.next();

            if (tag.startsWith(ULT_TAG) && !tag.startsWith(ULT_PHASE)) {
                int ticks = Integer.parseInt(tag.substring(ULT_TAG.length())) - 1;
                it.remove();

                ServerWorld w = player.getServerWorld();

                // constant particles
                w.spawnParticles(
                        ParticleTypes.FIREWORK,
                        player.getX(),
                        player.getBodyY(0.5),
                        player.getZ(),
                        2,              // low count
                        0.4, 0.6, 0.4,  // spread
                        0.01
                );

                // occasional other
                if (ticks % 10 == 0) {
                    w.spawnParticles(
                            ParticleTypes.END_ROD,
                            player.getX(),
                            player.getBodyY(0.5),
                            player.getZ(),
                            8,
                            0.6, 0.8, 0.6,
                            0.05
                    );
                }

                if (ticks <= 0) return;

                newTag = ULT_TAG + ticks;

                float hpPercent = player.getHealth() / player.getMaxHealth();

                int phase = getPhase(hpPercent);
                int prevPhase = getStoredPhase(player);

                // detect phase change
                if (phase != prevPhase) {
                    setStoredPhase(player, phase);

                    if (phase == 1) { // EXPOSED ENTRY BURST
                        player.addStatusEffect(new StatusEffectInstance(
                                StatusEffects.SPEED,
                                40,
                                2,
                                true, false, true
                        ));

                        w.spawnParticles(
                                ParticleTypes.FLASH,
                                player.getX(),
                                player.getBodyY(0.5),
                                player.getZ(),
                                10,
                                0.5, 0.6, 0.5,
                                0.1
                        );

                        w.playSound(null, player.getBlockPos(),
                                SoundEvents.ENTITY_ENDER_DRAGON_GROWL,
                                player.getSoundCategory(),
                                0.8f, 1.4f);
                    }
                }

                applyPhaseEffects(player, phase, ticks);

                break;
            }
        }

        if (newTag != null) {
            player.getCommandTags().add(newTag);
        }
    }

    private void applyPhaseEffects(ServerPlayerEntity player, int phase, int ticks) {

        switch (phase) {

            case 5: // OVERFLOW
                player.addStatusEffect(new StatusEffectInstance(
                        StatusEffects.STRENGTH, EFFECT_REFRESH, 1, true, false, true
                ));

                player.addStatusEffect(new StatusEffectInstance(
                        StatusEffects.ABSORPTION, EFFECT_REFRESH, 1, true, false, true
                ));
                break;

            case 4: // EXCEPTIONAL
                player.addStatusEffect(new StatusEffectInstance(
                        StatusEffects.STRENGTH, EFFECT_REFRESH, 0, true, false, true
                ));

                setLifesteal(player, LS_HIGH);
                break;

            case 3: // EQUILIBRIUM
                player.addStatusEffect(new StatusEffectInstance(
                        StatusEffects.REGENERATION, EFFECT_REFRESH, 0, true, false, true
                ));

                setLifesteal(player, LS_MID);
                setSmoothing(player, SMOOTH_MID);
                break;

            case 2: // INADEQUATE
                player.addStatusEffect(new StatusEffectInstance(
                        StatusEffects.REGENERATION, EFFECT_REFRESH, 1, true, false, true
                ));

                player.addStatusEffect(new StatusEffectInstance(
                        StatusEffects.RESISTANCE, EFFECT_REFRESH, 0, true, false, true
                ));

                setSmoothing(player, SMOOTH_LOW);
                tryCleanse(player, ticks, 10);
                break;

            case 1: // EXPOSED
                player.addStatusEffect(new StatusEffectInstance(
                        StatusEffects.SPEED, EFFECT_REFRESH, 1, true, false, true
                ));

                player.addStatusEffect(new StatusEffectInstance(
                        StatusEffects.REGENERATION, EFFECT_REFRESH, 1, true, false, true
                ));

                setSmoothing(player, SMOOTH_LOW);
                tryCleanse(player, ticks, 5);
                break;
        }
    }

    private void setLifesteal(ServerPlayerEntity p, float value) {
        removeTagPrefix(p, LS_TAG);
        p.getCommandTags().add(LS_TAG + value);
    }

    private float getLifesteal(ServerPlayerEntity p) {
        for (String tag : p.getCommandTags()) {
            if (tag.startsWith(LS_TAG)) {
                return Float.parseFloat(tag.substring(LS_TAG.length()));
            }
        }
        return 0f;
    }

    private void setSmoothing(ServerPlayerEntity p, float value) {
        removeTagPrefix(p, SMOOTH_TAG);
        p.getCommandTags().add(SMOOTH_TAG + value);
    }

    public float getSmoothing(ServerPlayerEntity p) {
        for (String tag : p.getCommandTags()) {
            if (tag.startsWith(SMOOTH_TAG)) {
                return Float.parseFloat(tag.substring(SMOOTH_TAG.length()));
            }
        }
        return 0f;
    }

    private void tryCleanse(ServerPlayerEntity player, int ticks, int interval) {
        if (ticks % interval != 0) return;

        for (StatusEffectInstance effect : new java.util.ArrayList<>(player.getStatusEffects())) {
            if (!effect.getEffectType().isBeneficial()) {
                player.removeStatusEffect(effect.getEffectType());
                break;
            }
        }
    }

    private int getStoredPhase(ServerPlayerEntity p) {
        for (String tag : p.getCommandTags()) {
            if (tag.startsWith(ULT_PHASE)) {
                return Integer.parseInt(tag.substring(ULT_PHASE.length()));
            }
        }
        return -1;
    }

    private void setStoredPhase(ServerPlayerEntity p, int phase) {
        removeTagPrefix(p, ULT_PHASE);
        p.getCommandTags().add(ULT_PHASE + phase);
    }

    /* ============================================================
       OTHER STUFF
       ============================================================ */

    @Override public String getName() { return "Healing Factor"; }

    @Override public String getPassiveName() { return "Fast Patch"; }
    @Override public String getPrimaryName() { return "Cleanse"; }
    @Override public String getSecondaryName() { return "Expulsion"; }
    @Override public String getUltimateName() { return "Adaptive Evolution"; }

    @Override public long getPrimaryCooldownMs() { return 3_000; }
    @Override public long getSecondaryCooldownMs() { return 5_000; }
    @Override public long getUltimateCooldownMs() { return 7_000; }

    /*
    100%: Overflow - Strength 2, absorbtion
    80%: Exceptional - Strength 1, lifesteal
    60%: Equilibrium - regeneration. lifesteal, smoothing
    40%: - inadequate - Regeneration, resistance, smoothing, cleansing
    20%: Exposed - Speed 2 (and a burst of speed when first entering), regeneration, cleansing
    * */

    @Override
    public String getOverviewDescription() {
        return "Healing Factor is intended to be a more utility focussed power, with the primary focus being survivability, with some other utility like damage." +
                " You can punish people for overcomitting, but may struggle in direct combat due to your limited damage abilities.";
    }

    @Override
    public String getPassiveDescription() {
        return "After not being hurt for a while, you will quickly begin to regenerate your health. This does NOT use the regeneration effect and therefore will occur regardless" +
                " of hunger.";
    }

    @Override
    public String getPrimaryDescription() {
        return "Heal yourself slightly and purge most negative effects (including some power effects) from you. You will heal more based on the number of effects you clear.";
    }

    @Override
    public String getSecondaryDescription() {
        return "Briefly slow your movement and charge up, during this you take significantly less damage and the damage you take charges up your pulse, the damage and knockback of the" +
                " pulse scales with the amount of damage you have taken (with a limit) and has range falloff. Your pulse also propagates anyone hit with any vanilla negative effects" +
                " you had at the time, removing them from you. At higher charges you will emit more particles.";
    }

    @Override
    public String getUltimateDescription() {
        return "Enter a state where you gain different buffs based on your current health. The stages are as follows:\n" +
                "100% - 80%: Overflow - Strength 2, absorption\n" +
                "80% - 60%: Exceptional - Strength 1, slight lifesteal\n" +
                "60% - 40%: Equilibrium - Regeneration, stronger lifesteal, slight smoothing\n" +
                "40% - 20%: Inadequate - Regeneration, resistance, stronger smoothing, cleansing\n" +
                "20% - 0%: Exposed - Speed 2 (with a burst on entry), regeneration, cleansing\n" +
                "\n" +
                "There are transition stages where effects briefly overlap when entering a new phase.\n" +
                "\n" +
                "Non-potion effects:\n" +
                "Lifesteal - Hits restore health\n" +
                "Smoothing - Reduces large bursts of damage\n" +
                "Cleansing - Periodically removes a negative effect";
    }

    /* ============================================================
       HELPERS
       ============================================================ */

    private static void removeTagPrefix(Entity e, String prefix) {
        var it = e.getCommandTags().iterator();
        while (it.hasNext()) {
            String tag = it.next();
            if (tag.startsWith(prefix)) {
                it.remove();
                return;
            }
        }
    }
}