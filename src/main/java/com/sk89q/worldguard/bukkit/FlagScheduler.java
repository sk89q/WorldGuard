// $Id$
/*
 * WorldGuard
 * Copyright (C) 2010 sk89q <http://www.sk89q.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
*/

package com.sk89q.worldguard.bukkit;

import java.util.HashMap;
import java.util.Map;
import org.bukkit.entity.Player;
import com.sk89q.worldedit.Vector;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.flags.DefaultFlag;
import com.sk89q.worldguard.protection.managers.RegionManager;
import static com.sk89q.worldguard.bukkit.BukkitUtil.*;

/**
 * This processed periodical tasks for flags that require them, such as with
 * the healing flag.
 * 
 * @author sk89q
 */
public class FlagScheduler implements Runnable {
    
    public static final int RUN_DELAY = 20;
    
    private WorldGuardPlugin plugin;
    private Map<String, PlayerFlagState> states;
    
    /**
     * Construct the object.
     * 
     * @param plugin
     */
    public FlagScheduler(WorldGuardPlugin plugin) {
        this.plugin = plugin;
        
        states = new HashMap<String, PlayerFlagState>();
    }

    /**
     * Run the task.
     */
    public void run() {
        Player[] players = plugin.getServer().getOnlinePlayers();
        GlobalStateManager config = plugin.getGlobalStateManager();

        for (Player player : players) {
            PlayerFlagState state;
            
            synchronized (this) {
                state = states.get(player.getName());
                
                if (state == null) {
                    state = new PlayerFlagState();
                    states.put(player.getName(), state);
                }
            }
            
            WorldStateManager worldConfig = config.get(player.getWorld());

            if (worldConfig.useRegions) {
                Vector playerLocation = toVector(player.getLocation());
                RegionManager regionManager = plugin.getGlobalRegionManager()
                        .get(player.getWorld());
                ApplicableRegionSet applicable = regionManager
                        .getApplicableRegions(playerLocation);
                
                processHeal(applicable, player, state);
            }
        }
    }
    
    /**
     * Process healing for a player.
     * 
     * @param applicable
     * @param player
     * @param state
     */
    private void processHeal(ApplicableRegionSet applicable, Player player,
            PlayerFlagState state) {
        
        if (player.getHealth() >= 20) {
            return;
        }
        
        long now = System.currentTimeMillis();

        Integer healAmount = applicable.getFlag(DefaultFlag.HEAL_AMOUNT);
        Integer healDelay = applicable.getFlag(DefaultFlag.HEAL_DELAY);
        
        if (healAmount == null || healDelay == null || healAmount < 1 || healDelay < 0) {
            return;
        }
        
        if (healDelay <= 0) {
            player.setHealth(20);
            state.lastHeal = now;
        } else if (now - state.lastHeal > healDelay * 1000) {
            player.setHealth(Math.min(20, player.getHealth() + healAmount));
            state.lastHeal = now;
        }
    }
    
    /**
     * Forget a player.
     * 
     * @param player
     */
    public synchronized void forget(Player player) {
        states.remove(player.getName());
    }
    
    /**
     * Keeps state per player.
     */
    private static class PlayerFlagState {
        private long lastHeal;
    }

}
