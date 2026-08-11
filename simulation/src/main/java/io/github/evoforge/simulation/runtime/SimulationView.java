package io.github.evoforge.simulation.runtime;

import io.github.evoforge.simulation.world.landscape.terrain.TerrainLookup;
import io.github.evoforge.simulation.world.mechanics.geometry.GeometryLookup;
import io.github.evoforge.simulation.world.navigation.NavigationLookup;
import io.github.evoforge.simulation.world.object.ObjectLookup;
import io.github.evoforge.simulation.world.spatial.TransformLookup;
import io.github.evoforge.simulation.world.spatial.indexes.CellSpatialIndex;

/**
 * Read-only capabilities exposed by a started simulation runtime.
 */
public record SimulationView(
        ObjectLookup objects,
        TransformLookup transforms,
        TerrainLookup terrain,
        GeometryLookup geometry,
        NavigationLookup navigation,
        CellSpatialIndex.Lookup cells) {

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
        if (geometry == null) {
            throw new IllegalArgumentException(
                    "geometry must not be null");
        }
        if (navigation == null) {
            throw new IllegalArgumentException(
                    "navigation must not be null");
        }
        if (cells == null) {
            throw new IllegalArgumentException(
                    "cells must not be null");
        }
    }
}
