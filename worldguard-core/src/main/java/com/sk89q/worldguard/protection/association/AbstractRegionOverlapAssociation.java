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

package com.sk89q.worldguard.protection.association;

import static com.google.common.base.Preconditions.checkNotNull;

import com.sk89q.worldguard.domains.Association;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public abstract class AbstractRegionOverlapAssociation implements RegionAssociable {

    @Nullable
    protected Set<ProtectedRegion> source;
    private boolean useMaxPriorityAssociation;
    private int maxPriority;
    private Set<ProtectedRegion> maxPriorityRegions;

    protected AbstractRegionOverlapAssociation(@Nullable Set<ProtectedRegion> source, boolean useMaxPriorityAssociation) {
        this.source = source;
        this.useMaxPriorityAssociation = useMaxPriorityAssociation;
    }

    protected void calcMaxPriority() {
        checkNotNull(source);
        int best = 0;
        Set<ProtectedRegion> bestRegions = new HashSet<>();
        for (ProtectedRegion region : source) {
            int priority = region.getPriority();
            if (priority > best) {
                best = priority;
                bestRegions.clear();
                bestRegions.add(region);
            } else if (priority == best) {
                bestRegions.add(region);
            }
        }
        this.maxPriority = best;
        this.maxPriorityRegions = bestRegions;
    }

    private boolean checkNonplayerProtectionDomains(Iterable<? extends ProtectedRegion> source, Collection<?> domains) {
        if (source == null || domains == null || domains.isEmpty()) {
            return false;
        }

        for (ProtectedRegion region : source) {
            Set<String> regionDomains = region.getFlag(Flags.NONPLAYER_PROTECTION_DOMAINS);

            if (regionDomains == null || regionDomains.isEmpty()) {
                continue;
            }

            if (!Collections.disjoint(regionDomains, domains)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public Association getAssociation(List<ProtectedRegion> regions) {
        checkNotNull(source);
        for (ProtectedRegion region : regions) {
            while (region != null) {
                if ((region.getId().equals(ProtectedRegion.GLOBAL_REGION) && source.isEmpty())) {
                    return Association.OWNER;
                }

                if (source.contains(region)) {
                    if (useMaxPriorityAssociation) {
                        int priority = region.getPriority();
                        if (priority == maxPriority) {
                            return Association.OWNER;
                        }
                    } else {
                        return Association.OWNER;
                    }
                }

                Set<ProtectedRegion> source;

                if (useMaxPriorityAssociation) {
                    source = maxPriorityRegions;
                } else {
                    source = this.source;
                }

                if (checkNonplayerProtectionDomains(source, region.getFlag(Flags.NONPLAYER_PROTECTION_DOMAINS))) {
                    return Association.OWNER;
                }

                region = region.getParent();
            }
        }

        return Association.NON_MEMBER;
    }
}
