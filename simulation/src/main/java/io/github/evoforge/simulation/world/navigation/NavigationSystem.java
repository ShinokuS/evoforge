package io.github.evoforge.simulation.world.navigation;

import io.github.evoforge.simulation.world.mechanics.geometry.GeometryLookup;
import io.github.evoforge.simulation.world.mechanics.geometry.Shape;
import io.github.evoforge.simulation.world.mechanics.geometry.TransitionComposition;
import io.github.evoforge.simulation.world.mechanics.geometry.TransitionMask;
import io.github.evoforge.simulation.world.mechanics.geometry.TransitionPorts;

public final class NavigationSystem {

    private final GeometryLookup geometry;
    private final NavigationCache cache =
            new NavigationCache();
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

    public void invalidateGeometry(
            int x,
            int y,
            int z) {

        int minOffsetX = x == Integer.MIN_VALUE ? 0 : -1;
        int maxOffsetX = x == Integer.MAX_VALUE ? 0 : 1;
        int minOffsetY = y == Integer.MIN_VALUE ? 0 : -1;
        int maxOffsetY = y == Integer.MAX_VALUE ? 0 : 1;
        int minOffsetZ = z == Integer.MIN_VALUE ? 0 : -1;
        int maxOffsetZ = z == Integer.MAX_VALUE ? 0 : 1;

        for (int offsetZ = minOffsetZ; offsetZ <= maxOffsetZ; offsetZ++) {
            int sourceZ = z + offsetZ;

            for (int offsetY = minOffsetY; offsetY <= maxOffsetY; offsetY++) {
                int sourceY = y + offsetY;

                for (int offsetX = minOffsetX; offsetX <= maxOffsetX; offsetX++) {
                    cache.remove(
                            x + offsetX,
                            sourceY,
                            sourceZ);
                }
            }
        }
    }

    public void clearCache() {
        cache.clear();
    }

    private int transitions(
            int x,
            int y,
            int z) {

        int cached = cache.get(
                x,
                y,
                z);

        if (cached != NavigationCache.MISS) {
            return cached;
        }

        int resolved = resolveTransitions(
                x,
                y,
                z);

        cache.put(
                x,
                y,
                z,
                resolved);

        return resolved;
    }

    private int resolveTransitions(
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
