package io.github.evoforge.visualizer.visual;

import io.github.evoforge.simulation.runtime.SimulationView;
import io.github.evoforge.simulation.world.geometry.Shape;

/**
 * Cheap column-local projection used by the default open-world top-down view.
 * It exposes the highest terrain anchor and current highest Water cell without
 * constructing or caching a second world representation.
 */
public final class SurfaceProjectionResolver {

    public static final int NO_Z = Integer.MIN_VALUE;

    private final SimulationView view;

    public SurfaceProjectionResolver(SimulationView view) {
        if (view == null) {
            throw new IllegalArgumentException("view must not be null");
        }
        this.view = view;
    }

    public SurfaceCell resolve(int x, int y) {
        boolean hasTerrain = view.terrainSurfaces().hasColumn(x, y);
        boolean hasWater = view.waterSurfaces().hasColumn(x, y);
        if (!hasTerrain && !hasWater) {
            return SurfaceCell.EMPTY;
        }

        int terrainZ = hasTerrain
                ? view.terrainSurfaces().topZ(x, y)
                : NO_Z;
        Shape shape = hasTerrain
                ? view.geometry().find(x, y, terrainZ)
                : null;
        int waterZ = hasWater
                ? view.waterSurfaces().topZ(x, y)
                : NO_Z;

        return new SurfaceCell(
                true,
                hasTerrain,
                terrainZ,
                standingZ(terrainZ),
                shape,
                hasWater,
                waterZ);
    }

    public int terrainZ(int x, int y) {
        return view.terrainSurfaces().hasColumn(x, y)
                ? view.terrainSurfaces().topZ(x, y)
                : NO_Z;
    }

    public int standingZ(int x, int y) {
        int terrainZ = terrainZ(x, y);
        return standingZ(terrainZ);
    }

    private static int standingZ(int terrainZ) {
        if (terrainZ == NO_Z) return NO_Z;
        if (terrainZ == Integer.MAX_VALUE) return Integer.MAX_VALUE;
        return terrainZ + 1;
    }

    public record SurfaceCell(
            boolean present,
            boolean hasTerrain,
            int terrainZ,
            int standingZ,
            Shape shape,
            boolean hasWater,
            int waterZ) {

        private static final SurfaceCell EMPTY = new SurfaceCell(
                false,
                false,
                NO_Z,
                NO_Z,
                null,
                false,
                NO_Z);
    }
}
