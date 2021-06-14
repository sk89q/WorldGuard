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

package com.sk89q.worldguard.protection;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.sk89q.worldguard.domains.Association;
import com.sk89q.worldguard.protection.association.RegionAssociable;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.MapFlag;
import com.sk89q.worldguard.protection.flags.RegionGroup;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.flags.StateFlag.State;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.util.NormativeOrders;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Calculates the value of a flag given a list of regions and an optional
 * global region.
 *
 * <p>Since there may be multiple overlapping regions, regions with
 * differing priorities, regions with inheritance, flags with region groups
 * assigned to them, and much more, the task of calculating the "effective"
 * value of a flag is far from trivial. This class abstracts away the
 * difficult with a number of methods for performing these calculations.</p>
 */
public class FlagValueCalculator {

    @Nullable
    private final ProtectedRegion globalRegion;
    private final Iterable<ProtectedRegion> applicable;

    /**
     * Create a new instance.
     *
     * @param regions a list of applicable regions that must be sorted according to {@link NormativeOrders}
     * @param globalRegion an optional global region (null to not use one)
     */
    public FlagValueCalculator(List<ProtectedRegion> regions, @Nullable ProtectedRegion globalRegion) {
        checkNotNull(regions);

        this.globalRegion = globalRegion;

        applicable = globalRegion == null ? regions
                : Iterables.concat(regions, Collections.singletonList(globalRegion));
    }

    /**
     * Returns an iterable of regions sorted by priority (descending), with
     * the global region tacked on at the end if one exists.
     *
     * @return an iterable
     */
    private Iterable<ProtectedRegion> getApplicable() {
        return applicable;
    }

    /**
     * Return the membership status of the given subject, indicating
     * whether there are no (counted) regions in the list of regions,
     * whether the subject is a member of all (counted) regions, or
     * whether the subject is not a member of all (counted) regions.
     *
     * <p>A region is "counted" if it doesn't have the
     * {@link Flags#PASSTHROUGH} flag set to {@code ALLOW} and if
     * there isn't another "counted" region with a higher priority.
     * (The explicit purpose of the PASSTHROUGH flag is to have the
     * region be skipped over in this check.)</p>
     *
     * <p>This method is mostly for internal use. It's not particularly
     * useful.</p>
     *
     * @param subject the subject
     * @return the membership result
     */
    public Result getMembership(RegionAssociable subject) {
        checkNotNull(subject);

        int minimumPriority = Integer.MIN_VALUE;
        Result result = Result.NO_REGIONS;

        Set<ProtectedRegion> ignoredRegions = Sets.newHashSet();

        for (ProtectedRegion region : getApplicable()) {
            int priority = getPriority(region);

            // Don't consider lower priorities below minimumPriority
            // (which starts at Integer.MIN_VALUE). A region that "counts"
            // (has the flag set OR has members) will raise minimumPriority
            // to its own priority.
            if (priority < minimumPriority) {
                break;
            }

            // If PASSTHROUGH is set, ignore this region
            if (getEffectiveFlag(region, Flags.PASSTHROUGH, subject) == State.ALLOW) {
                continue;
            }

            if (ignoredRegions.contains(region)) {
                continue;
            }

            minimumPriority = priority;

            boolean member = RegionGroup.MEMBERS.contains(subject.getAssociation(Collections.singletonList(region)));

            if (member) {
                result = Result.SUCCESS;
                addParents(ignoredRegions, region);
            } else {
                return Result.FAIL;
            }
        }

        return result;
    }


    /**
     * Get the effective value for a list of state flags. The rules of
     * states is observed here; that is, {@code DENY} overrides {@code ALLOW},
     * and {@code ALLOW} overrides {@code NONE}.
     *
     * <p>A subject can be provided that is used to determine whether the value
     * of a flag on a particular region should be used. For example, if a
     * flag's region group is set to {@link RegionGroup#MEMBERS} and the given
     * subject is not a member, then the region would be skipped when
     * querying that flag. If {@code null} is provided for the subject, then
     * only flags that use {@link RegionGroup#ALL},
     * {@link RegionGroup#NON_MEMBERS}, etc. will apply.</p>
     *
     * @param subject an optional subject, which would be used to determine the region group to apply
     * @param flags a list of flags to check
     * @return a state
     */
    @Nullable
    public State queryState(@Nullable RegionAssociable subject, StateFlag... flags) {
        State value = null;

        for (StateFlag flag : flags) {
            value = StateFlag.combine(value, queryValue(subject, flag));
            if (value == State.DENY) {
                break;
            }
        }

        return value;
    }

