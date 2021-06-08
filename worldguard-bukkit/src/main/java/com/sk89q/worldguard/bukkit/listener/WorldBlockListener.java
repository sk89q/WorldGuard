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

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.BukkitWorldConfiguration;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.bukkit.util.Materials;
import com.sk89q.worldguard.config.ConfigurationManager;
import com.sk89q.worldguard.config.WorldConfiguration;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.association.RegionAssociable;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.util.SpongeUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowman;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockIgniteEvent.IgniteCause;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.EntityBlockFormEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * The listener for block events.
 *
 * @author sk89q
 */
public class WorldBlockListener extends AbstractListener {

    /**
     * Construct the object.
     *
     * @param plugin The plugin instance
     */
    public WorldBlockListener(WorldGuardPlugin plugin) {
        super(plugin);
    }

    /*
     * Called when a block is broken.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        WorldConfiguration wcfg = getWorldConfig(player);

        if (!wcfg.itemDurability) {
            ItemStack held = player.getInventory().getItemInMainHand();
            ItemMeta meta = held.getItemMeta();
            if (meta != null) {
                ((Damageable) meta).setDamage(0);
                held.setItemMeta(meta);
                player.getInventory().setItemInMainHand(held);
            }
        }
    }

    /*
     * Called when fluids flow.
     */
    @EventHandler(ignoreCancelled = true)
    public void onBlockFromTo(BlockFromToEvent event) {
        World world = event.getBlock().getWorld();
        Block blockFrom = event.getBlock();
        Block blockTo = event.getToBlock();

        Material fromType = blockFrom.getType();
        boolean isWater = Materials.isWater(fromType);
        boolean isLava = fromType == Material.LAVA;
        boolean isAir = fromType == Material.AIR;

        ConfigurationManager cfg = WorldGuard.getInstance().getPlatform().getGlobalStateManager();
        WorldConfiguration wcfg = getWorldConfig(event.getBlock().getWorld());

        if (cfg.activityHaltToggle) {
            event.setCancelled(true);
            return;
        }

        if (wcfg.simulateSponge && isWater) {
            int ox = blockTo.getX();
            int oy = blockTo.getY();
            int oz = blockTo.getZ();

            for (int cx = -wcfg.spongeRadius; cx <= wcfg.spongeRadius; cx++) {
                for (int cy = -wcfg.spongeRadius; cy <= wcfg.spongeRadius; cy++) {
                    for (int cz = -wcfg.spongeRadius; cz <= wcfg.spongeRadius; cz++) {
                        Block sponge = world.getBlockAt(ox + cx, oy + cy, oz + cz);
                        if (sponge.getType() == Material.SPONGE
                                && (!wcfg.redstoneSponges || !sponge.isBlockIndirectlyPowered())) {
                            event.setCancelled(true);
                            return;
                        }
                    }
                }
            }
        }

        /*if (plugin.classicWater && isWater) {
        int blockBelow = blockFrom.getRelative(0, -1, 0).getTypeId();
        if (blockBelow != 0 && blockBelow != 8 && blockBelow != 9) {
        blockFrom.setTypeId(9);
        if (blockTo.getTypeId() == 0) {
        blockTo.setTypeId(9);
        }
        return;
        }
        }*/

        // Check the fluid block (from) whether it is air.
        // If so and the target block is protected, cancel the event
        if (!wcfg.preventWaterDamage.isEmpty()) {
            Material targetId = blockTo.getType();

            if ((isAir || isWater) &&
                    wcfg.preventWaterDamage.contains(BukkitAdapter.asBlockType(targetId).getId())) {
                event.setCancelled(true);
                return;
            }
        }

        if (!wcfg.allowedLavaSpreadOver.isEmpty() && isLava) {
            Material targetId = blockTo.getRelative(0, -1, 0).getType();

            if (!wcfg.allowedLavaSpreadOver.contains(BukkitAdapter.asBlockType(targetId).getId())) {
                event.setCancelled(true);
                return;
            }
        }

        if (wcfg.highFreqFlags && (isWater || blockFrom.getBlockData() instanceof Waterlogged)
                && WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery().queryState(BukkitAdapter.adapt(blockFrom.getLocation()), (RegionAssociable) null, Flags.WATER_FLOW) == StateFlag.State.DENY) {
            event.setCancelled(true);
            return;
        }

        if (wcfg.highFreqFlags && isLava
                && !StateFlag.test(WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery().queryState(BukkitAdapter.adapt(blockFrom.getLocation()), (RegionAssociable) null, Flags.LAVA_FLOW))) {
            event.setCancelled(true);
            return;
        }
    }

