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

package com.sk89q.worldguard.protection.flags;

import com.google.common.collect.Sets;
import com.sk89q.worldedit.util.formatting.text.TextComponent;
import com.sk89q.worldedit.util.formatting.text.format.TextColor;
import com.sk89q.worldedit.util.formatting.text.format.TextDecoration;
import com.sk89q.worldedit.util.formatting.text.serializer.legacy.LegacyComponentSerializer;
import com.sk89q.worldedit.world.entity.EntityType;
import com.sk89q.worldedit.world.gamemode.GameMode;
import com.sk89q.worldedit.world.weather.WeatherType;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.flags.registry.FlagConflictException;
import com.sk89q.worldguard.protection.flags.registry.FlagRegistry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * The flags that are used in WorldGuard.
 */
public final class Flags {

    private static final List<String> INBUILT_FLAGS_LIST = new ArrayList<>();
    public static final List<String> INBUILT_FLAGS = Collections.unmodifiableList(INBUILT_FLAGS_LIST);

    // Overrides membership check
    public static final StateFlag PASSTHROUGH = register(new StateFlag("passthrough", false));
    public static final SetFlag<String> NONPLAYER_PROTECTION_DOMAINS = register(new SetFlag<>("nonplayer-protection-domains", null, new StringFlag(null)));

    // This flag is unlike the others. It forces the checking of region membership
    public static final StateFlag BUILD = register(new BuildFlag("build", true));

    // These flags are used in tandem with the BUILD flag - if the player can
    // build, then the following flags do not need to be checked (although they
    // are still checked for DENY), so they are false by default
    public static final StateFlag BLOCK_BREAK = register(new StateFlag("block-break", false));
    public static final StateFlag BLOCK_PLACE = register(new StateFlag("block-place", false));
    public static final StateFlag USE = register(new StateFlag("use", false));
    public static final StateFlag INTERACT = register(new StateFlag("interact", false));
    public static final StateFlag DAMAGE_ANIMALS = register(new StateFlag("damage-animals", false));
    public static final StateFlag PVP = register(new StateFlag("pvp", false));
    public static final StateFlag SLEEP = register(new StateFlag("sleep", false));
    public static final StateFlag RESPAWN_ANCHORS = register(new StateFlag("respawn-anchors", false));
    public static final StateFlag TNT = register(new StateFlag("tnt", false));
    public static final StateFlag CHEST_ACCESS = register(new StateFlag("chest-access", false));
    public static final StateFlag PLACE_VEHICLE = register(new StateFlag("vehicle-place", false));
    public static final StateFlag DESTROY_VEHICLE = register(new StateFlag("vehicle-destroy", false));
    public static final StateFlag LIGHTER = register(new StateFlag("lighter", false));
    public static final StateFlag RIDE = register(new StateFlag("ride", false));
    public static final StateFlag POTION_SPLASH = register(new StateFlag("potion-splash", false));
    public static final StateFlag ITEM_FRAME_ROTATE = register(new StateFlag("item-frame-rotation", false));
    public static final StateFlag TRAMPLE_BLOCKS = register(new StateFlag("block-trampling", false));
    public static final StateFlag FIREWORK_DAMAGE = register(new StateFlag("firework-damage", false));
    public static final StateFlag USE_ANVIL = register(new StateFlag("use-anvil", false));
    public static final StateFlag USE_DRIPLEAF = register(new StateFlag("use-dripleaf", false));

    // These flags are similar to the ones above (used in tandem with BUILD),
    // but their defaults are set to TRUE because it is more user friendly.
    // However, it is not possible to disable these flags by default in all
    // regions because setting DENY in __global__ would also override the
    // BUILD flag. In the future, StateFlags will need a DISALLOW state.
    public static final StateFlag ITEM_PICKUP = register(new StateFlag("item-pickup", true)); // Intentionally true
    public static final StateFlag ITEM_DROP = register(new StateFlag("item-drop", true)); // Intentionally true
    public static final StateFlag EXP_DROPS = register(new StateFlag("exp-drops", true)); // Intentionally true

