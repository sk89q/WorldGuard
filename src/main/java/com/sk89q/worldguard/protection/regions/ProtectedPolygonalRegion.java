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
package com.sk89q.worldguard.protection.regions;

import java.util.ArrayList;
import java.util.List;

import com.sk89q.worldedit.BlockVector;
import com.sk89q.worldedit.BlockVector2D;
import com.sk89q.worldedit.Vector;
import com.sk89q.worldguard.protection.UnsupportedIntersectionException;

public class ProtectedPolygonalRegion extends ProtectedRegion {

    protected List<BlockVector2D> points;
    protected int minY;
    protected int maxY;
    private BlockVector min;
    private BlockVector max;

    public ProtectedPolygonalRegion(String id, List<BlockVector2D> points, int minY, int maxY) {
        super(id);
        this.points = points;
        this.minY = minY;
        this.maxY = maxY;

        int minX = points.get(0).getBlockX();
        int minZ = points.get(0).getBlockZ();
        int maxX = points.get(0).getBlockX();
        int maxZ = points.get(0).getBlockZ();

        for (BlockVector2D v : points) {
            int x = v.getBlockX();
            int z = v.getBlockZ();
            if (x < minX) {
                minX = x;
            }
            if (z < minZ) {
                minZ = z;
            }
            if (x > maxX) {
                maxX = x;
            }
            if (z > maxZ) {
                maxZ = z;
            }
        }

        min = new BlockVector(minX, minY, minZ);
        max = new BlockVector(maxX, maxY, maxZ);
    }

    public List<BlockVector2D> getPoints() {
        return points;
    }

    @Override
    public BlockVector getMinimumPoint() {
        return min;
    }

    @Override
    public BlockVector getMaximumPoint() {
        return max;
    }

    /**
     * Checks to see if a point is inside this region.
     */
    @Override
    public boolean contains(Vector pt) {
        int targetX = pt.getBlockX(); //wide
        int targetY = pt.getBlockY(); //height
        int targetZ = pt.getBlockZ(); //depth

        if (targetY < minY || targetY > maxY) {
            return false;
        }
        //Quick and dirty check.
        if (targetX < min.getBlockX() || targetX > max.getBlockX() || targetZ < min.getBlockZ() || targetZ > max.getBlockZ()) {
            return false;
        }
        boolean inside = false;
        int npoints = points.size();
        int xNew, zNew;
        int xOld, zOld;
        int x1, z1;
        int x2, z2;
        long crossproduct;
        int i;

        xOld = points.get(npoints - 1).getBlockX();
        zOld = points.get(npoints - 1).getBlockZ();

        for (i = 0; i < npoints; i++) {
            xNew = points.get(i).getBlockX();
            zNew = points.get(i).getBlockZ();
            //Check for corner
            if (xNew == targetX && zNew == targetZ) {
                return true;
            }
            if (xNew > xOld) {
                x1 = xOld;
                x2 = xNew;
                z1 = zOld;
                z2 = zNew;
            } else {
                x1 = xNew;
                x2 = xOld;
                z1 = zNew;
                z2 = zOld;
            }
            if (x1 <= targetX && targetX <= x2) {
                crossproduct = ((long) targetZ - (long) z1) * (long) (x2 - x1)
                    - ((long) z2 - (long) z1) * (long) (targetX - x1);
                if (crossproduct == 0) {
                    if ((z1 <= targetZ) == (targetZ <= z2)) return true; // on edge
                } else if (crossproduct < 0 && (x1 != targetX)) {
                    inside = !inside;
                }
            }
            xOld = xNew;
            zOld = zNew;
        }

        return inside;
    }

