package com.yourname.loopypowers.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.yourname.loopypowers.CooldownUI;
import com.yourname.loopypowers.Loopypowers;
import com.yourname.loopypowers.manager.AbilityTypes;
import com.yourname.loopypowers.manager.PlayerDataStore;
import com.yourname.loopypowers.manager.PowerManager;
import com.yourname.loopypowers.power.*;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import java.util.*;
import java.util.function.Supplier;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;
import static com.mojang.brigadier.arguments.StringArgumentType.word;
import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;

public class PowerCommand {

    // Arguments
    private static final String ARG_POWER     = "power";
    private static final String ARG_TARGETS   = "targets";
    private static final String ARG_HELP_TYPE = "type";
    private static final String ARG_LEVEL     = "level";

    // ---------- Power registry ----------
    private static final Map<String, Supplier<? extends Power>> POWERS = new LinkedHashMap<>();
    static {
        POWERS.put("speed",         SpeedPower::new);
        POWERS.put("fire",          FirePower::new);
        POWERS.put("teleport",      TeleportPower::new);
        POWERS.put("lightning",     LightningPower::new);
        POWERS.put("flight",        FlightPower::new);
        POWERS.put("blood",         BloodPower::new);
        POWERS.put("sound",         SoundPower::new);
        POWERS.put("strength",      StrengthPower::new);
        POWERS.put("explosion",     ExplosionPower::new);
        POWERS.put("nature",        NaturePower::new);
        POWERS.put("ice",           IcePower::new);
        POWERS.put("fortune",       FortunePower::new);
        POWERS.put("darkness",      DarknessPower::new);
        POWERS.put("healing",       HealingPower::new);
        POWERS.put("psychic",       PsychicPower::new);
        POWERS.put("cosmic",        CosmicPower::new);
        POWERS.put("telekinesis",   TelekinesisPower::new);
        POWERS.put("dimensional",   DimensionalPower::new);
        // "random" is handled specially in set()
    }

    private static final SuggestionProvider<ServerCommandSource> SUGGEST_POWERS =
            (ctx, builder) -> {
                for (String k : POWERS.keySet()) builder.suggest(k);
                builder.suggest("random");
                return builder.buildFuture();
            };

    private static final SuggestionProvider<ServerCommandSource> SUGGEST_HELP_TYPES =
            (ctx, builder) -> {
                builder.suggest("overview");
                builder.suggest("passive");
                builder.suggest("primary");
                builder.suggest("secondary");
                builder.suggest("ultimate");
                return builder.buildFuture();
            };

    public static void register() {
        CommandRegistrationCallback.EVENT.register(
                (dispatcher, registryAccess, environment) -> registerCommands(dispatcher)
        );
    }

