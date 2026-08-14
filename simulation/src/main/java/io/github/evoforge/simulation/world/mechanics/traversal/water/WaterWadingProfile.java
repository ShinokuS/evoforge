package io.github.evoforge.simulation.world.mechanics.traversal.water;

import io.github.evoforge.simulation.world.mechanics.geometry.CellSpace;

/** Terrestrial mover tolerance for water depth above its standing position. */
public record WaterWadingProfile(int maxDepth) {

    public WaterWadingProfile {
        CellSpace.requireHeight(maxDepth);
    }
}
