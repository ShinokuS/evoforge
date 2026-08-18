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
                boundaryHeight(elevation, x - 1L, y, center) - base,
                boundaryHeight(elevation, x + 1L, y, center) - base,
                boundaryHeight(elevation, x, y - 1L, center) - base,
                boundaryHeight(elevation, x, y + 1L, center) - base);
    }

    static TerrainSurfacePatch smoothVoxelTransitionPatch(ElevationField elevation, int x, int y) {
        requireElevation(elevation);
        TransitionIntent intent = transitionIntent(elevation, x, y, false);
        if (intent == null) return precisePatch(elevation, x, y);
        return normalizedCardinalPlane(intent.dx(), intent.dy());
    }

    /**
     * V12 rejects one-cell turns and locally rotating contour fragments. A generated transition is
     * promoted only when at least one lateral neighbour supports the same cardinal ascending
     * direction, and a lateral neighbour that is itself a valid transition may not point elsewhere.
     * This keeps the policy geometry-only while preventing spiral-like chains of unrelated ramps.
     */
    static TerrainSurfacePatch coherentVoxelTransitionPatch(ElevationField elevation, int x, int y) {
        requireElevation(elevation);
        TransitionIntent intent = transitionIntent(elevation, x, y, true);
        if (intent == null || !hasCoherentLateralSupport(elevation, x, y, intent)) {
            return precisePatch(elevation, x, y);
        }
        return normalizedCardinalPlane(intent.dx(), intent.dy());
    }

    private static TransitionIntent transitionIntent(
            ElevationField elevation,
            int x,
            int y,
            boolean requireCardinalDominance) {
        if (!elevation.contains(x, y)) return null;
        TerrainSurfacePatch precise = precisePatch(elevation, x, y);
        if (precise.reliefSubunits() >= MAX_RAW_TRANSITION_RELIEF) return null;

        long center = elevation.elevationSubunitsAt(x, y);
        long layer = Math.floorDiv(center, CELL);
        long gradientX = precise.gradientXSubunits();
        long gradientY = precise.gradientYSubunits();
        long absoluteX = absolute(gradientX);
        long absoluteY = absolute(gradientY);
        long dominantGradient = Math.max(absoluteX, absoluteY);
        if (dominantGradient == 0L) return null;

        if (!smoothNeighbor(elevation, x - 1, y, center, layer)
                || !smoothNeighbor(elevation, x + 1, y, center, layer)
                || !smoothNeighbor(elevation, x, y - 1, center, layer)
                || !smoothNeighbor(elevation, x, y + 1, center, layer)) {
            return null;
        }

        int dx;
        int dy;
        if (absoluteX >= absoluteY) {
            if (requireCardinalDominance && absoluteX * 2L < absoluteY * 3L) return null;
            dx = gradientX > 0L ? 1 : -1;
            dy = 0;
        } else {
            if (requireCardinalDominance && absoluteY * 2L < absoluteX * 3L) return null;
            dx = 0;
            dy = gradientY > 0L ? 1 : -1;
        }

        if (!crossesAscendingBoundary(elevation, x, y, layer, dx, dy)) return null;
        return new TransitionIntent(dx, dy);
    }

    private static boolean hasCoherentLateralSupport(
            ElevationField elevation,
            int x,
            int y,
            TransitionIntent center) {
        int sideX = -center.dy();
        int sideY = center.dx();
        int support = 0;

        for (int sign : new int[] {-1, 1}) {
            int neighbourX = x + sideX * sign;
            int neighbourY = y + sideY * sign;
            if (!elevation.contains(neighbourX, neighbourY)) continue;

            TransitionIntent neighbour = transitionIntent(
                    elevation,
                    neighbourX,
                    neighbourY,
                    true);
            if (neighbour == null) continue;
            if (!center.sameDirection(neighbour)) return false;
            support++;
        }
        return support > 0;
    }

    private static TerrainSurfacePatch normalizedCardinalPlane(int dx, int dy) {
        long half = CELL / 2L;
        long gradientX = (long) dx * CELL;
        long gradientY = (long) dy * CELL;
        return new TerrainSurfacePatch(
                half - gradientX / 2L,
                half + gradientX / 2L,
                half - gradientY / 2L,
                half + gradientY / 2L);
    }

    private static boolean smoothNeighbor(
            ElevationField elevation,
            int x,
            int y,
            long center,
            long centerLayer) {
        if (!elevation.contains(x, y)) return true;
        long neighbor = elevation.elevationSubunitsAt(x, y);
        if (absolute(neighbor - center) > MAX_TRANSITION_NEIGHBOR_DELTA) return false;
        return absolute(Math.floorDiv(neighbor, CELL) - centerLayer) <= 1L;
    }

    private static boolean crossesAscendingBoundary(
            ElevationField elevation,
            int x,
            int y,
            long centerLayer,
            int dx,
            int dy) {
        return layerAt(elevation, x + dx, y + dy, centerLayer) == centerLayer + 1L;
    }

    private static long layerAt(ElevationField elevation, int x, int y, long fallback) {
        return elevation.contains(x, y)
                ? Math.floorDiv(elevation.elevationSubunitsAt(x, y), CELL)
                : fallback;
    }

    private static long boundaryHeight(
            ElevationField elevation,
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
        if (value == Long.MIN_VALUE) {
            throw new ArithmeticException("surface difference exceeds signed range");
        }
        return Math.abs(value);
    }

    private static void requireElevation(ElevationField elevation) {
        if (elevation == null) throw new IllegalArgumentException("elevation must not be null");
    }

    private record TransitionIntent(int dx, int dy) {
        private TransitionIntent {
            if (Math.abs(dx) + Math.abs(dy) != 1) {
                throw new IllegalArgumentException("transition direction must be cardinal");
            }
        }

        private boolean sameDirection(TransitionIntent other) {
            return other != null && dx == other.dx && dy == other.dy;
        }
    }
}
