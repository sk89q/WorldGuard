/*
 * WorldGuard, a suite of tools for Minecraft
 * Copyright (C) sk89q <http://www.sk89q.com>
 * Copyright (C) WorldGuard team and contributors
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package com.sk89q.worldguard.bukkit;

import com.sk89q.util.yaml.YAMLFormat;
import com.sk89q.util.yaml.YAMLProcessor;
import com.sk89q.worldedit.util.Location;
import com.sk89q.worldedit.util.report.Unreported;
import com.sk89q.worldedit.world.entity.EntityType;
import com.sk89q.worldedit.world.entity.EntityTypes;
import com.sk89q.worldedit.world.item.ItemTypes;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.blacklist.Blacklist;
import com.sk89q.worldguard.blacklist.BlacklistLoggerHandler;
import com.sk89q.worldguard.blacklist.logger.ConsoleHandler;
import com.sk89q.worldguard.blacklist.logger.DatabaseHandler;
import com.sk89q.worldguard.blacklist.logger.FileHandler;
import com.sk89q.worldguard.blacklist.target.TargetMatcherParseException;
import com.sk89q.worldguard.blacklist.target.TargetMatcherParser;
import com.sk89q.worldguard.bukkit.chest.BukkitSignChestProtection;
import com.sk89q.worldguard.bukkit.internal.TargetMatcherSet;
import com.sk89q.worldguard.chest.ChestProtection;
import com.sk89q.worldguard.commands.CommandUtils;
import com.sk89q.worldguard.config.YamlWorldConfiguration;
import org.bukkit.potion.PotionEffectType;
import org.yaml.snakeyaml.error.YAMLException;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;

/**
 * Holds the configuration for individual worlds.
 *
 * @author sk89q
 * @author Michael
 */
public class BukkitWorldConfiguration extends YamlWorldConfiguration {

    private static final TargetMatcherParser matcherParser = new TargetMatcherParser();

    @Unreported private String worldName;

    @Unreported private ChestProtection chestProtection = new BukkitSignChestProtection();

    /* Configuration data start */
    public Set<PotionEffectType> blockPotions;
    public TargetMatcherSet allowAllInteract;
    public TargetMatcherSet blockUseAtFeet;
    public boolean usePaperEntityOrigin;
    /* Configuration data end */

    public boolean needsBlockListener;

    /**
     * Construct the object.
     *
     * @param plugin The WorldGuardPlugin instance
     * @param worldName The world name that this BukkitWorldConfiguration is for.
     * @param parentConfig The parent configuration to read defaults from
     */
    public BukkitWorldConfiguration(WorldGuardPlugin plugin, String worldName, YAMLProcessor parentConfig) {
        File baseFolder = new File(plugin.getDataFolder(), "worlds/" + worldName);
        File configFile = new File(baseFolder, "config.yml");
        blacklistFile = new File(baseFolder, "blacklist.txt");

        this.worldName = worldName;
        this.parentConfig = parentConfig;

        plugin.createDefaultConfiguration(configFile, "config_world.yml");
        plugin.createDefaultConfiguration(blacklistFile, "blacklist.txt");

        config = new YAMLProcessor(configFile, true, YAMLFormat.EXTENDED);
        loadConfiguration();

        if (summaryOnStart) {
            log.info("Loaded configuration for world '" + worldName + "'");
        }
    }

    private TargetMatcherSet getTargetMatchers(String node) {
        TargetMatcherSet set = new TargetMatcherSet();
        List<String> inputs = parentConfig.getStringList(node, null);

        if (inputs == null || inputs.isEmpty()) {
            parentConfig.setProperty(node, new ArrayList<String>());
        }

        if (config.getProperty(node) != null) {
            inputs = config.getStringList(node, null);
        }

        if (inputs == null || inputs.isEmpty()) {
            return set;
        }

        for (String input : inputs) {
            try {
                set.add(matcherParser.fromInput(input));
            } catch (TargetMatcherParseException e) {
                log.warning("Failed to parse the block / item type specified as '" + input + "'");
            }
        }

        return set;
    }

