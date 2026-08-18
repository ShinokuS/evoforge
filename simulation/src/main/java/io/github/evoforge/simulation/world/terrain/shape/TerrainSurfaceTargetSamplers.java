package io.github.evoforge.simulation.world.terrain.shape;

import io.github.evoforge.simulation.world.atlas.ElevationField;
import io.github.evoforge.simulation.world.spatial.WorldBounds;

/** Internal geometry-only target construction policies for generated terrain. */
final class TerrainSurfaceTargetSamplers {
    private static final long CELL = ElevationField.SUBUNITS_PER_CELL;
    private static final long MAX_TRANSITION_NEIGHBOR_DELTA = CELL * 45L / 100L;
    private static final long MAX_RAW_TRANSITION_RELIEF = CELL * 65L / 100L;

    private TerrainSurfaceTargetSamplers() {
    }

    static TerrainSurfacePatch precisePatch(ElevationField elevation, int x, int y) {
        requireElevation(elevation);
        long center = elevation.elevationSubunitsAt(x, y);
        long base = Math.multiplyExact(Math.floorDiv(center, CELL), CELL);
        return new TerrainSurfacePatch(
                boundaryHeight(elevation, x, y, x - 1L, y, center) - base,
                boundaryHeight(elevation, x, y, x + 1L, y, center) - base,
                boundaryHeight(elevation, x, y, x, y - 1L, center) - base,
                boundaryHeight(elevation, x, y, x, y + 1L, center) - base);
    }

    static TerrainSurfacePatch smoothVoxelTransitionPatch(ElevationField elevation, int x, int y) {
        requireElevation(elevation);
        TerrainSurfacePatch precise = precisePatch(elevation, x, y);
        if (precise.reliefSubunits() >= MAX_RAW_TRANSITION_RELIEF) return precise;

        long center = elevation.elevationSubunitsAt(x, y);
        long layer = Math.floorDiv(center, CELL);
        long gradientX = precise.gradientXSubunits();
        long gradientY = precise.gradientYSubunits();
        long dominantGradient = Math.max(absolute(gradientX), absolute(gradientY));
        if (dominantGradient == 0L) return precise;

        if (!isSmoothNeighborhood(elevation, x, y, center, layer)) return precise;
        if (!crossesAscendingBoundary(elevation, x, y, layer, gradientX, gradientY)) return precise;

        return normalizedPlane(gradientX, gradientY, dominantGradient);
    }

    private static TerrainSurfacePatch normalizedPlane(
            long gradientX,
            long gradientY,
            long dominantGradient) {
        long half = CELL / 2L;
        long scaledX = Math.multiplyExact(gradientX, CELL) / dominantGradient;
        long scaledY = Math.multiplyExact(gradientY, CELL) / dominantGradient;
        return new TerrainSurfacePatch(
                half - scaledX / 2L,
                half + scaledX / 2L,
                half - scaledY / 2L,
                half + scaledY / 2L);
    }

    private static boolean isSmoothNeighborhood(
            ElevationField elevation,
            int x,
            int y,
            long center,
            long centerLayer) {
        int[][] offsets = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        for (int[] offset : offsets) {
            int nx = x + offset[0];
            int ny = y + offset[1];
            if (!elevation.contains(nx, ny)) continue;
            long neighbor = elevation.elevationSubunitsAt(nx, ny);
            if (absolute(neighbor - center) > MAX_TRANSITION_NEIGHBOR_DELTA) return false;
            long layerDifference = Math.floorDiv(neighbor, CELL) - centerLayer;
            if (absolute(layerDifference) > 1L) return false;
        }
        return true;
    }

    private static boolean crossesAscendingBoundary(
            ElevationField elevation,
            int x,
            int y,
            long centerLayer,
            long gradientX,
            long gradientY) {
        return gradientX > 0L && layerAt(elevation, x + 1, y, centerLayer) == centerLayer + 1L
                || gradientX < 0L && layerAt(elevation, x - 1, y, centerLayer) == centerLayer + 1L
                || gradientY > 0L && layerAt(elevation, x, y + 1, centerLayer) == centerLayer + 1L
                || gradientY < 0L && layerAt(elevation, x, y - 1, centerLayer) == centerLayer + 1L;
    }

    private static long layerAt(ElevationField elevation, int x, int y, long fallback) {
        return elevation.contains(x, y)
                ? Math.floorDiv(elevation.elevationSubunitsAt(x, y), CELL)
                : fallback;
    }

    private static long boundaryHeight(
            ElevationField elevation,
            int x,
            int y,
            long neighborX,
            long neighborY,
            long center) {
        WorldBounds bounds = elevation.bounds();
        if (neighborX < bounds.minX() || neighborX > bounds.maxX()
                || neighborY < bounds.minY() || neighborY > bounds.maxY()) {
            return center;
        }
        long neighbor = elevation.elevationSubunitsAt((int) neighborX, (int) neighborY);
        return midpoint(center, neighbor);
    }

    private static long midpoint(long first, long second) {
        return first / 2L + second / 2L + (first % 2L + second % 2L) / 2L;
    }

    private static long absolute(long value) {
        if (value == Long.MIN_VALUE) throw new ArithmeticException("surface difference exceeds signed range");
        return Math.abs(value);
    }

    private static void requireElevation(ElevationField elevation) {
        if (elevation == null) throw new IllegalArgumentException("elevation must not be null");
    }
}