    /**
     * Get the effective value for a list of state flags. The rules of
     * states is observed here; that is, {@code DENY} overrides {@code ALLOW},
     * and {@code ALLOW} overrides {@code NONE}.
     *
     * <p>This method is the same as
     * {@link #queryState(RegionAssociable, StateFlag...)}.</p>
     *
     * @param subject an optional subject, which would be used to determine the region group to apply
     * @param flag a flag to check
     * @return a state
     */
    @Nullable
    public State queryState(@Nullable RegionAssociable subject, StateFlag flag) {
        return queryValue(subject, flag);
    }

    /**
     * Get the effective value for a flag. If there are multiple values
     * (for example, if there are multiple regions with the same priority
     * but with different farewell messages set, there would be multiple
     * completing values), then the selected (or "winning") value will depend
     * on the flag type.
     *
     * <p>Only some flag types actually have a strategy for picking the
     * "best value." For most types, the actual value that is chosen to be
     * returned is undefined (it could be any value). As of writing, the only
     * type of flag that can consistently return the same 'best' value is
     * {@link StateFlag}.</p>
     *
     * <p>A subject can be provided that is used to determine whether the value
     * of a flag on a particular region should be used. For example, if a
     * flag's region group is set to {@link RegionGroup#MEMBERS} and the given
     * subject is not a member, then the region would be skipped when
     * querying that flag. If {@code null} is provided for the subject, then
     * only flags that use {@link RegionGroup#ALL},
     * {@link RegionGroup#NON_MEMBERS}, etc. will apply.</p>
     *
     * @param subject an optional subject, which would be used to determine the region group to apply
     * @param flag the flag
     * @return a value, which could be {@code null}
     */
    @Nullable
    public <V> V queryValue(@Nullable RegionAssociable subject, Flag<V> flag) {
        Collection<V> values = queryAllValues(subject, flag, true);
        return flag.chooseValue(values);
    }

    /**
     * Get the effective value for a key in a {@link MapFlag}. If there are multiple values
     * (for example, if there are multiple regions with the same priority
     * but with different farewell messages set, there would be multiple
     * completing values), then the selected (or "winning") value will be undefined.
     *
     * <p>A subject can be provided that is used to determine whether the value
     * of a flag on a particular region should be used. For example, if a
     * flag's region group is set to {@link RegionGroup#MEMBERS} and the given
     * subject is not a member, then the region would be skipped when
     * querying that flag. If {@code null} is provided for the subject, then
     * only flags that use {@link RegionGroup#ALL},
     * {@link RegionGroup#NON_MEMBERS}, etc. will apply.</p>
     *
     * @param subject an optional subject, which would be used to determine the region group to apply
     * @param flag the flag of type {@link MapFlag}
     * @param key the key for the map flag
     * @return a value, which could be {@code null}
     */
    @Nullable
    public <V, K> V queryMapValue(@Nullable RegionAssociable subject, MapFlag<K, V> flag, K key, Flag<V> fallback) {
        checkNotNull(flag);
        checkNotNull(key);

        Map<ProtectedRegion, V> consideredValues = new HashMap<>();
        Map<ProtectedRegion, V> fallbackValues = new HashMap<>();
        int minimumPriority = Integer.MIN_VALUE;
        Set<ProtectedRegion> ignoredParents = new HashSet<>();

        for(ProtectedRegion region : getApplicable()) {
            int priority = getPriority(region);

            if (priority < minimumPriority) {
                break;
            }

            if (ignoredParents.contains(region)) {
                continue;
            }

            V effectiveValue = getEffectiveMapValue(region, flag, key, subject);

            if (effectiveValue != null) {
                minimumPriority = priority;
                consideredValues.put(region, effectiveValue);
            } else if (fallback != null) {
                effectiveValue = getEffectiveFlag(region, fallback, subject);
                if (effectiveValue != null) {
                    minimumPriority = priority;
                    fallbackValues.put(region, effectiveValue);
                }
            }

            addParents(ignoredParents, region);
        }


        if (consideredValues.isEmpty()) {
            if (fallback != null && !fallbackValues.isEmpty()) {
                return fallback.chooseValue(fallbackValues.values());
            }
            V defaultValue = flag.getValueFlag().getDefault();
            return defaultValue != null ? defaultValue : fallback != null ? fallback.getDefault() : null;
        }

        return flag.getValueFlag().chooseValue(consideredValues.values());
    }