    @Override
    public List<ProtectedRegion> getIntersectingRegions(List<ProtectedRegion> regions) throws UnsupportedIntersectionException {
        int numRegions = regions.size();
        int numPoints = points.size();
        List<ProtectedRegion> intersectingRegions = new ArrayList<ProtectedRegion>();
        int i, i2;
    
        for (i = 0; i < numRegions; i++) {
            ProtectedRegion region = regions.get(i);
            BlockVector rMinPoint = region.getMinimumPoint();
            BlockVector rMaxPoint = region.getMaximumPoint();
    
            // Check whether the region is outside the min and max vector
            if ((rMinPoint.getBlockX() < min.getBlockX() && rMaxPoint.getBlockX() < min.getBlockX()) 
                    || (rMinPoint.getBlockX() > max.getBlockX() && rMaxPoint.getBlockX() > max.getBlockX())
                    || (rMinPoint.getBlockY() < min.getBlockY() && rMaxPoint.getBlockY() < min.getBlockY())
                    || (rMinPoint.getBlockY() > max.getBlockY() && rMaxPoint.getBlockY() > max.getBlockY())
                    || (rMinPoint.getBlockZ() < min.getBlockZ() && rMaxPoint.getBlockZ() < min.getBlockZ())
                    || (rMinPoint.getBlockZ() > max.getBlockZ() && rMaxPoint.getBlockZ() > max.getBlockZ())) {

                    // One or more dimensions wholly outside. Regions aren't overlapping.
                    continue;
            }

            boolean regionIsIntersecting = false;
            // Check whether the regions points are inside the other region
            for (i2 = 0; i2 < numPoints; i2++) {
                BlockVector2D pt = points.get(i2);
                int pointX = Math.min(Math.max(rMinPoint.getBlockX(), pt.getBlockX()), rMaxPoint.getBlockX());
                int pointZ = Math.min(Math.max(rMinPoint.getBlockZ(), pt.getBlockZ()), rMaxPoint.getBlockZ());
                int pointY1 = Math.min(Math.max(rMinPoint.getBlockY(), minY), rMaxPoint.getBlockY());
                int pointY2 = Math.min(Math.max(rMinPoint.getBlockY(), maxY), rMaxPoint.getBlockY());
                Vector pt1 = new Vector(pointX, pointY1, pointZ);
                Vector pt2 = new Vector(pointX, pointY2, pointZ);
                if (region.contains(pt1) || region.contains(pt2)) {
                    intersectingRegions.add(regions.get(i));
                    regionIsIntersecting = true;
                    break;
                }
            }
            if (region instanceof ProtectedPolygonalRegion && !regionIsIntersecting) {
                // Check whether the other regions points are inside the current region
                for (i2 = 0; i2 < ((ProtectedPolygonalRegion)region).getPoints().size(); i2++) {
                    BlockVector2D pt2Dr = ((ProtectedPolygonalRegion)region).getPoints().get(i2);
                    int pointX = Math.min(Math.max(min.getBlockX(), pt2Dr.getBlockX()), max.getBlockX());
                    int pointZ = Math.min(Math.max(min.getBlockZ(), pt2Dr.getBlockZ()), max.getBlockZ());
                    int pointY1 = Math.min(Math.max(min.getBlockY(), ((ProtectedPolygonalRegion)region).minY), max.getBlockY());
                    int pointY2 = Math.min(Math.max(min.getBlockY(), ((ProtectedPolygonalRegion)region).maxY), max.getBlockY());
                    Vector pt1 = new Vector(pointX, pointY1, pointZ);
                    Vector pt2 = new Vector(pointX, pointY2, pointZ);
                    if (this.contains(pt1) || this.contains(pt2)) {
                        intersectingRegions.add(regions.get(i));
                        break;
                    }
                }
            }
        }
    
        return intersectingRegions;
    }


    /**
     * Return the type of region as a user-friendly name.
     * 
     * @return type of region
     */
    @Override
    public String getTypeName() {
        return "polygon";
    }

    /**
     * Get the number of Blocks in this region
     * 
     * @return
     */
    @Override
    public int volume() {
        int volume = 0;
        int numPoints = points.size();
        if (numPoints < 3) {
            return 0;
        }

        double area = 0;
        int xa, z1, z2;
/* this just does not work
        for (int i = 0; i <= numPoints - 1; i++) {
            xa = points.get(i).getBlockX();
            //za = points.get(i).getBlockZ();

            if (points.get(i + 1) == null) {
                z1 = points.get(0).getBlockZ();
            } else {
                z1 = points.get(i + 1).getBlockZ();
            }
            if (points.get(i - 1) == null) {
                z2 = points.get(numPoints - 1).getBlockZ();
            } else {
                z2 = points.get(i - 1).getBlockZ();
            }

            area = area + (xa * (z1 - z2));
        }

        xa = points.get(0).getBlockX();
        //za = points.get(0).getBlockZ();

        area = area + (xa * (points.get(1).getBlockZ() - points.get(numPoints - 1).getBlockZ()));

        volume = (Math.abs(maxY - minY) + 1) * (int) Math.ceil((Math.abs(area) / 2));
*/
        return volume;
    }
}