    // These flags adjust behavior and are not checked in tandem with the
    // BUILD flag so they need to be TRUE for their defaults.

    // mob griefing related
    public static final StateFlag MOB_DAMAGE = register(new StateFlag("mob-damage", true));
    public static final StateFlag CREEPER_EXPLOSION = register(new StateFlag("creeper-explosion", true));
    public static final StateFlag ENDERDRAGON_BLOCK_DAMAGE = register(new StateFlag("enderdragon-block-damage", true));
    public static final StateFlag GHAST_FIREBALL = register(new StateFlag("ghast-fireball", true));
    public static final StateFlag OTHER_EXPLOSION = register(new StateFlag("other-explosion", true));
    public static final StateFlag WITHER_DAMAGE = register(new StateFlag("wither-damage", true));
    public static final StateFlag ENDER_BUILD = register(new StateFlag("enderman-grief", true));
    public static final StateFlag SNOWMAN_TRAILS = register(new StateFlag("snowman-trails", true));
    public static final StateFlag RAVAGER_RAVAGE = register(new StateFlag("ravager-grief", true));
    public static final StateFlag ENTITY_PAINTING_DESTROY = register(new StateFlag("entity-painting-destroy", true));
    public static final StateFlag ENTITY_ITEM_FRAME_DESTROY = register(new StateFlag("entity-item-frame-destroy", true));

    // mob spawning related
    public static final StateFlag MOB_SPAWNING = register(new StateFlag("mob-spawning", true));
    public static final SetFlag<EntityType> DENY_SPAWN = register(new SetFlag<>("deny-spawn", new RegistryFlag<>(null, EntityType.REGISTRY)));

    // block dynamics
    public static final StateFlag PISTONS = register(new StateFlag("pistons", true));
    public static final StateFlag FIRE_SPREAD = register(new StateFlag("fire-spread", true));
    public static final StateFlag LAVA_FIRE = register(new StateFlag("lava-fire", true));
    public static final StateFlag LIGHTNING = register(new StateFlag("lightning", true));
    public static final StateFlag SNOW_FALL = register(new StateFlag("snow-fall", true));
    public static final StateFlag SNOW_MELT = register(new StateFlag("snow-melt", true));
    public static final StateFlag ICE_FORM = register(new StateFlag("ice-form", true));
    public static final StateFlag ICE_MELT = register(new StateFlag("ice-melt", true));
    public static final StateFlag FROSTED_ICE_MELT = register(new StateFlag("frosted-ice-melt", true));
    public static final StateFlag FROSTED_ICE_FORM = register(new StateFlag("frosted-ice-form", false)); // this belongs in the first category of "checked with build"
    public static final StateFlag MUSHROOMS = register(new StateFlag("mushroom-growth", true));
    public static final StateFlag LEAF_DECAY = register(new StateFlag("leaf-decay", true));
    public static final StateFlag GRASS_SPREAD = register(new StateFlag("grass-growth", true));
    public static final StateFlag MYCELIUM_SPREAD = register(new StateFlag("mycelium-spread", true));
    public static final StateFlag VINE_GROWTH = register(new StateFlag("vine-growth", true));
    public static final StateFlag CROP_GROWTH = register(new StateFlag("crop-growth", true));
    public static final StateFlag SOIL_DRY = register(new StateFlag("soil-dry", true));
    public static final StateFlag CORAL_FADE = register(new StateFlag("coral-fade", true));
    public static final StateFlag WATER_FLOW = register(new StateFlag("water-flow", true));
    public static final StateFlag LAVA_FLOW = register(new StateFlag("lava-flow", true));

    public static final RegistryFlag<WeatherType> WEATHER_LOCK = register(new RegistryFlag<>("weather-lock", WeatherType.REGISTRY));
    public static final StringFlag TIME_LOCK = register(new StringFlag("time-lock"));

    // chat related flags
    public static final StateFlag SEND_CHAT = register(new StateFlag("send-chat", true));
    public static final StateFlag RECEIVE_CHAT = register(new StateFlag("receive-chat", true));
    public static final SetFlag<String> BLOCKED_CMDS = register(new SetFlag<>("blocked-cmds", new CommandStringFlag(null)));
    public static final SetFlag<String> ALLOWED_CMDS = register(new SetFlag<>("allowed-cmds", new CommandStringFlag(null)));