    @Nullable
    public static <V, K> V getEffectiveMapValue(ProtectedRegion region, MapFlag<K, V> mapFlag, K key, RegionAssociable subject) {
        List<ProtectedRegion> seen = new ArrayList<>();
        ProtectedRegion current = region;

        while (current != null) {
            seen.add(current);

            Map<K, V> mapValue = current.getFlag(mapFlag);

            if (mapValue != null && mapValue.containsKey(key)) {
                boolean use = true;

                if (mapFlag.getRegionGroupFlag() != null) {
                    RegionGroup group = current.getFlag(mapFlag.getRegionGroupFlag());
                    if (group == null) {
                        group = mapFlag.getRegionGroupFlag().getDefault();
                    }

                    if (group == null) {
                        use = false;
                    } else if (subject == null) {
                        use = group.contains(Association.NON_MEMBER);
                    } else if (!group.contains(subject.getAssociation(seen))) {
                        use = false;
                    }
                }

                if (use) {
                    return mapValue.get(key);
                }
            }

            current = current.getParent();
        }
        return null;
    }

    /**
     * Get the effective values for a flag, returning a collection of all
     * values. It is up to the caller to determine which value, if any,
     * from the collection will be used.
     *
     * <p>A subject can be provided that is used to determine whether the value
     * of a flag on a particular region should be used. For example, if a
     * flag's region group is set to {@link RegionGroup#MEMBERS} and the given
     * subject is not a member, then the region would be skipped when
     * querying that flag. If {@code null} is provided for the subject, then
     * only flags that use {@link RegionGroup#ALL},
     * {@link RegionGroup#NON_MEMBERS}, etc. will apply.</p>
     *
     * @param subject an optional subject, which would be used to determine the region group to apply
     * @param flag the flag
     * @return a collection of values
     */
    public <V> Collection<V> queryAllValues(@Nullable RegionAssociable subject, Flag<V> flag) {
        return queryAllValues(subject, flag, false);
    }

    /**
     * Get the effective values for a flag, returning a collection of all
     * values. It is up to the caller to determine which value, if any,
     * from the collection will be used.
     *
     * <p>A subject can be provided that is used to determine whether the value
     * of a flag on a particular region should be used. For example, if a
     * flag's region group is set to {@link RegionGroup#MEMBERS} and the given
     * subject is not a member, then the region would be skipped when
     * querying that flag. If {@code null} is provided for the subject, then
     * only flags that use {@link RegionGroup#ALL},
     * {@link RegionGroup#NON_MEMBERS}, etc. will apply.</p>
     *
     * @param subject an optional subject, which would be used to determine the region group to apply
     * @param flag the flag
     * @param acceptOne if possible, return only one value if it doesn't matter
     * @return a collection of values
     */
    @SuppressWarnings("unchecked")
    private <V> Collection<V> queryAllValues(@Nullable RegionAssociable subject, Flag<V> flag, boolean acceptOne) {
        checkNotNull(flag);

        // Can't use this optimization with flags that have a conflict resolution strategy
        if (acceptOne && flag.hasConflictStrategy()) {
            acceptOne = false;
        }

        // Check to see whether we have a subject if this is BUILD
        if (flag.requiresSubject() && subject == null) {
            throw new NullPointerException("The " + flag.getName() + " flag is handled in a special fashion and requires a non-null subject parameter");
        }

        int minimumPriority = Integer.MIN_VALUE;

        Map<ProtectedRegion, V> consideredValues = new HashMap<>();
        Set<ProtectedRegion> ignoredParents = new HashSet<>();

        for (ProtectedRegion region : getApplicable()) {
            int priority = getPriority(region);

            if (priority < minimumPriority) {
                break;
            }

            if (ignoredParents.contains(region)) {
                continue;
            }

            V value = getEffectiveFlag(region, flag, subject);

            if (value != null) {
                minimumPriority = priority;

                if (acceptOne) {
                    return Arrays.asList(value);
                } else {
                    consideredValues.put(region, value);
                }
            }

            addParents(ignoredParents, region);

            // The BUILD flag is implicitly set on every region where
            // PASSTHROUGH is not set to ALLOW
            if (priority != minimumPriority && flag.implicitlySetWithMembership()
                    && getEffectiveFlag(region, Flags.PASSTHROUGH, subject) != State.ALLOW) {
                minimumPriority = priority;
            }
        }

        if (flag.usesMembershipAsDefault() && consideredValues.isEmpty()) {
            switch (getMembership(subject)) {
                case FAIL:
                    return ImmutableList.of();
                case SUCCESS:
                    return (Collection<V>) ImmutableList.of(State.ALLOW);
            }
        }

        if (consideredValues.isEmpty()) {
            V fallback = flag.getDefault();
            return fallback != null ? ImmutableList.of(fallback) : (Collection<V>) ImmutableList.of();
        }

        return consideredValues.values();
    }

