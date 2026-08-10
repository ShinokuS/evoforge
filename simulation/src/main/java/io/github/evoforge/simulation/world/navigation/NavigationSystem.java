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

        int minOffsetX = x == Integer.MIN_VALUE ? 0 : -1;
        int maxOffsetX = x == Integer.MAX_VALUE ? 0 : 1;
        int minOffsetY = y == Integer.MIN_VALUE ? 0 : -1;
        int maxOffsetY = y == Integer.MAX_VALUE ? 0 : 1;
        int minOffsetZ = z == Integer.MIN_VALUE ? 0 : -1;
        int maxOffsetZ = z == Integer.MAX_VALUE ? 0 : 1;

        long ports = TransitionPorts.NONE;
        int blocks = TransitionMask.NONE;

        for (int offsetZ = minOffsetZ; offsetZ <= maxOffsetZ; offsetZ++) {
            int shapeZ = z + offsetZ;

            for (int offsetY = minOffsetY; offsetY <= maxOffsetY; offsetY++) {
                int shapeY = y + offsetY;

                for (int offsetX = minOffsetX; offsetX <= maxOffsetX; offsetX++) {
                    Shape shape = geometry.find(
                            x + offsetX,
                            shapeY,
                            shapeZ);

                    if (shape == null) {
                        continue;
                    }

                    ports |= shape.transitionPorts(
                            -offsetX,
                            -offsetY,
                            -offsetZ);

                    blocks |= shape.transitionBlocks(
                            -offsetX,
                            -offsetY,
                            -offsetZ);
                }
            }
        }

        return TransitionComposition.resolve(
                ports,
                blocks);
    }
}