    // locations
    public static final LocationFlag TELE_LOC = register(new LocationFlag("teleport"));
    public static final LocationFlag SPAWN_LOC = register(new LocationFlag("spawn", RegionGroup.MEMBERS));

    /**
     * @deprecated The type of this flag will change from a StringFlag to a ComponentFlag to support JSON text
     *              in a future release. If you depend on the type of this flag, take proper precaution for future breakage.
     */
    @Deprecated
    public static final StringFlag TELE_MESSAGE = register(new StringFlag("teleport-message",
            LegacyComponentSerializer.INSTANCE.serialize(TextComponent.of("").append(TextComponent.of(
                    "Teleported you to the region '%id%'.", TextColor.LIGHT_PURPLE)))));

    // idk?
    public static final StateFlag INVINCIBILITY = register(new StateFlag("invincible", false));
    public static final StateFlag FALL_DAMAGE = register(new StateFlag("fall-damage", true));
    public static final StateFlag HEALTH_REGEN = register(new StateFlag("natural-health-regen", true));
    public static final StateFlag HUNGER_DRAIN = register(new StateFlag("natural-hunger-drain", true));

    // session and movement based flags
    public static final StateFlag ENTRY = register(new StateFlag("entry", true, RegionGroup.NON_MEMBERS));
    public static final StateFlag EXIT = register(new StateFlag("exit", true, RegionGroup.NON_MEMBERS));
    public static final BooleanFlag EXIT_OVERRIDE = register(new BooleanFlag("exit-override"));
    public static final StateFlag EXIT_VIA_TELEPORT = register(new StateFlag("exit-via-teleport", true));

    public static final StateFlag ENDERPEARL = register(new StateFlag("enderpearl", true));
    public static final StateFlag CHORUS_TELEPORT = register(new StateFlag("chorus-fruit-teleport", true));

    /**
     * @deprecated The type of this flag will change from a StringFlag to a ComponentFlag to support JSON text
     *              in a future release. If you depend on the type of this flag, take proper precaution for future breakage.
     */
    @Deprecated
    public static final StringFlag GREET_MESSAGE = register(new StringFlag("greeting"));
    /**
     * @deprecated The type of this flag will change from a StringFlag to a ComponentFlag to support JSON text
     *              in a future release. If you depend on the type of this flag, take proper precaution for future breakage.
     */
    @Deprecated
    public static final StringFlag FAREWELL_MESSAGE = register(new StringFlag("farewell"));
    /**
     * @deprecated The type of this flag will change from a StringFlag to a ComponentFlag to support JSON text
     *              in a future release. If you depend on the type of this flag, take proper precaution for future breakage.
     */
    @Deprecated
    public static final StringFlag GREET_TITLE = register(new StringFlag("greeting-title"));
    /**
     * @deprecated The type of this flag will change from a StringFlag to a ComponentFlag to support JSON text
     *              in a future release. If you depend on the type of this flag, take proper precaution for future breakage.
     */
    @Deprecated
    public static final StringFlag FAREWELL_TITLE = register(new StringFlag("farewell-title"));

    public static final BooleanFlag NOTIFY_ENTER = register(new BooleanFlag("notify-enter"));
    public static final BooleanFlag NOTIFY_LEAVE = register(new BooleanFlag("notify-leave"));

    public static final RegistryFlag<GameMode> GAME_MODE = register(new RegistryFlag<>("game-mode", GameMode.REGISTRY));

    private static final Number[] DELAY_VALUES = {0, 1, 5};
    private static final Number[] VITALS_VALUES = {0, 5, 10, 20};
    private static final Number[] VITALS_MINS = {0, 10};
    private static final Number[] VITALS_MAXS = {10, 20};