    /**
     * Load the configuration.
     */
    @Override
    public void loadConfiguration() {
        try {
            config.load();
        } catch (IOException e) {
            log.log(Level.SEVERE, "Error reading configuration for world " + worldName + ": ", e);
        } catch (YAMLException e) {
            log.severe("Error parsing configuration for world " + worldName + ". ");
            throw e;
        }

        summaryOnStart = getBoolean("summary-on-start", true);
        opPermissions = getBoolean("op-permissions", true);

        buildPermissions = getBoolean("build-permission-nodes.enable", false);
        buildPermissionDenyMessage = CommandUtils.replaceColorMacros(
                getString("build-permission-nodes.deny-message", "&eSorry, but you are not permitted to do that here."));

        strictEntitySpawn = getBoolean("event-handling.block-entity-spawns-with-untraceable-cause", false);
        allowAllInteract = getTargetMatchers("event-handling.interaction-whitelist");
        blockUseAtFeet = getTargetMatchers("event-handling.emit-block-use-at-feet");
        ignoreHopperMoveEvents = getBoolean("event-handling.ignore-hopper-item-move-events", false);
        breakDeniedHoppers = getBoolean("event-handling.break-hoppers-on-denied-move", true);

        usePaperEntityOrigin = getBoolean("regions.use-paper-entity-origin", false);

        itemDurability = getBoolean("protection.item-durability", true);
        removeInfiniteStacks = getBoolean("protection.remove-infinite-stacks", false);
        disableExpDrops = getBoolean("protection.disable-xp-orb-drops", false);
        disableObsidianGenerators = getBoolean("protection.disable-obsidian-generators", false);

        useMaxPriorityAssociation = getBoolean("protection.use-max-priority-association", false);

        blockPotions = new HashSet<>();
        for (String potionName : getStringList("gameplay.block-potions", null)) {
            PotionEffectType effect = PotionEffectType.getByName(potionName);

            if (effect == null) {
                log.warning("Unknown potion effect type '" + potionName + "'");
            } else {
                blockPotions.add(effect);
            }
        }
        blockPotionsAlways = getBoolean("gameplay.block-potions-overly-reliably", false);
        disableConduitEffects = getBoolean("gameplay.disable-conduit-effects", false);

        simulateSponge = getBoolean("simulation.sponge.enable", false);
        spongeRadius = Math.max(1, getInt("simulation.sponge.radius", 3)) - 1;
        redstoneSponges = getBoolean("simulation.sponge.redstone", false);

        pumpkinScuba = getBoolean("default.pumpkin-scuba", false);
        disableHealthRegain = getBoolean("default.disable-health-regain", false);

        noPhysicsGravel = getBoolean("physics.no-physics-gravel", false);
        noPhysicsSand = getBoolean("physics.no-physics-sand", false);
        ropeLadders = getBoolean("physics.vine-like-rope-ladders", false);
        allowPortalAnywhere = getBoolean("physics.allow-portal-anywhere", false);
        preventWaterDamage = new HashSet<>(convertLegacyBlocks(getStringList("physics.disable-water-damage-blocks", null)));

        blockTNTExplosions = getBoolean("ignition.block-tnt", false);
        blockTNTBlockDamage = getBoolean("ignition.block-tnt-block-damage", false);
        blockLighter = getBoolean("ignition.block-lighter", false);

        preventLavaFire = getBoolean("fire.disable-lava-fire-spread", false);
        disableFireSpread = getBoolean("fire.disable-all-fire-spread", false);
        disableFireSpreadBlocks = new HashSet<>(convertLegacyBlocks(getStringList("fire.disable-fire-spread-blocks", null)));
        allowedLavaSpreadOver = new HashSet<>(convertLegacyBlocks(getStringList("fire.lava-spread-blocks", null)));

        blockCreeperExplosions = getBoolean("mobs.block-creeper-explosions", false);
        blockCreeperBlockDamage = getBoolean("mobs.block-creeper-block-damage", false);
        blockWitherExplosions = getBoolean("mobs.block-wither-explosions", false);
        blockWitherBlockDamage = getBoolean("mobs.block-wither-block-damage", false);
        blockWitherSkullExplosions = getBoolean("mobs.block-wither-skull-explosions", false);
        blockWitherSkullBlockDamage = getBoolean("mobs.block-wither-skull-block-damage", false);
        blockEnderDragonBlockDamage = getBoolean("mobs.block-enderdragon-block-damage", false);
        blockEnderDragonPortalCreation = getBoolean("mobs.block-enderdragon-portal-creation", false);
        blockFireballExplosions = getBoolean("mobs.block-fireball-explosions", false);
        blockFireballBlockDamage = getBoolean("mobs.block-fireball-block-damage", false);
        antiWolfDumbness = getBoolean("mobs.anti-wolf-dumbness", false);
        allowTamedSpawns = getBoolean("mobs.allow-tamed-spawns", true);
        disableEndermanGriefing = getBoolean("mobs.disable-enderman-griefing", false);
        disableSnowmanTrails = getBoolean("mobs.disable-snowman-trails", false);
        blockEntityPaintingDestroy = getBoolean("mobs.block-painting-destroy", false);
        blockEntityItemFrameDestroy = getBoolean("mobs.block-item-frame-destroy", false);
        blockEntityArmorStandDestroy = getBoolean("mobs.block-armor-stand-destroy", false);
        blockPluginSpawning = getBoolean("mobs.block-plugin-spawning", true);
        blockGroundSlimes = getBoolean("mobs.block-above-ground-slimes", false);
        blockOtherExplosions = getBoolean("mobs.block-other-explosions", false);
        blockZombieDoorDestruction = getBoolean("mobs.block-zombie-door-destruction", false);
        blockEntityVehicleEntry = getBoolean("mobs.block-vehicle-entry", false);

        disableFallDamage = getBoolean("player-damage.disable-fall-damage", false);
        disableLavaDamage = getBoolean("player-damage.disable-lava-damage", false);
        disableFireDamage = getBoolean("player-damage.disable-fire-damage", false);
        disableLightningDamage = getBoolean("player-damage.disable-lightning-damage", false);
        disableDrowningDamage = getBoolean("player-damage.disable-drowning-damage", false);
        disableSuffocationDamage = getBoolean("player-damage.disable-suffocation-damage", false);
        disableContactDamage = getBoolean("player-damage.disable-contact-damage", false);
        teleportOnSuffocation = getBoolean("player-damage.teleport-on-suffocation", false);
        disableVoidDamage = getBoolean("player-damage.disable-void-damage", false);
        teleportOnVoid = getBoolean("player-damage.teleport-on-void-falling", false);
        safeFallOnVoid = getBoolean("player-damage.reset-fall-on-void-teleport", false);
        disableExplosionDamage = getBoolean("player-damage.disable-explosion-damage", false);
        disableMobDamage = getBoolean("player-damage.disable-mob-damage", false);
        disableDeathMessages = getBoolean("player-damage.disable-death-messages", false);

        signChestProtection = getBoolean("chest-protection.enable", false);
        disableSignChestProtectionCheck = getBoolean("chest-protection.disable-off-check", false);

        disableCreatureCropTrampling = getBoolean("crops.disable-creature-trampling", false);
        disablePlayerCropTrampling = getBoolean("crops.disable-player-trampling", false);

        disableCreatureTurtleEggTrampling = getBoolean("turtle-egg.disable-creature-trampling", false);
        disablePlayerTurtleEggTrampling = getBoolean("turtle-egg.disable-player-trampling", false);

        disallowedLightningBlocks = new HashSet<>(convertLegacyBlocks(getStringList("weather.prevent-lightning-strike-blocks", null)));
        preventLightningFire = getBoolean("weather.disable-lightning-strike-fire", false);
        disableThunder = getBoolean("weather.disable-thunderstorm", false);
        disableWeather = getBoolean("weather.disable-weather", false);
        disablePigZap = getBoolean("weather.disable-pig-zombification", false);
        disableVillagerZap = getBoolean("weather.disable-villager-witchification", false);
        disableCreeperPower = getBoolean("weather.disable-powered-creepers", false);
        alwaysRaining = getBoolean("weather.always-raining", false);
        alwaysThundering = getBoolean("weather.always-thundering", false);

        disableMushroomSpread = getBoolean("dynamics.disable-mushroom-spread", false);
        disableIceMelting = getBoolean("dynamics.disable-ice-melting", false);
        disableSnowMelting = getBoolean("dynamics.disable-snow-melting", false);
        disableSnowFormation = getBoolean("dynamics.disable-snow-formation", false);
        disableIceFormation = getBoolean("dynamics.disable-ice-formation", false);
        disableLeafDecay = getBoolean("dynamics.disable-leaf-decay", false);
        disableGrassGrowth = getBoolean("dynamics.disable-grass-growth", false);
        disableMyceliumSpread = getBoolean("dynamics.disable-mycelium-spread", false);
        disableVineGrowth = getBoolean("dynamics.disable-vine-growth", false);
        disableCropGrowth = getBoolean("dynamics.disable-crop-growth", false);
        disableSoilDehydration = getBoolean("dynamics.disable-soil-dehydration", false);
        disableCoralBlockFade = getBoolean("dynamics.disable-coral-block-fade", false);
        allowedSnowFallOver = new HashSet<>(convertLegacyBlocks(getStringList("dynamics.snow-fall-blocks", null)));

        useRegions = getBoolean("regions.enable", true);
        regionInvinciblityRemovesMobs = getBoolean("regions.invincibility-removes-mobs", false);
        regionCancelEmptyChatEvents = getBoolean("regions.cancel-chat-without-recipients", true);
        regionNetherPortalProtection = getBoolean("regions.nether-portal-protection", true);
        forceDefaultTitleTimes = config.getBoolean("regions.titles-always-use-default-times", true); // note: technically not region-specific, but we only use it for the title flags
        fakePlayerBuildOverride = getBoolean("regions.fake-player-build-override", true);
        explosionFlagCancellation = getBoolean("regions.explosion-flags-block-entity-damage", true);
        highFreqFlags = getBoolean("regions.high-frequency-flags", false);
        checkLiquidFlow = getBoolean("regions.protect-against-liquid-flow", false);
        regionWand = convertLegacyItem(getString("regions.wand", ItemTypes.LEATHER.getId()));
        maxClaimVolume = getInt("regions.max-claim-volume", 30000);
        claimOnlyInsideExistingRegions = getBoolean("regions.claim-only-inside-existing-regions", false);
        boundedLocationFlags = getBoolean("regions.location-flags-only-inside-regions", false);

        maxRegionCountPerPlayer = getInt("regions.max-region-count-per-player.default", 7);
        maxRegionCounts = new HashMap<>();
        maxRegionCounts.put(null, maxRegionCountPerPlayer);

        for (String key : getKeys("regions.max-region-count-per-player")) {
            if (!key.equalsIgnoreCase("default")) {
                Object val = getProperty("regions.max-region-count-per-player." + key);
                if (val instanceof Number) {
                    maxRegionCounts.put(key, ((Number) val).intValue());
                }
            }
        }

        // useiConomy = getBoolean("iconomy.enable", false);
        // buyOnClaim = getBoolean("iconomy.buy-on-claim", false);
        // buyOnClaimPrice = getDouble("iconomy.buy-on-claim-price", 1.0);

        blockCreatureSpawn = new HashSet<>();
        for (String creatureName : getStringList("mobs.block-creature-spawn", null)) {
            EntityType creature = EntityTypes.get(creatureName.toLowerCase());

            if (creature == null) {
                log.warning("Unknown entity type '" + creatureName + "'");
            } else {
                blockCreatureSpawn.add(creature);
            }
        }

        this.needsBlockListener = this.noPhysicsGravel || this.noPhysicsSand || this.allowPortalAnywhere || this.ropeLadders;

        boolean useBlacklistAsWhitelist = getBoolean("blacklist.use-as-whitelist", false);

        // Console log configuration
        boolean logConsole = getBoolean("blacklist.logging.console.enable", true);

        // Database log configuration
        boolean logDatabase = getBoolean("blacklist.logging.database.enable", false);
        String dsn = getString("blacklist.logging.database.dsn", "jdbc:mysql://localhost:3306/minecraft");
        String user = getString("blacklist.logging.database.user", "root");
        String pass = getString("blacklist.logging.database.pass", "");
        String table = getString("blacklist.logging.database.table", "blacklist_events");

        // File log configuration
        boolean logFile = getBoolean("blacklist.logging.file.enable", false);
        String logFilePattern = getString("blacklist.logging.file.path", "worldguard/logs/%Y-%m-%d.log");
        int logFileCacheSize = Math.max(1, getInt("blacklist.logging.file.open-files", 10));

        // Load the blacklist
        try {
            // If there was an existing blacklist, close loggers
            if (blacklist != null) {
                blacklist.getLogger().close();
            }

            // First load the blacklist data from worldguard-blacklist.txt
            Blacklist blist = new Blacklist(useBlacklistAsWhitelist);
            blist.load(blacklistFile);

            // If the blacklist is empty, then set the field to null
            // and save some resources
            if (blist.isEmpty()) {
                this.blacklist = null;
            } else {
                this.blacklist = blist;
                if (summaryOnStart) {
                    log.log(Level.INFO, "({0}) Blacklist loaded with {1} entries.",
                            new Object[]{worldName, blacklist.getItemCount()});
                }

                BlacklistLoggerHandler blacklistLogger = blist.getLogger();

                if (logDatabase) {
                    blacklistLogger.addHandler(new DatabaseHandler(dsn, user, pass, table, worldName, log));
                }

                if (logConsole) {
                    blacklistLogger.addHandler(new ConsoleHandler(worldName, log));
                }

                if (logFile) {
                    FileHandler handler =
                            new FileHandler(logFilePattern, logFileCacheSize, worldName, log);
                    blacklistLogger.addHandler(handler);
                }
            }
        } catch (FileNotFoundException e) {
            log.log(Level.WARNING, "WorldGuard blacklist does not exist.");
        } catch (IOException e) {
            log.log(Level.WARNING, "Could not load WorldGuard blacklist: "
                    + e.getMessage());
        }

        // Print an overview of settings
        if (summaryOnStart) {
            log.log(Level.INFO, blockTNTExplosions
                    ? "(" + worldName + ") TNT ignition is blocked."
                    : "(" + worldName + ") TNT ignition is PERMITTED.");
            log.log(Level.INFO, blockLighter
                    ? "(" + worldName + ") Lighters are blocked."
                    : "(" + worldName + ") Lighters are PERMITTED.");
            log.log(Level.INFO, preventLavaFire
                    ? "(" + worldName + ") Lava fire is blocked."
                    : "(" + worldName + ") Lava fire is PERMITTED.");

            if (disableFireSpread) {
                log.log(Level.INFO, "(" + worldName + ") All fire spread is disabled.");
            } else {
                if (!disableFireSpreadBlocks.isEmpty()) {
                    log.log(Level.INFO, "(" + worldName
                            + ") Fire spread is limited to "
                            + disableFireSpreadBlocks.size() + " block types.");
                } else {
                    log.log(Level.INFO, "(" + worldName
                            + ") Fire spread is UNRESTRICTED.");
                }
            }
        }

        config.setHeader(CONFIG_HEADER);

        config.save();
    }

    public boolean isChestProtected(Location block, LocalPlayer player) {
        if (!signChestProtection) {
            return false;
        }
        if (player.hasPermission("worldguard.chest-protection.override")
                || player.hasPermission("worldguard.override.chest-protection")) {
            return false;
        }
        return chestProtection.isProtected(block, player);
    }

    public boolean isChestProtected(Location block) {

        return signChestProtection && chestProtection.isProtected(block, null);
    }

    public boolean isChestProtectedPlacement(Location block, LocalPlayer player) {
        if (!signChestProtection) {
            return false;
        }
        if (player.hasPermission("worldguard.chest-protection.override")
                || player.hasPermission("worldguard.override.chest-protection")) {
            return false;
        }
        return chestProtection.isProtectedPlacement(block, player);
    }

    public boolean isAdjacentChestProtected(Location block, LocalPlayer player) {
        if (!signChestProtection) {
            return false;
        }
        if (player.hasPermission("worldguard.chest-protection.override")
                || player.hasPermission("worldguard.override.chest-protection")) {
            return false;
        }
        return chestProtection.isAdjacentChestProtected(block, player);
    }

    public ChestProtection getChestProtection() {
        return chestProtection;
    }

}
