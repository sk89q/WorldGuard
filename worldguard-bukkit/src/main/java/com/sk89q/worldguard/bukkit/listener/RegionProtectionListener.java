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

package com.sk89q.worldguard.bukkit.listener;

import com.google.common.base.Predicate;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.bukkit.cause.Cause;
import com.sk89q.worldguard.bukkit.event.DelegateEvent;
import com.sk89q.worldguard.bukkit.event.block.BreakBlockEvent;
import com.sk89q.worldguard.bukkit.event.block.PlaceBlockEvent;
import com.sk89q.worldguard.bukkit.event.block.UseBlockEvent;
import com.sk89q.worldguard.bukkit.event.entity.DamageEntityEvent;
import com.sk89q.worldguard.bukkit.event.entity.DestroyEntityEvent;
import com.sk89q.worldguard.bukkit.event.entity.SpawnEntityEvent;
import com.sk89q.worldguard.bukkit.event.entity.UseEntityEvent;
import com.sk89q.worldguard.bukkit.internal.WGMetadata;
import com.sk89q.worldguard.bukkit.protection.events.DisallowedPVPEvent;
import com.sk89q.worldguard.bukkit.util.Entities;
import com.sk89q.worldguard.bukkit.util.Events;
import com.sk89q.worldguard.bukkit.util.InteropUtils;
import com.sk89q.worldguard.bukkit.util.Materials;
import com.sk89q.worldguard.commands.CommandUtils;
import com.sk89q.worldguard.config.WorldConfiguration;
import com.sk89q.worldguard.protection.association.RegionAssociable;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.flags.StateFlag.State;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.event.Event;
import org.bukkit.event.Event.Result;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerTakeLecternBookEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Handle events that need to be processed by region protection.
 */
public class RegionProtectionListener extends AbstractListener {

    private static final String DENY_MESSAGE_KEY = "worldguard.region.lastMessage";
    private static final String DISEMBARK_MESSAGE_KEY = "worldguard.region.disembarkMessage";
    private static final int LAST_MESSAGE_DELAY = 500;

    /**
     * Construct the listener.
     *
     * @param plugin an instance of WorldGuardPlugin
     */
    public RegionProtectionListener(WorldGuardPlugin plugin) {
        super(plugin);
    }

    /**
     * Tell a sender that s/he cannot do something 'here'.
     *
     * @param event the event
     * @param cause the cause
     * @param location the location
     * @param what what was done
     */
    private void tellErrorMessage(DelegateEvent event, Cause cause, Location location, String what) {
        if (event.isSilent() || cause.isIndirect()) {
            return;
        }

        Object rootCause = cause.getRootCause();

        if (rootCause instanceof Player) {
            Player player = (Player) rootCause;

            long now = System.currentTimeMillis();
            Long lastTime = WGMetadata.getIfPresent(player, DENY_MESSAGE_KEY, Long.class);
            if (lastTime == null || now - lastTime >= LAST_MESSAGE_DELAY) {
                RegionQuery query = WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery();
                LocalPlayer localPlayer = getPlugin().wrapPlayer(player);
                String message = query.queryValue(BukkitAdapter.adapt(location), localPlayer, Flags.DENY_MESSAGE);
                formatAndSendDenyMessage(what, localPlayer, message);
                WGMetadata.put(player, DENY_MESSAGE_KEY, now);
            }
        }
    }

    static void formatAndSendDenyMessage(String what, LocalPlayer localPlayer, String message) {
        if (message == null || message.isEmpty()) return;
        message = WorldGuard.getInstance().getPlatform().getMatcher().replaceMacros(localPlayer, message);
        message = CommandUtils.replaceColorMacros(message);
        localPlayer.printRaw(message.replace("%what%", what));
    }