    /**
     * Get the effective priority of a region, overriding a region's priority
     * when appropriate (i.e. with the global region).
     *
     * @param region the region
     * @return the priority
     */
    public static int getPriority(final ProtectedRegion region) {
        if (region.getId().equals(ProtectedRegion.GLOBAL_REGION)) {
            return Integer.MIN_VALUE;
        } else {
            return region.getPriority();
        }
    }

    /**
     * Get a region's state flag, checking parent regions until a value for the
     * flag can be found (if one even exists).
     *
     * @param region the region
     * @param flag the flag
     * @param subject an subject object
     * @return the value
     */
    @SuppressWarnings("unchecked")
    public static <V> V getEffectiveFlag(final ProtectedRegion region, Flag<V> flag, @Nullable RegionAssociable subject) {
        if (region.getId().equals(ProtectedRegion.GLOBAL_REGION)) {
            if (flag == Flags.PASSTHROUGH) {
                // Has members/owners -> the global region acts like
                // a regular region without PASSTHROUGH
                State passthrough = region.getFlag(Flags.PASSTHROUGH);
                if (passthrough == State.DENY || passthrough != State.ALLOW && region.hasMembersOrOwners()) {
                    return null;
                } else {
                    return (V) State.ALLOW;
                }

            } else if (flag instanceof StateFlag && ((StateFlag) flag).preventsAllowOnGlobal()) {
                // Legacy behavior -> we can't let people change BUILD on
                // the global region
                State value = region.getFlag((StateFlag) flag);
                return value != State.ALLOW ? (V) value : null;
            }
        }

        ProtectedRegion current = region;

        List<ProtectedRegion> seen = new ArrayList<>();

        while (current != null) {
            seen.add(current);

            V value = current.getFlag(flag);

            if (value != null) {
                boolean use = true;

                if (flag.getRegionGroupFlag() != null) {
                    RegionGroup group = current.getFlag(flag.getRegionGroupFlag());
                    if (group == null) {
                        group = flag.getRegionGroupFlag().getDefault();
                    }

                    if (group == null) {
                        use = false;
                    } else if (subject == null) {
                        use = group.contains(Association.NON_MEMBER);
                    } else if (!group.contains(subject.getAssociation(seen))) {
                        use = false;
                    }
                }

                if (use) {
                    return value;
                }
            }

            current = current.getParent();
        }

        return null;
    }

    /**
     * Clear a region's parents for getFlag().
     *
     * @param ignored The regions to ignore
     * @param region The region to start from
     */
    private void addParents(Set<ProtectedRegion> ignored, ProtectedRegion region) {
        ProtectedRegion parent = region.getParent();

        while (parent != null) {
            ignored.add(parent);
            parent = parent.getParent();
        }
    }

    /**
     * Describes the membership result from
     * {@link #getMembership(RegionAssociable)}.
     */
    public static enum Result {
        /**
         * Indicates that there are no regions or the only regions are
         * ones with {@link Flags#PASSTHROUGH} enabled.
         */
        NO_REGIONS,

        /**
         * Indicates that the player is not a member of all overlapping
         * regions.
         */
        FAIL,

        /**
         * Indicates that the player is a member of all overlapping
         * regions.
         */
        SUCCESS
    }

}
