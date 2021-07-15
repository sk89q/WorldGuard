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

package com.sk89q.worldguard.commands.task;

import static com.google.common.base.Preconditions.checkNotNull;

import com.sk89q.minecraft.util.commands.CommandException;
import com.sk89q.worldguard.util.profile.Profile;
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldedit.util.formatting.component.PaginationBox;
import com.sk89q.worldedit.util.formatting.text.Component;
import com.sk89q.worldedit.util.formatting.text.TextComponent;
import com.sk89q.worldedit.util.formatting.text.event.ClickEvent;
import com.sk89q.worldedit.util.formatting.text.event.HoverEvent;
import com.sk89q.worldedit.util.formatting.text.format.TextColor;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.domains.DefaultDomain;
import com.sk89q.worldguard.internal.permission.RegionPermissionModel;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class RegionLister implements Callable<Integer> {

    private static final Logger log = Logger.getLogger(RegionLister.class.getCanonicalName());

    private final Actor sender;
    private final RegionManager manager;
    private final String world;
    private OwnerMatcher ownerMatcher;
    private String idFilter;
    private Pattern regexPattern;
    private int page;
    private String playerName;
    private boolean nameOnly;

    public RegionLister(RegionManager manager, Actor sender, String world) {
        checkNotNull(manager);
        checkNotNull(sender);
        checkNotNull(world);

        this.manager = manager;
        this.sender = sender;
        this.world = world;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public void filterOwnedByName(String name, boolean nameOnly) {
        this.playerName = name;
        this.nameOnly = nameOnly;
        if (nameOnly) {
            filterOwnedByName(name);
        } else {
            filterOwnedByProfile(name);
        }
    }

    private void filterOwnedByName(final String name) {
        ownerMatcher = new OwnerMatcher() {
            @Override
            public String getName() {
                return name;
            }

            @Override
            public boolean isContainedWithin(DefaultDomain domain) {
                return domain.contains(name);
            }
        };
    }

    private void filterOwnedByProfile(final String name) {
        ownerMatcher = new OwnerMatcher() {
            private UUID uniqueId;

            @Override
            public String getName() {
                return name;
            }

            @Override
            public boolean isContainedWithin(DefaultDomain domain) throws CommandException {
                if (domain.contains(name)) {
                    return true;
                }

                if (uniqueId == null) {
                    Profile profile;

                    try {
                        profile = WorldGuard.getInstance().getProfileService().findByName(name);
                    } catch (IOException e) {
                        log.log(Level.WARNING, "Failed UUID lookup of '" + name + "'", e);
                        throw new CommandException("Failed to lookup the UUID of '" + name + "'");
                    } catch (InterruptedException e) {
                        log.log(Level.WARNING, "Failed UUID lookup of '" + name + "'", e);
                        throw new CommandException("The lookup the UUID of '" + name + "' was interrupted");
                    }

                    if (profile == null) {
                        throw new CommandException("A user by the name of '" + name + "' does not seem to exist.");
                    }

                    uniqueId = profile.getUniqueId();
                }

                return domain.contains(uniqueId);
            }
        };
    }

    public void filterIdByMatch(String idFilter) {
        this.idFilter = idFilter;
    }

    public void filterIdByRegex(Pattern pattern) {
        this.regexPattern = pattern;
    }

    @Override
    public Integer call() throws Exception {
        Map<String, ProtectedRegion> regions = manager.getRegions();

        // Build a list of regions to show
        List<RegionListEntry> entries = new ArrayList<>();

        for (Map.Entry<String, ProtectedRegion> rg : regions.entrySet()) {
            if (rg.getKey().equals("__global__")) {
                continue;
            }
            final ProtectedRegion region = rg.getValue();
            final RegionListEntry entry = new RegionListEntry(region);

            if (entry.matches(idFilter) && entry.matches(ownerMatcher)) {
                entries.add(entry);
            }
        }

        if (ownerMatcher == null) {
            Collections.sort(entries);
        }
        // insert global on top
        if (regions.containsKey("__global__")) {
            final RegionListEntry entry = new RegionListEntry(regions.get("__global__"));
            if (entry.matches(ownerMatcher)) {
                entries.add(0, entry);
            }
        }
        // unless we're matching owners, then sort by ownership
        if (ownerMatcher != null) {
            Collections.sort(entries);
        }

        RegionPermissionModel perms = sender.isPlayer() ? new RegionPermissionModel(sender) : null;
        String title = ownerMatcher == null ? "Regions" : "Regions for " + ownerMatcher.getName();
        String cmd = "/rg list -w \"" + world + "\""
                + (playerName != null ? " -p " + playerName : "")
                + (nameOnly ? " -n" : "")
                + " %page%";
        PaginationBox box = new RegionListBox(title, cmd, perms, entries, world);
        sender.print(box.create(page));

        return page;
    }

    private interface OwnerMatcher {
        String getName();

        boolean isContainedWithin(DefaultDomain domain) throws CommandException;
    }

    private static final class RegionListEntry implements Comparable<RegionListEntry> {
        private final ProtectedRegion region;
        private boolean isOwner;
        private boolean isMember;

        private RegionListEntry(ProtectedRegion rg) {
            this.region = rg;
        }

        public boolean matches(OwnerMatcher matcher) throws CommandException {
            return matcher == null
                    || (isOwner = matcher.isContainedWithin(region.getOwners()))
                    || (isMember = matcher.isContainedWithin(region.getMembers()));
        }

        public boolean matches(String idMatcher) {
            return idMatcher == null || region.getId().contains(idMatcher);
        }

        public boolean matches(Pattern pattern) {
            return pattern == null || pattern.matcher(region.getId()).matches();
        }

        public ProtectedRegion getRegion() {
            return region;
        }

        public boolean isOwner() {
            return isOwner;
        }

        public boolean isMember() {
            return isMember;
        }

        @Override
        public int compareTo(RegionListEntry o) {
            if (isOwner != o.isOwner) {
                return isOwner ? -1 : 1;
            }
            if (isMember != o.isMember) {
                return isMember ? -1 : 1;
            }
            return region.getId().compareTo(o.region.getId());
        }
    }

    private static class RegionListBox extends PaginationBox {
        private final RegionPermissionModel perms;
        private final List<RegionListEntry> entries;
        private String world;

        RegionListBox(String title, String cmd, RegionPermissionModel perms, List<RegionListEntry> entries, String world) {
            super(title, cmd);
            this.perms = perms;
            this.entries = entries;
            this.world = world;
        }

        @Override
        public Component getComponent(int number) {
            final RegionListEntry entry = entries.get(number);
            final TextComponent.Builder builder = TextComponent.builder(number + 1 + ".").color(TextColor.LIGHT_PURPLE);
            if (entry.isOwner()) {
                builder.append(TextComponent.space()).append(TextComponent.of("+", TextColor.DARK_AQUA)
                        .hoverEvent(HoverEvent.of(HoverEvent.Action.SHOW_TEXT, TextComponent.of("Region Owner", TextColor.GOLD))));
            } else if (entry.isMember()) {
                builder.append(TextComponent.space()).append(TextComponent.of("-", TextColor.AQUA)
                        .hoverEvent(HoverEvent.of(HoverEvent.Action.SHOW_TEXT, TextComponent.of("Region Member", TextColor.GOLD))));
            }
            builder.append(TextComponent.space()).append(TextComponent.of(entry.getRegion().getId(), TextColor.GOLD));
            if (perms != null && perms.mayLookup(entry.region)) {
                builder.append(TextComponent.space().append(TextComponent.of("[Info]", TextColor.GRAY)
                        .hoverEvent(HoverEvent.of(HoverEvent.Action.SHOW_TEXT, TextComponent.of("Click for info")))
                        .clickEvent(ClickEvent.of(ClickEvent.Action.RUN_COMMAND,
                                "/rg info -w \"" + world + "\" " + entry.region.getId()))));
            }
            if (perms != null && entry.region.getFlag(Flags.TELE_LOC) != null && perms.mayTeleportTo(entry.region)) {
                builder.append(TextComponent.space().append(TextComponent.of("[TP]", TextColor.GRAY)
                        .hoverEvent(HoverEvent.of(HoverEvent.Action.SHOW_TEXT, TextComponent.of("Click to teleport")))
                        .clickEvent(ClickEvent.of(ClickEvent.Action.RUN_COMMAND,
                                "/rg tp -w \"" + world + "\" " + entry.region.getId()))));
            } else if (perms != null && perms.mayTeleportToCenter(entry.getRegion()) && entry.getRegion().isPhysicalArea()) {
                builder.append(TextComponent.space().append(TextComponent.of("[TP-Center]", TextColor.GRAY)
                        .hoverEvent(HoverEvent.of(HoverEvent.Action.SHOW_TEXT,
                                TextComponent.of("Click to teleport to the center")))
                        .clickEvent(ClickEvent.of(ClickEvent.Action.RUN_COMMAND,
                                "/rg tp -c -w \"" + world + "\" " + entry.region.getId()))));
            }
            return builder.build();
        }

        @Override
        public int getComponentsSize() {
            return entries.size();
        }
    }
}
