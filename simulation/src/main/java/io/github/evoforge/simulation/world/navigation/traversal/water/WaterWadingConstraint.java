package io.github.evoforge.simulation.world.navigation.traversal.water;

import io.github.evoforge.simulation.world.liquid.water.WaterLookup;
import io.github.evoforge.simulation.world.geometry.CellSpace;
import io.github.evoforge.simulation.world.space.measurement.CellVolume;
import io.github.evoforge.simulation.world.geometry.GeometryLookup;
import io.github.evoforge.simulation.world.geometry.Shape;
import io.github.evoforge.simulation.world.navigation.traversal.MoverTraversalConstraint;
import io.github.evoforge.simulation.world.object.ObjectId;
import io.github.evoforge.simulation.world.object.ObjectLookup;
import io.github.evoforge.simulation.world.object.WorldObject;

/**
 * Terrestrial destination-water permission derived from current Water and neutral
 * cell-space Geometry.
 *
 * <p>Only the destination standing position is constrained. A mover already caught
 * in water deeper than its configured tolerance may still leave for a shallower or
 * dry destination rather than becoming mechanically trapped.
 */
public final class WaterWadingConstraint
        implements MoverTraversalConstraint {

    private static final int DEEPER_THAN_ONE_CELL =
            CellSpace.FULL_HEIGHT + 1;

    private final ObjectLookup objects;
    private final WaterWadingDefinitions definitions;
    private final WaterLookup water;
    private final GeometryLookup geometry;

    public WaterWadingConstraint(
            ObjectLookup objects,
            WaterWadingDefinitions definitions,
            WaterLookup water,
            GeometryLookup geometry) {

        if (objects == null
                || definitions == null
                || water == null
                || geometry == null) {
            throw new IllegalArgumentException(
                    "water wading dependencies must not be null");
        }
        this.objects = objects;
        this.definitions = definitions;
        this.water = water;
        this.geometry = geometry;
    }

    @Override
    public boolean allows(
            ObjectId moverId,
            int fromX,
            int fromY,
            int fromZ,
            int toX,
            int toY,
            int toZ) {

        if (moverId == null) {
            throw new IllegalArgumentException(
                    "moverId must not be null");
        }
        WorldObject mover = objects.get(moverId);
        if (mover == null) {
            throw new IllegalArgumentException(
                    "unknown mover: " + moverId);
        }

        if (!definitions.has(mover.definitionId())) {
            return true;
        }

        int depth = destinationDepth(
                toX,
                toY,
                toZ);
        return depth <= definitions
                .profile(mover.definitionId())
                .maxDepth();
    }

    int destinationDepth(
            int x,
            int y,
            int z) {

        int amount = CellVolume.requireValid(
                water.amount(x, y, z));
        if (amount == CellVolume.EMPTY) {
            return CellSpace.EMPTY_HEIGHT;
        }

        Shape shape = geometry.find(x, y, z);
        int capacity = CellSpace.capacity(shape);

        // Geometry may have changed after Water was stored. Existing displaced Water
        // is authoritative and must be treated conservatively until flow relocates it.
        if (amount > capacity) {
            return DEEPER_THAN_ONE_CELL;
        }

        int localDepth = CellSpace.surfaceHeight(
                shape,
                amount);
        if (localDepth < CellSpace.FULL_HEIGHT) {
            return localDepth;
        }

        if (z != Integer.MAX_VALUE
                && CellVolume.requireValid(
                        water.amount(x, y, z + 1))
                        > CellVolume.EMPTY) {
            return DEEPER_THAN_ONE_CELL;
        }

        return CellSpace.FULL_HEIGHT;
    }
}
