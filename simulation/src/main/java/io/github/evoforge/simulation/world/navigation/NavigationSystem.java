package io.github.evoforge.simulation.world.navigation;

import io.github.evoforge.simulation.world.mechanics.geometry.GeometryLookup;
import io.github.evoforge.simulation.world.mechanics.geometry.Shape;
import io.github.evoforge.simulation.world.mechanics.geometry.TransitionComposition;
import io.github.evoforge.simulation.world.mechanics.geometry.TransitionMask;
import io.github.evoforge.simulation.world.mechanics.geometry.TransitionPorts;

public final class NavigationSystem {

    private final GeometryLookup geometry;
    private final NavigationLookup lookup;

    public NavigationSystem(
            GeometryLookup geometry) {

        if (geometry == null) {
            throw new IllegalArgumentException(
                    "geometry must not be null");
        }

        this.geometry = geometry;
        lookup = this::transitions;
    }

    public NavigationLookup lookup() {
        return lookup;
    }

    private int transitions(
            int x,
            int y,
            int z) {

        long ports = TransitionPorts.NONE;
        int blocks = TransitionMask.NONE;

        for (int offsetZ = -1; offsetZ <= 1; offsetZ++) {
            long shapeZ = (long) z + offsetZ;

            if (shapeZ < Integer.MIN_VALUE
                    || shapeZ > Integer.MAX_VALUE) {
                continue;
            }

            for (int offsetY = -1; offsetY <= 1; offsetY++) {
                long shapeY = (long) y + offsetY;

                if (shapeY < Integer.MIN_VALUE
                        || shapeY > Integer.MAX_VALUE) {
                    continue;
                }

                for (int offsetX = -1; offsetX <= 1; offsetX++) {
                    long shapeX = (long) x + offsetX;

                    if (shapeX < Integer.MIN_VALUE
                            || shapeX > Integer.MAX_VALUE) {
                        continue;
                    }

                    Shape shape = geometry.find(
                            (int) shapeX,
                            (int) shapeY,
                            (int) shapeZ);

                    if (shape == null) {
                        continue;
                    }

                    int relativeX = -offsetX;
                    int relativeY = -offsetY;
                    int relativeZ = -offsetZ;

                    ports |= shape.transitionPorts(
                            relativeX,
                            relativeY,
                            relativeZ);

                    blocks |= shape.transitionBlocks(
                            relativeX,
                            relativeY,
                            relativeZ);
                }
            }
        }

        return TransitionComposition.resolve(
                ports,
                blocks);
    }
}
