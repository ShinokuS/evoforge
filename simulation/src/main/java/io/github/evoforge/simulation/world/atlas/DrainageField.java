package io.github.evoforge.simulation.world.atlas;

import io.github.evoforge.simulation.world.spatial.WorldBounds;

/** Immutable closed-world drainage topology over global XY world columns. */
public interface DrainageField {
    WorldBounds bounds();

    boolean hasDownstream(int x, int y);

    int downstreamXAt(int x, int y);

    int downstreamYAt(int x, int y);

    /** Number of world columns whose drainage path reaches this column, including itself. */
    long contributingAreaAt(int x, int y);

    /** Global X coordinate of the terminal basin representative reached by this column. */
    int terminalXAt(int x, int y);

    /** Global Y coordinate of the terminal basin representative reached by this column. */
    int terminalYAt(int x, int y);

    default boolean contains(int x, int y) {
        WorldBounds bounds = bounds();
        return x >= bounds.minX() && x <= bounds.maxX()
                && y >= bounds.minY() && y <= bounds.maxY();
    }
}