    /**
     * Return whether the given cause is whitelist (should be ignored).
     *
     * @param cause the cause
     * @param world the world
     * @param pvp whether the event in question is PvP combat
     * @return true if whitelisted
     */
    private boolean isWhitelisted(Cause cause, World world, boolean pvp) {
        Object rootCause = cause.getRootCause();

        if (rootCause instanceof Player) {
            Player player = (Player) rootCause;
            LocalPlayer localPlayer = WorldGuardPlugin.inst().wrapPlayer(player);
            com.sk89q.worldedit.world.World localWorld = BukkitAdapter.adapt(world);
            WorldConfiguration config = getWorldConfig(localWorld);

            if (config.fakePlayerBuildOverride && InteropUtils.isFakePlayer(player)) {
                return true;
            }

            return !pvp && WorldGuard.getInstance().getPlatform().getSessionManager().hasBypass(localPlayer, localWorld);
        } else {
            return false;
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlaceBlock(final PlaceBlockEvent event) {
        if (event.getResult() == Result.ALLOW) return; // Don't care about events that have been pre-allowed
        if (!isRegionSupportEnabled(BukkitAdapter.adapt(event.getWorld()))) return; // Region support disabled
        if (isWhitelisted(event.getCause(), event.getWorld(), false)) return; // Whitelisted cause

        final Material type = event.getEffectiveMaterial();
        final RegionQuery query = WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery();
        final RegionAssociable associable = createRegionAssociable(event.getCause());

        // Don't check liquid flow unless it's enabled
        if (event.getCause().getRootCause() instanceof Block
                && Materials.isLiquid(type)
                && !getWorldConfig(BukkitAdapter.adapt(event.getWorld())).checkLiquidFlow) {
            return;
        }

        event.filter((Predicate<Location>) target -> {
            boolean canPlace;
            String what;

            /* Flint and steel, fire charge, etc. */
            if (Materials.isFire(type)) {
                Block block = event.getCause().getFirstBlock();
                boolean fire = block != null && Materials.isFire(type);
                boolean lava = block != null && Materials.isLava(block.getType());
                List<StateFlag> flags = new ArrayList<>();
                flags.add(Flags.BLOCK_PLACE);
                flags.add(Flags.LIGHTER);
                if (fire) flags.add(Flags.FIRE_SPREAD);
                if (lava) flags.add(Flags.LAVA_FIRE);
                canPlace = query.testBuild(BukkitAdapter.adapt(target), associable, combine(event, flags.toArray(new StateFlag[flags.size()])));
                what = "place fire";

            } else if (type == Material.FROSTED_ICE) {
                event.setSilent(true); // gets spammy
                canPlace = query.testBuild(BukkitAdapter.adapt(target), associable, combine(event, Flags.BLOCK_PLACE, Flags.FROSTED_ICE_FORM));
                what = "use frostwalker"; // hidden anyway
            /* Everything else */
            } else {
                canPlace = query.testBuild(BukkitAdapter.adapt(target), associable, combine(event, Flags.BLOCK_PLACE));
                what = "place that block";
            }

            if (!canPlace) {
                tellErrorMessage(event, event.getCause(), target, what);
                return false;
            }

            return true;
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreakBlock(final BreakBlockEvent event) {
        if (event.getResult() == Result.ALLOW) return; // Don't care about events that have been pre-allowed
        if (!isRegionSupportEnabled(BukkitAdapter.adapt(event.getWorld()))) return; // Region support disabled
        if (isWhitelisted(event.getCause(), event.getWorld(), false)) return; // Whitelisted cause

        final RegionQuery query = WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery();

        if (!event.isCancelled()) {
            final RegionAssociable associable = createRegionAssociable(event.getCause());

            event.filter((Predicate<Location>) target -> {
                boolean canBreak;
                String what;

                /* TNT */
                if (event.getCause().find(EntityType.PRIMED_TNT, EntityType.MINECART_TNT) != null) {
                    canBreak = query.testBuild(BukkitAdapter.adapt(target), associable, combine(event, Flags.BLOCK_BREAK, Flags.TNT));
                    what = "use dynamite";

                /* Everything else */
                } else {
                    canBreak = query.testBuild(BukkitAdapter.adapt(target), associable, combine(event, Flags.BLOCK_BREAK));
                    what = "break that block";
                }

                if (!canBreak) {
                    tellErrorMessage(event, event.getCause(), target, what);
                    return false;
                }

                return true;
            });
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onUseBlock(final UseBlockEvent event) {
        if (event.getResult() == Result.ALLOW) return; // Don't care about events that have been pre-allowed
        if (!isRegionSupportEnabled(BukkitAdapter.adapt(event.getWorld()))) return; // Region support disabled
        if (isWhitelisted(event.getCause(), event.getWorld(), false)) return; // Whitelisted cause

        final Material type = event.getEffectiveMaterial();
        final RegionQuery query = WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery();
        final RegionAssociable associable = createRegionAssociable(event.getCause());

        event.filter((Predicate<Location>) target -> {
            boolean canUse;
            String what;

            /* Saplings, etc. */
            if (Materials.isConsideredBuildingIfUsed(type)) {
                canUse = query.testBuild(BukkitAdapter.adapt(target), associable, combine(event));
                what = "use that";

            /* Inventory */
            } else if (Materials.isInventoryBlock(type)) {
                canUse = query.testBuild(BukkitAdapter.adapt(target), associable, combine(event, Flags.CHEST_ACCESS));
                what = "open that";

            /* Inventory for blocks with the possibility to be only use, e.g. lectern */
            } else if (handleAsInventoryUsage(event.getOriginalEvent())) {
                canUse = query.testBuild(BukkitAdapter.adapt(target), associable, combine(event, Flags.CHEST_ACCESS));
                what = "take that";

            /* Anvils */
            } else if (Materials.isAnvil(type)) {
                canUse = query.testBuild(BukkitAdapter.adapt(target), associable, combine(event, Flags.USE_ANVIL));
                what = "use that";

            /* Beds */
            } else if (Materials.isBed(type)) {
                canUse = query.testBuild(BukkitAdapter.adapt(target), associable, combine(event, Flags.INTERACT, Flags.SLEEP));
                what = "sleep";

            /* Respawn Anchors */
            } else if(type == Material.RESPAWN_ANCHOR) {
                canUse = query.testBuild(BukkitAdapter.adapt(target), associable, combine(event, Flags.INTERACT, Flags.RESPAWN_ANCHORS));
                what = "use anchors";

            /* TNT */
            } else if (type == Material.TNT) {
                canUse = query.testBuild(BukkitAdapter.adapt(target), associable, combine(event, Flags.INTERACT, Flags.TNT));
                what = "use explosives";

            /* Legacy USE flag */
            } else if (Materials.isUseFlagApplicable(type)) {
                canUse = query.testBuild(BukkitAdapter.adapt(target), associable, combine(event, Flags.INTERACT, Flags.USE));
                what = "use that";

            /* Everything else */
            } else {
                canUse = query.testBuild(BukkitAdapter.adapt(target), associable, combine(event, Flags.INTERACT));
                what = "use that";
            }

            if (!canUse) {
                tellErrorMessage(event, event.getCause(), target, what);
                return false;
            }

            return true;
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onSpawnEntity(SpawnEntityEvent event) {
        if (event.getResult() == Result.ALLOW) return; // Don't care about events that have been pre-allowed
        if (!isRegionSupportEnabled(BukkitAdapter.adapt(event.getWorld()))) return; // Region support disabled
        if (isWhitelisted(event.getCause(), event.getWorld(), false)) return; // Whitelisted cause

        Location target = event.getTarget();
        EntityType type = event.getEffectiveType();

        RegionQuery query = WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery();
        RegionAssociable associable = createRegionAssociable(event.getCause());

        boolean canSpawn;
        String what;

        /* Vehicles */
        if (Entities.isVehicle(type)) {
            canSpawn = query.testBuild(BukkitAdapter.adapt(target), associable, combine(event, Flags.PLACE_VEHICLE));
            what = "place vehicles";

        /* Item pickup */
        } else if (event.getEntity() instanceof Item) {
            canSpawn = query.testBuild(BukkitAdapter.adapt(target), associable, combine(event, Flags.ITEM_DROP));
            what = "drop items";

        /* XP drops */
        } else if (type == EntityType.EXPERIENCE_ORB) {
            canSpawn = query.testBuild(BukkitAdapter.adapt(target), associable, combine(event, Flags.EXP_DROPS));
            what = "drop XP";

        } else if (Entities.isAoECloud(type)) {
            canSpawn = query.testBuild(BukkitAdapter.adapt(target), associable, combine(event, Flags.POTION_SPLASH));
            what = "use lingering potions";

        /* Everything else */
        } else {
            canSpawn = query.testBuild(BukkitAdapter.adapt(target), associable, combine(event));
            what = "place things";
        }

        if (!canSpawn) {
            tellErrorMessage(event, event.getCause(), target, what);
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDestroyEntity(DestroyEntityEvent event) {
        if (event.getResult() == Result.ALLOW) return; // Don't care about events that have been pre-allowed
        if (!isRegionSupportEnabled(BukkitAdapter.adapt(event.getWorld()))) return; // Region support disabled
        if (isWhitelisted(event.getCause(), event.getWorld(), false)) return; // Whitelisted cause

        Location target = event.getTarget();
        EntityType type = event.getEntity().getType();
        RegionAssociable associable = createRegionAssociable(event.getCause());

        RegionQuery query = WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery();
        boolean canDestroy;
        String what;

        /* Vehicles */
        if (Entities.isVehicle(type)) {
            canDestroy = query.testBuild(BukkitAdapter.adapt(target), associable, combine(event, Flags.DESTROY_VEHICLE));
            what = "break vehicles";

        /* Item pickup */
        } else if (event.getEntity() instanceof Item || event.getEntity() instanceof ExperienceOrb) {
            canDestroy = query.testBuild(BukkitAdapter.adapt(target), associable, combine(event, Flags.ITEM_PICKUP));
            what = "pick up items";

        /* Everything else */
        } else {
            canDestroy = query.testBuild(BukkitAdapter.adapt(target), associable, combine(event));
            what = "break things";
        }

        if (!canDestroy) {
            tellErrorMessage(event, event.getCause(), target, what);
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onUseEntity(UseEntityEvent event) {
        if (event.getResult() == Result.ALLOW) return; // Don't care about events that have been pre-allowed
        if (!isRegionSupportEnabled(BukkitAdapter.adapt(event.getWorld()))) return; // Region support disabled
        if (isWhitelisted(event.getCause(), event.getWorld(), false)) return; // Whitelisted cause

        Location target = event.getTarget();
        RegionAssociable associable = createRegionAssociable(event.getCause());

        RegionQuery query = WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery();
        boolean canUse;
        String what;

        /* Hostile / ambient mob override */
        final Entity entity = event.getEntity();
        final EntityType type = entity.getType();
        if (Entities.isHostile(entity) || Entities.isAmbient(entity)
                || Entities.isNPC(entity) || entity instanceof Player) {
            canUse = event.getRelevantFlags().isEmpty() || query.queryState(BukkitAdapter.adapt(target), associable, combine(event)) != State.DENY;
            what = "use that";
        /* Paintings, item frames, etc. */
        } else if (Entities.isConsideredBuildingIfUsed(entity)) {
            if (type == EntityType.ITEM_FRAME && event.getCause().getFirstPlayer() != null
                    && ((ItemFrame) entity).getItem().getType() != Material.AIR) {
                canUse = query.testBuild(BukkitAdapter.adapt(target), associable, combine(event, Flags.ITEM_FRAME_ROTATE));
                what = "change that";
            } else if (Entities.isMinecart(type)) {
                canUse = query.testBuild(BukkitAdapter.adapt(target), associable, combine(event, Flags.CHEST_ACCESS));
                what = "open that";
            } else {
                canUse = query.testBuild(BukkitAdapter.adapt(target), associable, combine(event));
                what = "change that";
            }
        /* Ridden on use */
        } else if (Entities.isRiddenOnUse(entity)) {
            StateFlag flagToCheck;
            if (entity instanceof Tameable){
                flagToCheck = Flags.MOUNT_ANIMALS;
            }else {
                flagToCheck = Flags.MOUNT_VEHICLES;
            }
            // backward compatibility – if Flag.RIDE is allowed, just allow
            canUse = query.testBuild(BukkitAdapter.adapt(target), associable, combine(event, Flags.RIDE, Flags.INTERACT))
                || query.testBuild(BukkitAdapter.adapt(target), associable, combine(event, flagToCheck));
            what = "ride that";

        /* Everything else */
        } else {
            canUse = query.testBuild(BukkitAdapter.adapt(target), associable, combine(event, Flags.INTERACT));
            what = "use that";
        }

        if (!canUse) {
            tellErrorMessage(event, event.getCause(), target, what);
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamageEntity(DamageEntityEvent event) {
        if (event.getResult() == Result.ALLOW) return; // Don't care about events that have been pre-allowed
        if (!isRegionSupportEnabled(BukkitAdapter.adapt(event.getWorld()))) return; // Region support disabled
        // Whitelist check is below

        com.sk89q.worldedit.util.Location target = BukkitAdapter.adapt(event.getTarget());
        RegionAssociable associable = createRegionAssociable(event.getCause());

        RegionQuery query = WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery();
        Player playerAttacker = event.getCause().getFirstPlayer();
        boolean canDamage;
        String what;

        // Block PvP like normal even if the player has an override permission
        // because (1) this is a frequent source of confusion and
        // (2) some users want to block PvP even with the bypass permission
        boolean pvp = event.getEntity() instanceof Player && playerAttacker != null && !playerAttacker.equals(event.getEntity());
        if (isWhitelisted(event.getCause(), event.getWorld(), pvp)) {
            return;
        }

        /* Hostile / ambient mob override */
        if (Entities.isHostile(event.getEntity()) || Entities.isAmbient(event.getEntity())
                || Entities.isVehicle(event.getEntity().getType())) {
            canDamage = event.getRelevantFlags().isEmpty() || query.queryState(target, associable, combine(event)) != State.DENY;
            what = "hit that";

        /* Paintings, item frames, etc. */
        } else if (Entities.isConsideredBuildingIfUsed(event.getEntity())) {
            canDamage = query.testBuild(target, associable, combine(event));
            what = "change that";

        /* PVP */
        } else if (pvp) {
            LocalPlayer localAttacker = WorldGuardPlugin.inst().wrapPlayer(playerAttacker);
            Player defender = (Player) event.getEntity();

            // if defender is an NPC
            if (Entities.isNPC(defender)) {
                return;
            }

            canDamage = query.testBuild(target, associable, combine(event, Flags.PVP))
                    && query.queryState(localAttacker.getLocation(), localAttacker, combine(event, Flags.PVP)) != State.DENY
                    && query.queryState(target, localAttacker, combine(event, Flags.PVP)) != State.DENY;

            // Fire the disallow PVP event
            if (!canDamage && Events.fireAndTestCancel(new DisallowedPVPEvent(playerAttacker, defender, event.getOriginalEvent()))) {
                canDamage = true;
            }

            what = "PvP";

        /* Player damage not caused  by another player */
        } else if (event.getEntity() instanceof Player) {
            canDamage = event.getRelevantFlags().isEmpty() || query.queryState(target, associable, combine(event)) != State.DENY;
            what = "damage that";

        /* damage to non-hostile mobs (e.g. animals) */
        } else if (Entities.isNonHostile(event.getEntity())) {
            canDamage = query.testBuild(target, associable, combine(event, Flags.DAMAGE_ANIMALS));
            what = "harm that";

        /* Everything else */
        } else {
            canDamage = query.testBuild(target, associable, combine(event, Flags.INTERACT));
            what = "hit that";
        }

        if (!canDamage) {
            tellErrorMessage(event, event.getCause(), event.getTarget(), what);
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onVehicleExit(VehicleExitEvent event) {
        Entity vehicle = event.getVehicle();
        if (!isRegionSupportEnabled(BukkitAdapter.adapt(vehicle.getWorld()))) return; // Region support disabled
        Entity exited = event.getExited();

        if(exited instanceof Player){
            Player player = (Player) exited;
            LocalPlayer localPlayer = WorldGuardPlugin.inst().wrapPlayer(player);
            StateFlag flagToCheck;
            if (vehicle instanceof Tameable){
                flagToCheck = Flags.DISMOUNT_ANIMALS;
            } else {
                flagToCheck = Flags.DISMOUNT_VEHICLES;
            }
            if (!isWhitelisted(Cause.create(player), vehicle.getWorld(), false)) {
                RegionQuery query = WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery();
                Location location = vehicle.getLocation();
                // don't let the INTERACT flag override dismounting
                // the player might need to interact with world objects while staying mounted (buttons, doors etc)
                // for backward compatibility, still allow the old Flags.RIDE | Flags.INTERACT
                if (!query.testBuild(BukkitAdapter.adapt(location), localPlayer, flagToCheck)
                    && !query.testBuild(BukkitAdapter.adapt(location), localPlayer, Flags.RIDE, Flags.INTERACT)) {
                    long now = System.currentTimeMillis();
                    Long lastTime = WGMetadata.getIfPresent(player, DISEMBARK_MESSAGE_KEY, Long.class);
                    if (lastTime == null || now - lastTime >= LAST_MESSAGE_DELAY) {
                        player.sendMessage("" + ChatColor.GOLD + "Don't disembark here!" + ChatColor.GRAY + " You can't get back on.");
                        WGMetadata.put(player, DISEMBARK_MESSAGE_KEY, now);
                    }

                    event.setCancelled(true);
                }
            }
        }
    }

    private boolean isWhitelistedEntity(Entity entity) {
        return Entities.isNonPlayerCreature(entity);
    }

    /**
     * Combine the flags from a delegate event with an array of flags.
     *
     * <p>The delegate event's flags appear at the end.</p>
     *
     * @param event The event
     * @param flag An array of flags
     * @return An array of flags
     */
    private static StateFlag[] combine(DelegateEvent event, StateFlag... flag) {
        List<StateFlag> extra = event.getRelevantFlags();
        StateFlag[] flags = Arrays.copyOf(flag, flag.length + extra.size());
        for (int i = 0; i < extra.size(); i++) {
            flags[flag.length + i] = extra.get(i);
        }
        return flags;
    }

    /**
     * Check if that event should be handled as inventory usage, e.g. if a player takes a book from a lectern
     *
     * @param event the event to handle
     * @return whether it should be handled as inventory usage
     */
    private static boolean handleAsInventoryUsage(Event event) {
        return event instanceof PlayerTakeLecternBookEvent;
    }

}
