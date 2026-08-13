package io.github.evoforge.simulation.runtime;

import io.github.evoforge.simulation.world.landscape.terrain.TerrainExtentLookup;
import io.github.evoforge.simulation.world.landscape.terrain.TerrainLookup;
import io.github.evoforge.simulation.world.landscape.terrain.TerrainRevisionLookup;
import io.github.evoforge.simulation.world.mechanics.geometry.GeometryLookup;
import io.github.evoforge.simulation.world.mechanics.movement.MoveToLookup;
import io.github.evoforge.simulation.world.mechanics.occupancy.OccupancyLookup;
import io.github.evoforge.simulation.world.navigation.NavigationLookup;
import io.github.evoforge.simulation.world.object.ObjectLookup;
import io.github.evoforge.simulation.world.pathfinding.Pathfinder;
import io.github.evoforge.simulation.world.spatial.CellObjectLookup;
import io.github.evoforge.simulation.world.spatial.TransformLookup;

/**
 * Read-only capabilities exposed by a started simulation runtime.
 */
public record SimulationView(
        ObjectLookup objects,
        TransformLookup transforms,
        TerrainLookup terrain,
        TerrainExtentLookup terrainExtents,
        TerrainRevisionLookup terrainRevision,
        GeometryLookup geometry,
        NavigationLookup navigation,
        OccupancyLookup occupancy,
        CellObjectLookup cells,
        Pathfinder pathfinder,
        MoveToLookup moveTo) {

    public SimulationView {
        if (objects == null) {
            throw new IllegalArgumentException(
                    "objects must not be null");
        }
        if (transforms == null) {
            throw new IllegalArgumentException(
                    "transforms must not be null");
        }
        if (terrain == null) {
            throw new IllegalArgumentException(
                    "terrain must not be null");
        }
        if (terrainExtents == null) {
            throw new IllegalArgumentException(
                    "terrainExtents must not be null");
        }
        if (terrainRevision == null) {
            throw new IllegalArgumentException(
                    "terrainRevision must not be null");
        }
        if (geometry == null) {
            throw new IllegalArgumentException(
                    "geometry must not be null");
        }
        if (navigation == null) {
            throw new IllegalArgumentException(
                    "navigation must not be null");
        }
        if (occupancy == null) {
            throw new IllegalArgumentException(
                    "occupancy must not be null");
        }
        if (cells == null) {
            throw new IllegalArgumentException(
                    "cells must not be null");
        }
        if (pathfinder == null) {
            throw new IllegalArgumentException(
                    "pathfinder must not be null");
        }
        if (moveTo == null) {
            throw new IllegalArgumentException(
                    "moveTo must not be null");
        }
    }
}
