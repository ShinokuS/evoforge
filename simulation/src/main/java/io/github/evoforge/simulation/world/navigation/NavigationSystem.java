package io.github.evoforge.simulation.world.navigation;

import io.github.evoforge.simulation.world.geometry.CellFace;
import io.github.evoforge.simulation.world.geometry.GeometryLookup;
import io.github.evoforge.simulation.world.geometry.Shape;
import io.github.evoforge.simulation.world.geometry.SurfaceBoundaryContinuity;
import io.github.evoforge.simulation.world.geometry.TransitionComposition;
import io.github.evoforge.simulation.world.geometry.TransitionMask;
import io.github.evoforge.simulation.world.geometry.TransitionPorts;

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
        int minOffsetZ =
                z == Integer.MIN_VALUE
                        ? 0
                        : z == Integer.MIN_VALUE + 1
                                ? -1
                                : -2;
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

        int resolved = TransitionComposition.resolve(ports, blocks);
        return filterHorizontalSurfaceJoins(x, y, z, resolved);
    }

    /**
     * Same-level cardinal crossings must join the same physical surface line on both
     * sides. This is Shape-agnostic: ramps, flats and future surface Shapes provide
     * their own boundary profiles through the common geometry contract.
     */
    private int filterHorizontalSurfaceJoins(
            int x,
            int y,
            int z,
            int transitions) {
        if (transitions == TransitionMask.NONE || z == Integer.MIN_VALUE) {
            return transitions;
        }

        int supportZ = z - 1;
        Shape source = geometry.find(x, y, supportZ);
        if (source == null) return transitions;

        int filtered = transitions;
        for (CellFace face : CellFace.values()) {
            if (face.dz() != 0) continue;
            int direction = TransitionMask.of(face.dx(), face.dy(), 0);
            if ((filtered & direction) == 0) continue;
            if ((face.dx() < 0 && x == Integer.MIN_VALUE)
                    || (face.dx() > 0 && x == Integer.MAX_VALUE)
                    || (face.dy() < 0 && y == Integer.MIN_VALUE)
                    || (face.dy() > 0 && y == Integer.MAX_VALUE)) {
                continue;
            }

            Shape destination = geometry.find(
                    x + face.dx(),
                    y + face.dy(),
                    supportZ);
            if (destination != null
                    && !SurfaceBoundaryContinuity.aligns(
                            source,
                            supportZ,
                            face,
                            destination,
                            supportZ)) {
                filtered &= ~direction;
            }
        }
        return filtered;
    }
}