    /*
     * Called when a block gets ignited.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockIgnite(BlockIgniteEvent event) {
        IgniteCause cause = event.getCause();
        Block block = event.getBlock();
        World world = block.getWorld();

        ConfigurationManager cfg = WorldGuard.getInstance().getPlatform().getGlobalStateManager();
        WorldConfiguration wcfg = getWorldConfig(world);

        if (cfg.activityHaltToggle) {
            event.setCancelled(true);
            return;
        }
        boolean isFireSpread = cause == IgniteCause.SPREAD;

        if (wcfg.preventLightningFire && cause == IgniteCause.LIGHTNING) {
            event.setCancelled(true);
            return;
        }

        if (wcfg.preventLavaFire && cause == IgniteCause.LAVA) {
            event.setCancelled(true);
            return;
        }

        if (wcfg.disableFireSpread && isFireSpread) {
            event.setCancelled(true);
            return;
        }

        if (wcfg.blockLighter && (cause == IgniteCause.FLINT_AND_STEEL || cause == IgniteCause.FIREBALL)
                && event.getPlayer() != null
                && !getPlugin().hasPermission(event.getPlayer(), "worldguard.override.lighter")) {
            event.setCancelled(true);
            return;
        }

        if (wcfg.fireSpreadDisableToggle && isFireSpread) {
            event.setCancelled(true);
            return;
        }

        if (!wcfg.disableFireSpreadBlocks.isEmpty() && isFireSpread) {
            int x = block.getX();
            int y = block.getY();
            int z = block.getZ();

            if (wcfg.disableFireSpreadBlocks.contains(BukkitAdapter.asBlockType(world.getBlockAt(x, y - 1, z).getType()).getId())
                    || wcfg.disableFireSpreadBlocks.contains(BukkitAdapter.asBlockType(world.getBlockAt(x + 1, y, z).getType()).getId())
                    || wcfg.disableFireSpreadBlocks.contains(BukkitAdapter.asBlockType(world.getBlockAt(x - 1, y, z).getType()).getId())
                    || wcfg.disableFireSpreadBlocks.contains(BukkitAdapter.asBlockType(world.getBlockAt(x, y, z - 1).getType()).getId())
                    || wcfg.disableFireSpreadBlocks.contains(BukkitAdapter.asBlockType(world.getBlockAt(x, y, z + 1).getType()).getId())) {
                event.setCancelled(true);
                return;
            }
        }

        if (wcfg.useRegions) {
            ApplicableRegionSet set =
                    WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery().getApplicableRegions(BukkitAdapter.adapt(block.getLocation()));

            if (wcfg.highFreqFlags && isFireSpread
                    && !set.testState(null, Flags.FIRE_SPREAD)) {
                event.setCancelled(true);
                return;
            }

            if (wcfg.highFreqFlags && cause == IgniteCause.LAVA
                    && !set.testState(null, Flags.LAVA_FIRE)) {
                event.setCancelled(true);
                return;
            }

            if (cause == IgniteCause.FIREBALL && event.getPlayer() == null) {
                // wtf bukkit, FIREBALL is supposed to be reserved to players
                if (!set.testState(null, Flags.GHAST_FIREBALL)) {
                    event.setCancelled(true);
                    return;
                }
            }

            if (cause == IgniteCause.LIGHTNING && !set.testState(null, Flags.LIGHTNING)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    /*
     * Called when a block is destroyed from burning.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBurn(BlockBurnEvent event) {
        ConfigurationManager cfg = WorldGuard.getInstance().getPlatform().getGlobalStateManager();
        BukkitWorldConfiguration wcfg = (BukkitWorldConfiguration) getWorldConfig(event.getBlock().getWorld());

        if (cfg.activityHaltToggle) {
            event.setCancelled(true);
            return;
        }

        if (wcfg.disableFireSpread) {
            event.setCancelled(true);
            return;
        }

        if (wcfg.fireSpreadDisableToggle) {
            Block block = event.getBlock();
            event.setCancelled(true);
            checkAndDestroyFireAround(block.getWorld(), block.getX(), block.getY(), block.getZ());
            return;
        }

        if (!wcfg.disableFireSpreadBlocks.isEmpty()) {
            Block block = event.getBlock();

            if (wcfg.disableFireSpreadBlocks.contains(BukkitAdapter.asBlockType(block.getType()).getId())) {
                event.setCancelled(true);
                checkAndDestroyFireAround(block.getWorld(), block.getX(), block.getY(), block.getZ());
                return;
            }
        }

        if (wcfg.isChestProtected(BukkitAdapter.adapt(event.getBlock().getLocation()))) {
            event.setCancelled(true);
            return;
        }

        if (wcfg.useRegions) {
            Block block = event.getBlock();
            int x = block.getX();
            int y = block.getY();
            int z = block.getZ();
            ApplicableRegionSet set =
                    WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery().getApplicableRegions(BukkitAdapter.adapt(block.getLocation()));

            if (!set.testState(null, Flags.FIRE_SPREAD)) {
                checkAndDestroyFireAround(block.getWorld(), x, y, z);
                event.setCancelled(true);
            }

        }
    }

    private void checkAndDestroyFireAround(World world, int x, int y, int z) {
        checkAndDestroyFire(world, x, y, z + 1);
        checkAndDestroyFire(world, x, y, z - 1);
        checkAndDestroyFire(world, x, y + 1, z);
        checkAndDestroyFire(world, x, y - 1, z);
        checkAndDestroyFire(world, x + 1, y, z);
        checkAndDestroyFire(world, x - 1, y, z);
    }

    private void checkAndDestroyFire(World world, int x, int y, int z) {
        if (Materials.isFire(world.getBlockAt(x, y, z).getType())) {
            world.getBlockAt(x, y, z).setType(Material.AIR);
        }
    }

    /*
     * Called when block physics occurs.
     */
    @EventHandler(ignoreCancelled = true)
    public void onBlockPhysics(BlockPhysicsEvent event) {
        ConfigurationManager cfg = WorldGuard.getInstance().getPlatform().getGlobalStateManager();

        if (cfg.activityHaltToggle) {
            event.setCancelled(true);
            return;
        }

        BukkitWorldConfiguration wcfg = getWorldConfig(event.getBlock().getWorld());
        if (!wcfg.needsBlockListener) {
            return;
        }

        Material id = event.getBlock().getType();

        if (id == Material.GRAVEL && wcfg.noPhysicsGravel) {
            event.setCancelled(true);
            return;
        }

        if (id == Material.SAND && wcfg.noPhysicsSand) {
            event.setCancelled(true);
            return;
        }

        if (id == Material.NETHER_PORTAL && wcfg.allowPortalAnywhere) {
            event.setCancelled(true);
            return;
        }

        if (id == Material.LADDER && wcfg.ropeLadders) {
            if (event.getBlock().getRelative(0, 1, 0).getType() == Material.LADDER) {
                event.setCancelled(true);
                return;
            }
        }
    }

