package io.github.evoforge.simulation.world.terrain.shape;

import io.github.evoforge.simulation.world.atlas.ElevationField;
import io.github.evoforge.simulation.world.spatial.WorldBounds;

/** Internal geometry-only target construction policies for generated terrain. */
final class TerrainSurfaceTargetSamplers {
    private static final long CELL = ElevationField.SUBUNITS_PER_CELL;
    private static final long MAX_TRANSITION_NEIGHBOR_DELTA = CELL * 45L / 100L;
    private static final long MAX_RAW_TRANSITION_RELIEF = CELL * 65L / 100L;
    private static final int MIN_COHERENT_BAND_WIDTH = 3;
    private static final int V13_RAMP_LATERAL_SPACING = 5;

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
     * V12 accepts only locally coherent smooth voxel-transition bands. A candidate must have a
     * clear cardinal rise and belong to a contiguous same-direction lateral band at least three
     * cells wide. Anything else intentionally produces the neutral flat target for Shape fitting.
     */
    static TerrainSurfacePatch coherentVoxelTransitionPatch(ElevationField elevation, int x, int y) {
        requireElevation(elevation);
        TransitionIntent intent = coherentTransitionIntent(elevation, x, y);
        return intent == null
                ? TerrainSurfacePatch.flatTop()
                : normalizedCardinalPlane(intent.dx(), intent.dy());
    }

    /**
     * V13 uses the same coherent geometric eligibility as V12, but deliberately samples only a
     * sparse, evenly spaced set of sites along each eligible contour. This keeps ramps present across
     * the whole mountain surface without allowing one coherent face to become almost entirely ramps.
     * The policy still knows only surface direction, discrete level and coordinates; it never names
     * or selects a concrete runtime Shape.
     */
    static TerrainSurfacePatch sparseCoherentVoxelTransitionPatch(ElevationField elevation, int x, int y) {
        requireElevation(elevation);
        TransitionIntent intent = coherentTransitionIntent(elevation, x, y);
        if (intent == null || !isV13SparseSite(elevation, x, y, intent)) {
            return TerrainSurfacePatch.flatTop();
        }
        return normalizedCardinalPlane(intent.dx(), intent.dy());
    }

    private static TransitionIntent coherentTransitionIntent(ElevationField elevation, int x, int y) {
        TransitionIntent intent = transitionIntent(elevation, x, y, true);
        if (intent == null
                || coherentBandWidth(elevation, x, y, intent) < MIN_COHERENT_BAND_WIDTH) {
            return null;
        }
        return intent;
    }

    private static boolean isV13SparseSite(
            ElevationField elevation,
            int x,
            int y,
            TransitionIntent intent) {
        long level = Math.floorDiv(elevation.elevationSubunitsAt(x, y), CELL);
        int lateralCoordinate = intent.dx() != 0 ? y : x;
        int phase = (int) Math.floorMod(level * 2L, V13_RAMP_LATERAL_SPACING);
        return Math.floorMod(lateralCoordinate + phase, V13_RAMP_LATERAL_SPACING) == 0;
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

    private static int coherentBandWidth(
            ElevationField elevation,
            int x,
            int y,
            TransitionIntent center) {
        int sideX = -center.dy();
        int sideY = center.dx();
        int width = 1;

        width += contiguousSupport(elevation, x, y, center, sideX, sideY);
        width += contiguousSupport(elevation, x, y, center, -sideX, -sideY);
        return width;
    }

    /** Only two cells per side are needed to prove the minimum three-cell visual band. */
    private static int contiguousSupport(
            ElevationField elevation,
            int x,
            int y,
            TransitionIntent center,
            int stepX,
            int stepY) {
        int support = 0;
        for (int distance = 1; distance < MIN_COHERENT_BAND_WIDTH; distance++) {
            int neighbourX = x + stepX * distance;
            int neighbourY = y + stepY * distance;
            TransitionIntent neighbour = transitionIntent(
                    elevation,
                    neighbourX,
                    neighbourY,
                    true);
            if (!center.sameDirection(neighbour)) break;
            support++;
        }
        return support;
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
