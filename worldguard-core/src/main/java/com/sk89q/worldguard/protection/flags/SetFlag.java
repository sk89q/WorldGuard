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
import com.sk89q.worldguard.protection.regions.ProtectedRegion;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.Objects.requireNonNull;

/**
 * Stores a set of types.
 */
public class SetFlag<T> extends Flag<Set<T>> {

    private static final Pattern ANY_MODIFIER = Pattern.compile("^(add|sub|subtract|rem|remove) (.*)$");
    private static final Pattern REMOVE_MODIFIERS = Pattern.compile("^(sub|subtract|rem|remove) (.*)$");

    private Flag<T> subFlag;

    public SetFlag(String name, RegionGroup defaultGroup, Flag<T> subFlag) {
        super(name, defaultGroup);
        requireNonNull(subFlag, "SubFlag cannot be null.");
        this.subFlag = subFlag;
    }

    public SetFlag(String name, Flag<T> subFlag) {
        super(name);
        requireNonNull(subFlag, "SubFlag cannot be null.");
        this.subFlag = subFlag;
    }

    /**
     * Get the flag that is stored in this flag.
     *
     * @return the stored flag type
     */
    public Flag<T> getType() {
        return subFlag;
    }

    @Override
    public Set<T> parseInput(FlagContext context) throws InvalidFlagFormat {
        String input = context.getUserInput();
        if (input.isEmpty()) {
            return Sets.newHashSet();
        } else {
            Set<T> items = Sets.newHashSet();
            boolean subtractive = false;

            // If the input starts with particular keywords, attempt to load the existing values,
            // and make this a modification, instead of an overwrite.
            Matcher keywordMatcher = ANY_MODIFIER.matcher(input);
            if (keywordMatcher.matches()) {
                ProtectedRegion region = Objects.requireNonNull((ProtectedRegion) context.get("region"));

                Set<T> existingValue = region.getFlag(this);
                if (existingValue != null) {
                    items.addAll(existingValue);
                }

                subtractive = REMOVE_MODIFIERS.matcher(input).matches();
                input = keywordMatcher.group(2);
            }

            for (String str : input.split(",")) {
                FlagContext copy = context.copyWith(null, str, null);

                T subFlagValue = subFlag.parseInput(copy);
                if (subtractive) {
                    items.remove(subFlagValue);
                } else {
                    items.add(subFlagValue);
                }
            }

            return items;
        }
    }

    @Override
    public Set<T> unmarshal(Object o) {
        if (o instanceof Collection<?>) {
            Collection<?> collection = (Collection<?>) o;
            Set<T> items = new HashSet<>();

            for (Object sub : collection) {
                T item = subFlag.unmarshal(sub);
                if (item != null) {
                    items.add(item);
                }
            }

            return items;
        } else {
            return null;
        }
    }

    @Override
    public Object marshal(Set<T> o) {
        List<Object> list = new ArrayList<>();
        for (T item : o) {
            list.add(subFlag.marshal(item));
        }

        return list;
    }
    
}
