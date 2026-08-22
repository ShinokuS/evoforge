package io.github.evoforge.simulation.world.navigation.traversal.water;

import io.github.evoforge.simulation.world.geometry.CellSpace;

/** Terrestrial mover tolerance for water depth above its standing position. */
public record WaterWadingProfile(int maxDepth) {

    public WaterWadingProfile {
        CellSpace.requireHeight(maxDepth);
    }
}
