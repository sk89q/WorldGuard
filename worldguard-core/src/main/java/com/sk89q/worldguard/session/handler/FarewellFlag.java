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

package com.sk89q.worldguard.session.handler;

import com.google.common.collect.Sets;
import com.sk89q.worldedit.util.Location;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.session.MoveType;
import com.sk89q.worldguard.session.Session;
import com.sk89q.worldguard.util.MessagingUtil;

import java.util.Collections;
import java.util.Set;
import java.util.function.BiConsumer;

public class FarewellFlag extends Handler {

    public static final Factory FACTORY = new Factory();
    public static class Factory extends Handler.Factory<FarewellFlag> {
        @Override
        public FarewellFlag create(Session session) {
            return new FarewellFlag(session);
        }
    }

    private Set<String> lastMessageStack = Collections.emptySet();
    private Set<String> lastTitleStack = Collections.emptySet();

    public FarewellFlag(Session session) {
        super(session);
    }

    private Set<String> getMessages(LocalPlayer player, ApplicableRegionSet set, Flag<String> flag) {
        return Sets.newLinkedHashSet(set.queryAllValues(player, flag));
    }

    @Override
    public void initialize(LocalPlayer player, Location current, ApplicableRegionSet set) {
        lastMessageStack = getMessages(player, set, Flags.FAREWELL_MESSAGE);
        lastTitleStack = getMessages(player, set, Flags.FAREWELL_TITLE);
    }

    @Override
    public boolean onCrossBoundary(LocalPlayer player, Location from, Location to, ApplicableRegionSet toSet,
                                   Set<ProtectedRegion> entered, Set<ProtectedRegion> exited, MoveType moveType) {

        lastMessageStack = collectAndSend(player, toSet, Flags.FAREWELL_MESSAGE, lastMessageStack, MessagingUtil::sendStringToChat);
        lastTitleStack = collectAndSend(player, toSet, Flags.FAREWELL_TITLE, lastTitleStack, MessagingUtil::sendStringToTitle);

        return true;
    }

    private Set<String> collectAndSend(LocalPlayer player, ApplicableRegionSet toSet, Flag<String> flag,
                                       Set<String> stack, BiConsumer<LocalPlayer, String> msgFunc) {
        Set<String> messages = getMessages(player, toSet, flag);

        if (!messages.isEmpty()) {
            // Due to flag priorities, we have to collect the lower
            // priority flag values separately
            for (ProtectedRegion region : toSet) {
                String message = region.getFlag(flag); // parents? FlagValueCalculator.getEffectiveFlag(region, flag, player)?
                if (message != null) {
                    messages.add(message);
                }
            }
        }

        for (String message : stack) {
            if (!messages.contains(message)) {
                msgFunc.accept(player, message);
                break;
            }
        }
        return messages;
    }
}