    private static void registerCommands(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
                literal("power")

                        // SET - op
                        .then(literal("set")
                                .requires(src -> src.hasPermissionLevel(2))
                                .then(argument(ARG_POWER, word())
                                        .suggests(SUGGEST_POWERS)

                                        // /power set <power>
                                        .executes(PowerCommand::setPowerSelf)

                                        // /power set <power> <selector>
                                        .then(argument(ARG_TARGETS, EntityArgumentType.players())
                                                .executes(PowerCommand::setPowerTargets)
                                        )
                                )
                        )

                        // QUERY - op
                        .then(literal("query")
                                // /power query
                                .executes(PowerCommand::querySelf)
                                // /power query <selector>
                                .then(argument(ARG_TARGETS, EntityArgumentType.players())
                                        .executes(PowerCommand::queryTargets)
                                )
                        )

                        // REMOVE - op
                        .then(literal("remove")
                                .requires(src -> src.hasPermissionLevel(2))
                                // /power remove
                                .executes(PowerCommand::removeSelf)
                                // /power remove <selector>
                                .then(argument(ARG_TARGETS, EntityArgumentType.players())
                                        .executes(PowerCommand::removeTargets)
                                )
                        )

                        // CLEAR - op
                        // wipes both in-memory state AND the persistent save file
                        .then(literal("clear")
                                .requires(src -> src.hasPermissionLevel(2))
                                // /power clear
                                .executes(PowerCommand::clearSelf)
                                // /power clear <selector>
                                .then(argument(ARG_TARGETS, EntityArgumentType.players())
                                        .executes(PowerCommand::clearTargets)
                                )
                        )

                        // LEVEL - op
                        // directly sets a player's level (1–3) without changing their power
                        .then(literal("level")
                                .requires(src -> src.hasPermissionLevel(2))
                                // /power level <1-3>
                                .then(argument(ARG_LEVEL, integer(1, 3))
                                        .executes(PowerCommand::setLevelSelf)
                                        // /power level <1-3> <selector>
                                        .then(argument(ARG_TARGETS, EntityArgumentType.players())
                                                .executes(PowerCommand::setLevelTargets)
                                        )
                                )
                        )

                        // LEVELUP - op
                        // bumps a player's level by one step (1→2, 2→3, caps at 3)
                        .then(literal("levelup")
                                .requires(src -> src.hasPermissionLevel(2))
                                // /power levelup
                                .executes(PowerCommand::levelUpSelf)
                                // /power levelup <selector>
                                .then(argument(ARG_TARGETS, EntityArgumentType.players())
                                        .executes(PowerCommand::levelUpTargets)
                                )
                        )

                        // HELP - no op
                        .then(literal("help")
                                .then(argument(ARG_HELP_TYPE, word())
                                        .suggests(SUGGEST_HELP_TYPES)

                                        // /power help <type>    (uses caller's power)
                                        .executes(PowerCommand::helpSelfPower)

                                        // /power help <type> <powername>
                                        .then(argument(ARG_POWER, word())
                                                .suggests(SUGGEST_POWERS)
                                                .executes(PowerCommand::helpNamedPower)
                                        )
                                )
                        )

                        // DEBUG - op
                        .then(literal("debug")
                                .requires(src -> src.hasPermissionLevel(2))

                                // /power debug clearcooldowns [selector]
                                .then(literal("clearcooldowns")
                                        .executes(PowerCommand::debugClearAllCooldownsSelf)
                                        .then(argument(ARG_TARGETS, EntityArgumentType.players())
                                                .executes(PowerCommand::debugClearAllCooldownsTargets)
                                        )
                                )

                                // /power debug clearcooldown <primary|secondary|ultimate|all> [selector]
                                .then(literal("clearcooldown")
                                        .then(literal("primary")
                                                .executes(ctx -> debugClearAbilityCooldownsSelf(ctx, AbilityTypes.PRIMARY))
                                                .then(argument(ARG_TARGETS, EntityArgumentType.players())
                                                        .executes(ctx -> debugClearAbilityCooldownsTargets(ctx, AbilityTypes.PRIMARY))
                                                )
                                        )
                                        .then(literal("secondary")
                                                .executes(ctx -> debugClearAbilityCooldownsSelf(ctx, AbilityTypes.SECONDARY))
                                                .then(argument(ARG_TARGETS, EntityArgumentType.players())
                                                        .executes(ctx -> debugClearAbilityCooldownsTargets(ctx, AbilityTypes.SECONDARY))
                                                )
                                        )
                                        .then(literal("ultimate")
                                                .executes(ctx -> debugClearAbilityCooldownsSelf(ctx, AbilityTypes.ULTIMATE))
                                                .then(argument(ARG_TARGETS, EntityArgumentType.players())
                                                        .executes(ctx -> debugClearAbilityCooldownsTargets(ctx, AbilityTypes.ULTIMATE))
                                                )
                                        )
                                        .then(literal("all")
                                                .executes(ctx -> debugClearAbilityCooldownsSelf(ctx, null))
                                                .then(argument(ARG_TARGETS, EntityArgumentType.players())
                                                        .executes(ctx -> debugClearAbilityCooldownsTargets(ctx, null))
                                                )
                                        )
                                )

                                // /power debug save [selector]
                                // force-writes current state to disk without waiting for disconnect
                                .then(literal("save")
                                        .executes(PowerCommand::debugSaveSelf)
                                        .then(argument(ARG_TARGETS, EntityArgumentType.players())
                                                .executes(PowerCommand::debugSaveTargets)
                                        )
                                )

                                // /power debug reload [selector]
                                // discards in-memory state and reloads from the save file
                                .then(literal("reload")
                                        .executes(PowerCommand::debugReloadSelf)
                                        .then(argument(ARG_TARGETS, EntityArgumentType.players())
                                                .executes(PowerCommand::debugReloadTargets)
                                        )
                                )

                                // /power debug togglecooldowns
                                // disables or re-enables the cooldown system globally (useful for testing)
                                .then(literal("togglecooldowns")
                                        .executes(PowerCommand::debugToggleCooldowns)
                                )

                                // /power debug onepunch
                                // bypasses rng and armor for the easter egg punch
                                .then(literal("onepunch")
                                        .executes(PowerCommand::debugToggleOnePunch)
                                )
                        )
        );
    }

    // ============================================================
    // SET
    // ============================================================

    private static int setPowerSelf(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity player = requirePlayer(ctx);
        String powerName = getString(ctx, ARG_POWER);
        applySetPower(ctx.getSource(), List.of(player), powerName);
        return 1;
    }

    private static int setPowerTargets(CommandContext<ServerCommandSource> ctx) {
        String powerName = getString(ctx, ARG_POWER);

        final Collection<ServerPlayerEntity> targets;
        try {
            targets = EntityArgumentType.getPlayers(ctx, ARG_TARGETS);
        } catch (CommandSyntaxException e) {
            ctx.getSource().sendError(Text.literal("Invalid selector."));
            return 0;
        }

        applySetPower(ctx.getSource(), targets, powerName);
        return 1;
    }

    private static void applySetPower(ServerCommandSource src, Collection<ServerPlayerEntity> targets, String powerName) {
        if (targets.isEmpty()) {
            src.sendError(Text.literal("No targets found."));
            return;
        }

        if (powerName.equalsIgnoreCase("random")) {
            for (ServerPlayerEntity p : targets) {
                PowerManager.assignRandomPower(p);
                p.sendMessage(Text.literal("§bPower set to §eRANDOM"), false);
            }
            src.sendFeedback(() -> Text.literal("Assigned RANDOM power to " + targets.size() + " player(s)."), false);
            return;
        }

        Supplier<? extends Power> factory = POWERS.get(powerName.toLowerCase(Locale.ROOT));
        if (factory == null) {
            src.sendError(Text.literal("Unknown power: " + powerName));
            return;
        }

        for (ServerPlayerEntity p : targets) {
            PowerManager.setPower(p, factory.get());
            p.sendMessage(Text.literal("§bPower set to §a" + powerName.toUpperCase(Locale.ROOT)), false);
        }

        src.sendFeedback(() -> Text.literal("Set power '" + powerName + "' for " + targets.size() + " player(s)."), false);
    }

    // ============================================================
    // QUERY
    // ============================================================

    private static int querySelf(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity player = requirePlayer(ctx);
        sendPowerQuery(ctx.getSource(), List.of(player));
        return 1;
    }

    private static int queryTargets(CommandContext<ServerCommandSource> ctx) {
        final Collection<ServerPlayerEntity> targets;
        try {
            targets = EntityArgumentType.getPlayers(ctx, ARG_TARGETS);
        } catch (CommandSyntaxException e) {
            ctx.getSource().sendError(Text.literal("Invalid selector."));
            return 0;
        }

        sendPowerQuery(ctx.getSource(), targets);
        return 1;
    }

    private static void sendPowerQuery(ServerCommandSource src, Collection<ServerPlayerEntity> targets) {
        if (targets.isEmpty()) {
            src.sendError(Text.literal("No targets found."));
            return;
        }

        for (ServerPlayerEntity p : targets) {
            Power power = PowerManager.getPower(p);
            String powerName = (power == null) ? "NONE" : power.getName();
            int level = PowerManager.getLevel(p);

            // snapshot of ACTIVE cooldowns
            Map<String, CooldownUI.CooldownInfo> cds = CooldownUI.getCooldownSnapshot(p);

            StringBuilder sb = new StringBuilder();
            sb.append("§b").append(p.getName().getString())
                    .append(" §7-> §fPower: §a").append(powerName)
                    .append(" §7| Level: §b").append(level); // show level alongside power

            // Always show the 3 ability statuses for their CURRENT power (if they have one)
            if (power != null) {
                sb.append("\n§7Cooldowns:");

                for (AbilityTypes type : AbilityTypes.values()) {
                    String key = PowerManager.abilityKey(power, type);
                    CooldownUI.CooldownInfo info = cds.get(key);

                    String abilityName = switch (type) {
                        case PRIMARY   -> power.getPrimaryName();
                        case SECONDARY -> power.getSecondaryName();
                        case ULTIMATE  -> power.getUltimateName();
                    };

                    // show lock state inline with the ability name if the player hasn't unlocked it yet
                    int requiredLevel = switch (type) {
                        case PRIMARY   -> 1;
                        case SECONDARY -> 2;
                        case ULTIMATE  -> 3;
                    };

                    String lockNote = (level < requiredLevel) ? " §c[Lv." + requiredLevel + "]" : "";

                    sb.append("\n  ")
                            .append(type.color).append(abilityName).append(lockNote).append("§7: ");

                    if (info == null) {
                        sb.append("§aREADY");
                    } else {
                        sb.append("§c").append(msToSecondsCeil(info.remainingMs)).append("s");
                        if (info.suffix != null && !info.suffix.isBlank()) {
                            sb.append(" §8").append(info.suffix);
                        }
                    }
                }
            }

            // Also list any EXTRA cooldown keys (not the 3 standard ones), if present
            if (!cds.isEmpty()) {
                Set<String> standardKeys = new HashSet<>();
                if (power != null) {
                    standardKeys.add(PowerManager.abilityKey(power, AbilityTypes.PRIMARY));
                    standardKeys.add(PowerManager.abilityKey(power, AbilityTypes.SECONDARY));
                    standardKeys.add(PowerManager.abilityKey(power, AbilityTypes.ULTIMATE));
                }

                List<String> extras = new ArrayList<>();
                for (var e : cds.entrySet()) {
                    if (standardKeys.contains(e.getKey())) continue;
                    extras.add(e.getKey());
                }

                if (!extras.isEmpty()) {
                    sb.append("\n§7Other cooldowns:");
                    extras.sort(String::compareToIgnoreCase);
                    for (String k : extras) {
                        CooldownUI.CooldownInfo info = cds.get(k);
                        sb.append("\n  §7").append(k).append(": §c")
                                .append(msToSecondsCeil(info.remainingMs)).append("s");
                        if (info.suffix != null && !info.suffix.isBlank()) {
                            sb.append(" §8").append(info.suffix);
                        }
                    }
                }
            }

            src.sendFeedback(() -> Text.literal(sb.toString()), false);
        }
    }

    private static long msToSecondsCeil(long ms) {
        if (ms <= 0) return 0;
        return (ms + 999) / 1000;
    }

    // ============================================================
    // REMOVE
    // ============================================================

    private static int removeSelf(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity player = requirePlayer(ctx);
        removePowerFrom(ctx.getSource(), List.of(player));
        return 1;
    }

    private static int removeTargets(CommandContext<ServerCommandSource> ctx) {
        final Collection<ServerPlayerEntity> targets;
        try {
            targets = EntityArgumentType.getPlayers(ctx, ARG_TARGETS);
        } catch (CommandSyntaxException e) {
            ctx.getSource().sendError(Text.literal("Invalid selector."));
            return 0;
        }

        removePowerFrom(ctx.getSource(), targets);
        return 1;
    }

    private static void removePowerFrom(ServerCommandSource src, Collection<ServerPlayerEntity> targets) {
        if (targets.isEmpty()) {
            src.sendError(Text.literal("No targets found."));
            return;
        }

        for (ServerPlayerEntity p : targets) {
            PowerManager.removePower(p); // removes
            p.sendMessage(Text.literal("§cPower removed."), false);
        }

        src.sendFeedback(() -> Text.literal("Removed power from " + targets.size() + " player(s)."), false);
    }

    // ============================================================
    // CLEAR
    // ============================================================

    private static int clearSelf(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity player = requirePlayer(ctx);
        clearPowerData(ctx.getSource(), List.of(player));
        return 1;
    }

    private static int clearTargets(CommandContext<ServerCommandSource> ctx) {
        final Collection<ServerPlayerEntity> targets;
        try {
            targets = EntityArgumentType.getPlayers(ctx, ARG_TARGETS);
        } catch (CommandSyntaxException e) {
            ctx.getSource().sendError(Text.literal("Invalid selector."));
            return 0;
        }

        clearPowerData(ctx.getSource(), targets);
        return 1;
    }

    /**
     * Wipes both in-memory state and the persistent save file.
     * Literally leaves no trace on disc, so it should be reassigned on next join event
     */
    private static void clearPowerData(ServerCommandSource src, Collection<ServerPlayerEntity> targets) {
        if (targets.isEmpty()) {
            src.sendError(Text.literal("No targets found."));
            return;
        }

        for (ServerPlayerEntity p : targets) {
            PowerManager.removePower(p);  // clears in-memory power, level, cooldowns
            PlayerDataStore.delete(p);    // wipes the save file entirely
            p.sendMessage(Text.literal("§cAll power data cleared."), false);
        }

        src.sendFeedback(() -> Text.literal("Cleared all power data for " + targets.size() + " player(s)."), false);
    }

    // ============================================================
    // LEVEL
    // ============================================================

    private static int setLevelSelf(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity player = requirePlayer(ctx);
        int level = getInteger(ctx, ARG_LEVEL);
        applySetLevel(ctx.getSource(), List.of(player), level);
        return 1;
    }

    private static int setLevelTargets(CommandContext<ServerCommandSource> ctx) {
        int level = getInteger(ctx, ARG_LEVEL);

        final Collection<ServerPlayerEntity> targets;
        try {
            targets = EntityArgumentType.getPlayers(ctx, ARG_TARGETS);
        } catch (CommandSyntaxException e) {
            ctx.getSource().sendError(Text.literal("Invalid selector."));
            return 0;
        }

        applySetLevel(ctx.getSource(), targets, level);
        return 1;
    }

    private static void applySetLevel(ServerCommandSource src, Collection<ServerPlayerEntity> targets, int level) {
        if (targets.isEmpty()) {
            src.sendError(Text.literal("No targets found."));
            return;
        }

        for (ServerPlayerEntity p : targets) {
            PowerManager.setLevel(p, level);
            PlayerDataStore.save(p); // persist immediately
            p.sendMessage(Text.literal("§bYour power level was set to §e" + level + "§b."), false);
        }

        src.sendFeedback(() -> Text.literal("Set level " + level + " for " + targets.size() + " player(s)."), false);
    }

    // ============================================================
    // LEVELUP
    // ============================================================

    private static int levelUpSelf(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity player = requirePlayer(ctx);
        applyLevelUp(ctx.getSource(), List.of(player));
        return 1;
    }

    private static int levelUpTargets(CommandContext<ServerCommandSource> ctx) {
        final Collection<ServerPlayerEntity> targets;
        try {
            targets = EntityArgumentType.getPlayers(ctx, ARG_TARGETS);
        } catch (CommandSyntaxException e) {
            ctx.getSource().sendError(Text.literal("Invalid selector."));
            return 0;
        }

        applyLevelUp(ctx.getSource(), targets);
        return 1;
    }

    private static void applyLevelUp(ServerCommandSource src, Collection<ServerPlayerEntity> targets) {
        if (targets.isEmpty()) {
            src.sendError(Text.literal("No targets found."));
            return;
        }

        int changed = 0;
        for (ServerPlayerEntity p : targets) {
            int before = PowerManager.getLevel(p);
            if (before >= 3) {
                p.sendMessage(Text.literal("§cYour power is already at maximum level."), false);
                continue;
            }
            PowerManager.levelUp(p);   // sends the evolution message to the player
            PlayerDataStore.save(p);   // persist immediately
            changed++;
        }

        int finalChanged = changed;
        src.sendFeedback(() -> Text.literal("Levelled up " + finalChanged + " player(s)."), false);
    }

    // ============================================================
    // HELP
    // ============================================================

    private static int helpSelfPower(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity player = requirePlayer(ctx);
        String type = getString(ctx, ARG_HELP_TYPE).toLowerCase(Locale.ROOT);

        Power power = PowerManager.getPower(player);
        if (power == null) {
            ctx.getSource().sendError(Text.literal("You have no power. Use /power set <power>."));
            return 0;
        }

        // pass the player's actual level so overview can show lock status
        sendHelp(ctx.getSource(), type, power, PowerManager.getLevel(player));
        return 1;
    }

    private static int helpNamedPower(CommandContext<ServerCommandSource> ctx) {
        String type      = getString(ctx, ARG_HELP_TYPE).toLowerCase(Locale.ROOT);
        String powerName = getString(ctx, ARG_POWER).toLowerCase(Locale.ROOT);

        if (powerName.equals("random")) {
            ctx.getSource().sendError(Text.literal("Help is not available for 'random'. Pick a specific power."));
            return 0;
        }

        Supplier<? extends Power> factory = POWERS.get(powerName);
        if (factory == null) {
            ctx.getSource().sendError(Text.literal("Unknown power: " + powerName));
            return 0;
        }

        // no player-level context here — overview will show generic level requirements instead
        sendHelp(ctx.getSource(), type, factory.get(), 0);
        return 1;
    }

    /**
     * Sends a help message for a given power and section type.
     *
     * playerLevel controls lock display in "overview":
     *   0         → no player context; shows generic "Lv.X required" notes
     *   1 / 2 / 3 → player's actual level; shows LOCKED on abilities they can't yet use
     */
    private static void sendHelp(ServerCommandSource src, String type, Power power, int playerLevel) {
        Text msg;

        switch (type) {
            case "overview" -> {
                // lock suffix shown next to ability names that aren't yet available
                String secLock = getLockSuffix(playerLevel, 2);
                String ultLock = getLockSuffix(playerLevel, 3);

                msg = Text.literal(
                        "§b" + power.getName() + "§f\n" +
                                "§7" + power.getPassiveName() + ": §f" + power.getPassiveDescription() + "\n\n" +
                                "§7" + power.getPrimaryName() + ": §f" + power.getPrimaryDescription() + "\n\n" +
                                "§7" + power.getSecondaryName() + secLock + ": §f" + power.getSecondaryDescription() + "\n\n" +
                                "§7" + power.getUltimateName()  + ultLock + ": §f" + power.getUltimateDescription() + "\n\n" +
                                "§7Overview: §f" + power.getOverviewDescription()
                );
            }

            case "passive" -> msg = Text.literal(
                    "§b" + power.getName() + "§f\n" +
                            "§7" + power.getPassiveName() + ": §f" + power.getPassiveDescription()
            );

            case "primary" -> msg = Text.literal(
                    "§b" + power.getName() + "§f\n" +
                            "§7" + power.getPrimaryName() + " §8(" + cooldownSeconds(power.getPrimaryCooldownMs()) + "s cooldown)§f\n" +
                            "§7" + power.getPrimaryName() + ": §f" + power.getPrimaryDescription()
            );

            case "secondary" -> msg = Text.literal(
                    "§b" + power.getName() + "§f\n" +
                            "§7" + power.getSecondaryName() + " §8(" + cooldownSeconds(power.getSecondaryCooldownMs()) + "s cooldown)§f\n" +
                            "§7" + power.getSecondaryName() + ": §f" + power.getSecondaryDescription()
            );

            case "ultimate" -> msg = Text.literal(
                    "§b" + power.getName() + "§f\n" +
                            "§7" + power.getUltimateName() + " §8(" + cooldownSeconds(power.getUltimateCooldownMs()) + "s cooldown)§f\n" +
                            "§7" + power.getUltimateName() + ": §f" + power.getUltimateDescription()
            );

            default -> {
                src.sendError(Text.literal("Unknown help type: " + type));
                return;
            }
        }

        src.sendFeedback(() -> msg, false);
    }

    /**
     * Returns the annotation appended to an ability name in the overview.
     *
     * playerLevel == 0  → generic requirement shown to anyone browsing a named power
     * playerLevel < req → §c[LOCKED] — player can see it but can't use it yet
     * playerLevel >= req → empty string — unlocked, no annotation needed
     */
    private static String getLockSuffix(int playerLevel, int requiredLevel) {
        if (playerLevel == 0)               return " §8(Lv." + requiredLevel + " required)";
        if (playerLevel < requiredLevel)    return " §c[LOCKED]";
        return "";
    }

    private static long cooldownSeconds(long cooldownMs) {
        // converts and rounds up millisecond cooldowns
        if (cooldownMs <= 0) return 0;
        return (cooldownMs + 999) / 1000;
    }

    // ============================================================
    // DEBUG
    // ============================================================

    private static int debugClearAllCooldownsSelf(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity player = requirePlayer(ctx);
        debugClearAllCooldowns(ctx.getSource(), List.of(player));
        return 1;
    }

    private static int debugClearAllCooldownsTargets(CommandContext<ServerCommandSource> ctx) {
        final Collection<ServerPlayerEntity> targets;
        try {
            targets = EntityArgumentType.getPlayers(ctx, ARG_TARGETS);
        } catch (CommandSyntaxException e) {
            ctx.getSource().sendError(Text.literal("Invalid selector."));
            return 0;
        }

        debugClearAllCooldowns(ctx.getSource(), targets);
        return 1;
    }

    private static void debugClearAllCooldowns(ServerCommandSource src, Collection<ServerPlayerEntity> targets) {
        for (ServerPlayerEntity p : targets) {
            PowerManager.clearAllCooldowns(p);
            p.sendMessage(Text.literal("§aAll cooldowns cleared."), false);
        }
        src.sendFeedback(() -> Text.literal("Cleared ALL cooldowns for " + targets.size() + " player(s)."), false);
    }

    private static int debugClearAbilityCooldownsSelf(CommandContext<ServerCommandSource> ctx, AbilityTypes typeOrNullAll) {
        ServerPlayerEntity player = requirePlayer(ctx);
        debugClearAbilityCooldowns(ctx.getSource(), List.of(player), typeOrNullAll);
        return 1;
    }

    private static int debugClearAbilityCooldownsTargets(CommandContext<ServerCommandSource> ctx, AbilityTypes typeOrNullAll) {
        final Collection<ServerPlayerEntity> targets;
        try {
            targets = EntityArgumentType.getPlayers(ctx, ARG_TARGETS);
        } catch (CommandSyntaxException e) {
            ctx.getSource().sendError(Text.literal("Invalid selector."));
            return 0;
        }

        debugClearAbilityCooldowns(ctx.getSource(), targets, typeOrNullAll);
        return 1;
    }

    /**
     * Clears cooldowns ONLY for the player's CURRENT power ability keys.
     * typeOrNullAll == null => clears primary+secondary+ultimate.
     */
    private static void debugClearAbilityCooldowns(ServerCommandSource src, Collection<ServerPlayerEntity> targets, AbilityTypes typeOrNullAll) {
        int changed = 0;

        for (ServerPlayerEntity p : targets) {
            Power power = PowerManager.getPower(p);
            if (power == null) continue;

            if (typeOrNullAll == null) {
                // clears all three ability cooldowns
                PowerManager.clearCooldown(p, PowerManager.abilityKey(power, AbilityTypes.PRIMARY));
                PowerManager.clearCooldown(p, PowerManager.abilityKey(power, AbilityTypes.SECONDARY));
                PowerManager.clearCooldown(p, PowerManager.abilityKey(power, AbilityTypes.ULTIMATE));
                p.sendMessage(Text.literal("§aPrimary/Secondary/Ultimate cooldowns cleared."), false);
            } else {
                PowerManager.clearCooldown(p, PowerManager.abilityKey(power, typeOrNullAll));
                p.sendMessage(Text.literal("§a" + typeOrNullAll.name() + " cooldown cleared."), false);
            }

            changed++;
        }

        int finalChanged = changed;
        src.sendFeedback(() -> Text.literal("Cleared ability cooldowns for " + finalChanged + " player(s)."), false);
    }

    // ---------- Debug: save / reload ----------

    private static int debugSaveSelf(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity player = requirePlayer(ctx);
        debugSave(ctx.getSource(), List.of(player));
        return 1;
    }

    private static int debugSaveTargets(CommandContext<ServerCommandSource> ctx) {
        final Collection<ServerPlayerEntity> targets;
        try {
            targets = EntityArgumentType.getPlayers(ctx, ARG_TARGETS);
        } catch (CommandSyntaxException e) {
            ctx.getSource().sendError(Text.literal("Invalid selector."));
            return 0;
        }

        debugSave(ctx.getSource(), targets);
        return 1;
    }

    // force writes current in-memory state to disc immediately
    private static void debugSave(ServerCommandSource src, Collection<ServerPlayerEntity> targets) {
        for (ServerPlayerEntity p : targets) {
            PlayerDataStore.save(p);
            p.sendMessage(Text.literal("§aPower data saved to disc."), false);
        }
        src.sendFeedback(() -> Text.literal("Saved data for " + targets.size() + " player(s)."), false);
    }

    private static int debugReloadSelf(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity player = requirePlayer(ctx);
        debugReload(ctx.getSource(), List.of(player));
        return 1;
    }

    private static int debugReloadTargets(CommandContext<ServerCommandSource> ctx) {
        final Collection<ServerPlayerEntity> targets;
        try {
            targets = EntityArgumentType.getPlayers(ctx, ARG_TARGETS);
        } catch (CommandSyntaxException e) {
            ctx.getSource().sendError(Text.literal("Invalid selector."));
            return 0;
        }

        debugReload(ctx.getSource(), targets);
        return 1;
    }

    // discards current in-memory state and re-applies from the save file
    private static void debugReload(ServerCommandSource src, Collection<ServerPlayerEntity> targets) {
        for (ServerPlayerEntity p : targets) {
            PowerManager.removePower(p);  // clear current state first so onAssign does the thing cleanly
            PlayerDataStore.load(p);
            p.sendMessage(Text.literal("§aPower data reloaded from disk."), false);
        }
        src.sendFeedback(() -> Text.literal("Reloaded data for " + targets.size() + " player(s)."), false);
    }

    // ---------- Debug: toggle cooldowns ----------

    // tracks the current disabled state so toggle knows which way to flip
    private static boolean cooldownsCurrentlyDisabled = false;

    private static int debugToggleCooldowns(CommandContext<ServerCommandSource> ctx) {
        cooldownsCurrentlyDisabled = !cooldownsCurrentlyDisabled;
        PowerManager.setCooldownsDisabled(cooldownsCurrentlyDisabled);

        String state = cooldownsCurrentlyDisabled ? "§cDISABLED" : "§aENABLED";
        ctx.getSource().sendFeedback(() -> Text.literal("Cooldown system is now " + state + "§f."), true);
        return 1;
    }

    // ---------- Debug: toggle onepunch ----------

    private static int debugToggleOnePunch(CommandContext<ServerCommandSource> ctx) {
        StrengthPower.onePunchDebugEnabled = !StrengthPower.onePunchDebugEnabled;
        String state = StrengthPower.onePunchDebugEnabled ? "§aENABLED" : "§cDISABLED";
        ctx.getSource().sendFeedback(() -> Text.literal("One Punch mode is now " + state + "§f."), true);
        return 1;
    }

    // ============================================================
    // Helpers
    // ============================================================

    private static ServerPlayerEntity requirePlayer(CommandContext<ServerCommandSource> ctx) {
        try {
            return ctx.getSource().getPlayer();
        } catch (Exception e) {
            throw new RuntimeException("This command must be run by a player (or provide a selector).");
        }
    }
}