    public static final IntegerFlag HEAL_DELAY = register(new IntegerFlag("heal-delay"), f -> f.setSuggestedValues(DELAY_VALUES));
    public static final IntegerFlag HEAL_AMOUNT = register(new IntegerFlag("heal-amount"), f -> f.setSuggestedValues(VITALS_VALUES));
    public static final DoubleFlag MIN_HEAL = register(new DoubleFlag("heal-min-health"), f -> f.setSuggestedValues(VITALS_MINS));
    public static final DoubleFlag MAX_HEAL = register(new DoubleFlag("heal-max-health"), f -> f.setSuggestedValues(VITALS_MAXS));

    public static final IntegerFlag FEED_DELAY = register(new IntegerFlag("feed-delay"), f -> f.setSuggestedValues(DELAY_VALUES));
    public static final IntegerFlag FEED_AMOUNT = register(new IntegerFlag("feed-amount"), f -> f.setSuggestedValues(VITALS_VALUES));
    public static final IntegerFlag MIN_FOOD = register(new IntegerFlag("feed-min-hunger"), f -> f.setSuggestedValues(VITALS_MINS));
    public static final IntegerFlag MAX_FOOD = register(new IntegerFlag("feed-max-hunger"), f -> f.setSuggestedValues(VITALS_MAXS));

    // deny messages
    /**
     * @deprecated The type of this flag will change from a StringFlag to a ComponentFlag to support JSON text
     *              in a future release. If you depend on the type of this flag, take proper precaution for future breakage.
     */
    @Deprecated
    public static final StringFlag DENY_MESSAGE = register(new StringFlag("deny-message",
            LegacyComponentSerializer.INSTANCE.serialize(TextComponent.of("").append(TextComponent.of("Hey!",
                    TextColor.RED, Sets.newHashSet(TextDecoration.BOLD)))
                    .append(TextComponent.of(" Sorry, but you can't %what% here.", TextColor.GRAY)))));
    /**
     * @deprecated The type of this flag will change from a StringFlag to a ComponentFlag to support JSON text
     *              in a future release. If you depend on the type of this flag, take proper precaution for future breakage.
     */
    @Deprecated
    public static final StringFlag ENTRY_DENY_MESSAGE = register(new StringFlag("entry-deny-message",
            LegacyComponentSerializer.INSTANCE.serialize(TextComponent.of("").append(TextComponent.of("Hey!",
                    TextColor.RED, Sets.newHashSet(TextDecoration.BOLD)))
                    .append(TextComponent.of(" You are not permitted to enter this area.", TextColor.GRAY)))));
    /**
     * @deprecated The type of this flag will change from a StringFlag to a ComponentFlag to support JSON text
     *              in a future release. If you depend on the type of this flag, take proper precaution for future breakage.
     */
    @Deprecated
    public static final StringFlag EXIT_DENY_MESSAGE = register(new StringFlag("exit-deny-message",
            LegacyComponentSerializer.INSTANCE.serialize(TextComponent.of("").append(TextComponent.of("Hey!",
                    TextColor.RED, Sets.newHashSet(TextDecoration.BOLD)))
                    .append(TextComponent.of(" You are not permitted to leave this area.", TextColor.GRAY)))));

    private Flags() {
    }

    private static <T extends Flag<?>> T register(final T flag) throws FlagConflictException {
        WorldGuard.getInstance().getFlagRegistry().register(flag);
        INBUILT_FLAGS_LIST.add(flag.getName());
        return flag;
    }

    private static <T extends Flag<?>> T register(final T flag, Consumer<T> cfg) throws FlagConflictException {
        T f = register(flag);
        cfg.accept(f);
        return f;
    }

    /**
     * Try to match the flag with the given ID using a fuzzy name match.
     *
     * @param flagRegistry the flag registry
     * @param id the flag ID
     * @return a flag, or null
     */
    public static Flag<?> fuzzyMatchFlag(FlagRegistry flagRegistry, String id) {
        final String compId = id.replace("-", "");
        for (Flag<?> flag : flagRegistry) {
            if (flag.getName().replace("-", "").equalsIgnoreCase(compId)) {
                return flag;
            }
        }

        return null;
    }

    /**
     * Dummy method to call that initialises the class.
     */
    public static void registerAll() {
    }
}