    /*
     * Called when a player places a block.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Block target = event.getBlock();
        World world = target.getWorld();

        WorldConfiguration wcfg = getWorldConfig(world);

        if (wcfg.simulateSponge && target.getType() == Material.SPONGE) {
            if (wcfg.redstoneSponges && target.isBlockIndirectlyPowered()) {
                return;
            }

            int ox = target.getX();
            int oy = target.getY();
            int oz = target.getZ();

            SpongeUtil.clearSpongeWater(BukkitAdapter.adapt(world), ox, oy, oz);
        }
    }

    /*
     * Called when redstone changes.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockRedstoneChange(BlockRedstoneEvent event) {
        Block blockTo = event.getBlock();
        World world = blockTo.getWorld();

        WorldConfiguration wcfg = getWorldConfig(world);

        if (wcfg.simulateSponge && wcfg.redstoneSponges) {
            int ox = blockTo.getX();
            int oy = blockTo.getY();
            int oz = blockTo.getZ();

            for (int cx = -1; cx <= 1; cx++) {
                for (int cy = -1; cy <= 1; cy++) {
                    for (int cz = -1; cz <= 1; cz++) {
                        Block sponge = world.getBlockAt(ox + cx, oy + cy, oz + cz);
                        if (sponge.getType() == Material.SPONGE
                                && sponge.isBlockIndirectlyPowered()) {
                            SpongeUtil.clearSpongeWater(BukkitAdapter.adapt(world), ox + cx, oy + cy, oz + cz);
                        } else if (sponge.getType() == Material.SPONGE
                                && !sponge.isBlockIndirectlyPowered()) {
                            SpongeUtil.addSpongeWater(BukkitAdapter.adapt(world), ox + cx, oy + cy, oz + cz);
                        }
                    }
                }
            }

            return;
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLeavesDecay(LeavesDecayEvent event) {
        ConfigurationManager cfg = WorldGuard.getInstance().getPlatform().getGlobalStateManager();
        WorldConfiguration wcfg = getWorldConfig(event.getBlock().getWorld());

        if (cfg.activityHaltToggle) {
            event.setCancelled(true);
            return;
        }

        if (wcfg.disableLeafDecay) {
            event.setCancelled(true);
            return;
        }

        if (wcfg.useRegions) {
            if (!StateFlag.test(WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery().queryState(BukkitAdapter.adapt(event.getBlock().getLocation()), (RegionAssociable) null, Flags.LEAF_DECAY))) {
                event.setCancelled(true);
            }
        }
    }

    /*
     * Called when a block is formed based on world conditions.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockForm(BlockFormEvent event) {
        ConfigurationManager cfg = WorldGuard.getInstance().getPlatform().getGlobalStateManager();
        WorldConfiguration wcfg = getWorldConfig(event.getBlock().getWorld());

        if (cfg.activityHaltToggle) {
            event.setCancelled(true);
            return;
        }

        Material type = event.getNewState().getType();

        if (event instanceof EntityBlockFormEvent) {
            if (((EntityBlockFormEvent) event).getEntity() instanceof Snowman) {
                if (wcfg.disableSnowmanTrails) {
                    event.setCancelled(true);
                    return;
                }
            }
            return;
        }

        if (type == Material.ICE) {
            if (wcfg.disableIceFormation) {
                event.setCancelled(true);
                return;
            }
            if (wcfg.useRegions && !StateFlag.test(WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery()
                    .queryState(BukkitAdapter.adapt(event.getBlock().getLocation()), (RegionAssociable) null, Flags.ICE_FORM))) {
                event.setCancelled(true);
                return;
            }
        }

        if (type == Material.SNOW) {
            if (wcfg.disableSnowFormation) {
                event.setCancelled(true);
                return;
            }
            if (!wcfg.allowedSnowFallOver.isEmpty()) {
                Material targetId = event.getBlock().getRelative(0, -1, 0).getType();

                if (!wcfg.allowedSnowFallOver.contains(BukkitAdapter.asBlockType(targetId).getId())) {
                    event.setCancelled(true);
                    return;
                }
            }
            if (wcfg.useRegions && !StateFlag.test(WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery()
                    .queryState(BukkitAdapter.adapt(event.getBlock().getLocation()), (RegionAssociable) null, Flags.SNOW_FALL))) {
                event.setCancelled(true);
                return;
            }
        }
    }

    /*
     * Called when a block spreads based on world conditions.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockSpread(BlockSpreadEvent event) {
        ConfigurationManager cfg = WorldGuard.getInstance().getPlatform().getGlobalStateManager();
        WorldConfiguration wcfg = getWorldConfig(event.getBlock().getWorld());

        if (cfg.activityHaltToggle) {
            event.setCancelled(true);
            return;
        }

        Material newType = event.getNewState().getType(); // craftbukkit randomly gives AIR as event.getSource even if that block is not air

        if (Materials.isMushroom(newType)) {
            if (wcfg.disableMushroomSpread) {
                event.setCancelled(true);
                return;
            }
            if (wcfg.useRegions && !StateFlag.test(WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery()
                    .queryState(BukkitAdapter.adapt(event.getBlock().getLocation()), (RegionAssociable) null, Flags.MUSHROOMS))) {
                event.setCancelled(true);
                return;
            }
        }

        if (newType == Material.GRASS_BLOCK) {
            if (wcfg.disableGrassGrowth) {
                event.setCancelled(true);
                return;
            }
            if (wcfg.useRegions && !StateFlag.test(WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery()
                    .queryState(BukkitAdapter.adapt(event.getBlock().getLocation()), (RegionAssociable) null, Flags.GRASS_SPREAD))) {
                event.setCancelled(true);
                return;
            }
        }

        if (newType == Material.MYCELIUM) {
            if (wcfg.disableMyceliumSpread) {
                event.setCancelled(true);
                return;
            }

            if (wcfg.useRegions && !StateFlag.test(WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery()
                    .queryState(BukkitAdapter.adapt(event.getBlock().getLocation()), (RegionAssociable) null, Flags.MYCELIUM_SPREAD))) {
                event.setCancelled(true);
                return;
            }
        }

        if (Materials.isVine(newType)) {
            if (wcfg.disableVineGrowth) {
                event.setCancelled(true);
                return;
            }

            if (wcfg.useRegions && !StateFlag.test(WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery()
                    .queryState(BukkitAdapter.adapt(event.getBlock().getLocation()), (RegionAssociable) null, Flags.VINE_GROWTH))) {
                event.setCancelled(true);
                return;
            }
        }

        handleGrow(event, event.getBlock().getLocation(), newType);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockGrow(BlockGrowEvent event) {
        Location loc = event.getBlock().getLocation();
        final Material type = event.getNewState().getType();

        handleGrow(event, loc, type);
    }

    private void handleGrow(Cancellable event, Location loc, Material type) {
        WorldConfiguration wcfg = getWorldConfig(loc.getWorld());
        if (Materials.isCrop(type)) {
            if (wcfg.disableCropGrowth) {
                event.setCancelled(false);
                return;
            }

            if (wcfg.useRegions && !StateFlag.test(WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery()
                .queryState(BukkitAdapter.adapt(loc), (RegionAssociable) null, Flags.CROP_GROWTH))) {
                event.setCancelled(true);
                return;
            }
        }
    }

    /*
     * Called when a block fades.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockFade(BlockFadeEvent event) {

        WorldConfiguration wcfg = getWorldConfig(event.getBlock().getWorld());

        if (event.getBlock().getType() == Material.ICE) {
            if (wcfg.disableIceMelting) {
                event.setCancelled(true);
                return;
            }

            if (wcfg.useRegions && !StateFlag.test(WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery()
                    .queryState(BukkitAdapter.adapt(event.getBlock().getLocation()), (RegionAssociable) null, Flags.ICE_MELT))) {
                event.setCancelled(true);
                return;
            }
        } else if (event.getBlock().getType() == Material.FROSTED_ICE) {
            if (wcfg.useRegions && !StateFlag.test(WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery()
                    .queryState(BukkitAdapter.adapt(event.getBlock().getLocation()), (RegionAssociable) null, Flags.FROSTED_ICE_MELT))) {
                event.setCancelled(true);
                return;
            }
        } else if (event.getBlock().getType() == Material.SNOW) {
            if (wcfg.disableSnowMelting) {
                event.setCancelled(true);
                return;
            }

            if (wcfg.useRegions && !StateFlag.test(WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery()
                    .queryState(BukkitAdapter.adapt(event.getBlock().getLocation()), (RegionAssociable) null, Flags.SNOW_MELT))) {
                event.setCancelled(true);
                return;
            }
        } else if (event.getBlock().getType() == Material.FARMLAND) {
            if (wcfg.disableSoilDehydration) {
                event.setCancelled(true);
                return;
            }
            if (wcfg.useRegions && !StateFlag.test(WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery()
                    .queryState(BukkitAdapter.adapt(event.getBlock().getLocation()), (RegionAssociable) null, Flags.SOIL_DRY))) {
                event.setCancelled(true);
                return;
            }
        } else if (Materials.isCoral(event.getBlock().getType())) {
            if (wcfg.disableCoralBlockFade) {
                event.setCancelled(true);
                return;
            }
            if (wcfg.useRegions && !StateFlag.test(WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery()
                    .queryState(BukkitAdapter.adapt(event.getBlock().getLocation()), (RegionAssociable) null, Flags.CORAL_FADE))) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        ConfigurationManager cfg = WorldGuard.getInstance().getPlatform().getGlobalStateManager();

        if (cfg.activityHaltToggle) {
            event.setCancelled(true);
            return;
        }

        WorldConfiguration wcfg = getWorldConfig(event.getBlock().getWorld());
        if (wcfg.blockOtherExplosions) {
            event.setCancelled(true);
        }
    }

}
