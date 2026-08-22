package io.github.evoforge.simulation.world.navigation.pathfinding;

/** Replaceable implementation parameters for the derived 3D hierarchy. */
public record PathHierarchyConfig(
        int sizeX,
        int sizeY,
        int sizeZ) {

    public PathHierarchyConfig {
        if (sizeX <= 0 || sizeY <= 0 || sizeZ <= 0) {
            throw new IllegalArgumentException(
                    "hierarchy cluster sizes must be > 0");
        }
    }

    public static PathHierarchyConfig standard() {
        return new PathHierarchyConfig(8, 8, 8);
    }
}
