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

import com.sk89q.worldedit.math.BlockVector2;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.TestPlayer;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.domains.DefaultDomain;
import com.sk89q.worldguard.protection.association.RegionOverlapAssociation;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.flags.registry.FlagRegistry;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.GlobalProtectedRegion;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import com.sk89q.worldguard.protection.regions.ProtectedPolygonalRegion;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class RegionOverlapTest {
    static String COURTYARD_ID = "courtyard";
    static String FOUNTAIN_ID = "fountain";
    static String NO_FIRE_ID = "nofire";
    static String MEMBER_GROUP = "member";
    static String COURTYARD_GROUP = "courtyard";

    BlockVector3 inFountain = BlockVector3.at(2, 2, 2);
    BlockVector3 inCourtyard = BlockVector3.at(7, 7, 7);
    BlockVector3 outside = BlockVector3.at(15, 15, 15);
    BlockVector3 inNoFire = BlockVector3.at(150, 150, 150);
    RegionManager manager;
    ProtectedRegion globalRegion;
    ProtectedRegion courtyard;
    ProtectedRegion fountain;
    TestPlayer player1;
    TestPlayer player2;

   /* @Parameterized.Parameters(name = "{index}: useMaxPrio = {0}")
    public static Iterable<Object[]> params() {
        return Arrays.asList(new Object[][]{{true}, {false}});
    }*/


    // public boolean useMaxPriorityAssociation;

    protected FlagRegistry getFlagRegistry() {
        return WorldGuard.getInstance().getFlagRegistry();
    }
    
    protected abstract RegionManager createRegionManager() throws Exception;

    @BeforeAll
    public void setUp() throws Exception {
        setUpGlobalRegion();
        
        manager = createRegionManager();

        setUpPlayers();
        setUpCourtyardRegion();
        setUpFountainRegion();
        setUpNoFireRegion();
    }

    void setUpPlayers() {
        player1 = new TestPlayer("tetsu");
        player1.addGroup(MEMBER_GROUP);
        player1.addGroup(COURTYARD_GROUP);

        player2 = new TestPlayer("alex");
        player2.addGroup(MEMBER_GROUP);
    }

    void setUpGlobalRegion() {
        globalRegion = new GlobalProtectedRegion("__global__");
    }

    void setUpCourtyardRegion() {
        DefaultDomain domain = new DefaultDomain();
        domain.addGroup(COURTYARD_GROUP);

        ArrayList<BlockVector2> points = new ArrayList<>();
        points.add(BlockVector2.ZERO);
        points.add(BlockVector2.at(10, 0));
        points.add(BlockVector2.at(10, 10));
        points.add(BlockVector2.at(0, 10));

        //ProtectedRegion region = new ProtectedCuboidRegion(COURTYARD_ID, new BlockVector(0, 0, 0), new BlockVector(10, 10, 10));
        ProtectedRegion region = new ProtectedPolygonalRegion(COURTYARD_ID, points, 0, 10);

        region.setOwners(domain);
        region.setPriority(5);
        manager.addRegion(region);

        courtyard = region;
    }

    void setUpFountainRegion() throws Exception {
        DefaultDomain domain = new DefaultDomain();
        domain.addGroup(MEMBER_GROUP);

        ProtectedRegion region = new ProtectedCuboidRegion(FOUNTAIN_ID,
                BlockVector3.ZERO, BlockVector3.at(5, 5, 5));
        region.setMembers(domain);
        region.setPriority(10);
        manager.addRegion(region);

        fountain = region;
        fountain.setParent(courtyard);
        fountain.setFlag(Flags.FIRE_SPREAD, StateFlag.State.DENY);
    }

    void setUpNoFireRegion() throws Exception {
        ProtectedRegion region = new ProtectedCuboidRegion(NO_FIRE_ID,
                BlockVector3.at(100, 100, 100), BlockVector3.at(200, 200, 200));
        manager.addRegion(region);
        region.setFlag(Flags.FIRE_SPREAD, StateFlag.State.DENY);
    }

    @Test
    public void testNonBuildFlag() {
        ApplicableRegionSet appl;

        // Outside
        appl = manager.getApplicableRegions(outside);
        assertTrue(appl.testState(null, Flags.FIRE_SPREAD));
        // Inside courtyard
        appl = manager.getApplicableRegions(inCourtyard);
        assertTrue(appl.testState(null, Flags.FIRE_SPREAD));
        // Inside fountain
        appl = manager.getApplicableRegions(inFountain);
        assertFalse(appl.testState(null, Flags.FIRE_SPREAD));

        // Inside no fire zone
        appl = manager.getApplicableRegions(inNoFire);
        assertFalse(appl.testState(null, Flags.FIRE_SPREAD));
    }

    @Test
    public void testPlayer1BuildAccess() {
        ApplicableRegionSet appl;

        // Outside
        appl = manager.getApplicableRegions(outside);
        assertTrue(appl.testState(player1, Flags.BUILD));
        // Inside courtyard
        appl = manager.getApplicableRegions(inCourtyard);
        assertTrue(appl.testState(player1, Flags.BUILD));
        // Inside fountain
        appl = manager.getApplicableRegions(inFountain);
        assertTrue(appl.testState(player1, Flags.BUILD));
    }

    @Test
    public void testPlayer2BuildAccess() {
        ApplicableRegionSet appl;

        // Outside
        appl = manager.getApplicableRegions(outside);
        assertTrue(appl.testState(player2, Flags.BUILD));
        // Inside courtyard
        appl = manager.getApplicableRegions(inCourtyard);
        assertFalse(appl.testState(player2, Flags.BUILD));
        // Inside fountain
        appl = manager.getApplicableRegions(inFountain);
        assertTrue(appl.testState(player2, Flags.BUILD));
    }

    @ParameterizedTest(name = "useMaxPriorityAssociation={0}")
    @ValueSource(booleans = { false, true })
    public void testNonPlayerBuildAccessInOneRegion(boolean useMaxPriorityAssociation) {
        ApplicableRegionSet appl;

        HashSet<ProtectedRegion> source = new HashSet<>();
        source.add(courtyard);
        RegionOverlapAssociation assoc = new RegionOverlapAssociation(source, useMaxPriorityAssociation);

        // Outside
        appl = manager.getApplicableRegions(outside);
        assertTrue(appl.testState(assoc, Flags.BUILD));
        // Inside courtyard
        appl = manager.getApplicableRegions(inCourtyard);
        assertTrue(appl.testState(assoc, Flags.BUILD));
        // Inside fountain
        appl = manager.getApplicableRegions(inFountain);
        assertTrue(appl.testState(assoc, Flags.BUILD));
    }

    @ParameterizedTest(name = "useMaxPriorityAssociation={0}")
    @ValueSource(booleans = { false, true })
    public void testNonPlayerBuildAccessInBothRegions(boolean useMaxPriorityAssociation) {
        ApplicableRegionSet appl;

        HashSet<ProtectedRegion> source = new HashSet<>();
        source.add(fountain);
        source.add(courtyard);
        RegionOverlapAssociation assoc = new RegionOverlapAssociation(source, useMaxPriorityAssociation);

        // Outside
        appl = manager.getApplicableRegions(outside);
        assertTrue(appl.testState(assoc, Flags.BUILD));
        // Inside courtyard
        appl = manager.getApplicableRegions(inCourtyard);
        assertTrue(useMaxPriorityAssociation ^ appl.testState(assoc, Flags.BUILD));
        // Inside fountain
        appl = manager.getApplicableRegions(inFountain);
        assertTrue(appl.testState(assoc, Flags.BUILD));
    }

    @ParameterizedTest(name = "useMaxPriorityAssociation={0}")
    @ValueSource(booleans = { false, true })
    public void testNonPlayerBuildAccessInNoRegions(boolean useMaxPriorityAssociation) {
        ApplicableRegionSet appl;

        HashSet<ProtectedRegion> source = new HashSet<>();
        RegionOverlapAssociation assoc = new RegionOverlapAssociation(source, useMaxPriorityAssociation);

        // Outside
        appl = manager.getApplicableRegions(outside);
        assertTrue(appl.testState(assoc, Flags.BUILD));
        // Inside courtyard
        appl = manager.getApplicableRegions(inCourtyard);
        assertFalse(appl.testState(assoc, Flags.BUILD));
        // Inside fountain
        appl = manager.getApplicableRegions(inFountain);
        assertFalse(appl.testState(assoc, Flags.BUILD));
    }
}
