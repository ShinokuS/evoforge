package io.github.evoforge.simulation.world.terrain.shape;

import io.github.evoforge.simulation.world.atlas.ElevationField;
import io.github.evoforge.simulation.world.spatial.WorldBounds;

/** Internal geometry-only target construction policies for generated terrain. */
final class TerrainSurfaceTargetSamplers {
    private static final long CELL = ElevationField.SUBUNITS_PER_CELL;

    private static final TransitionPolicy V12_POLICY = new TransitionPolicy(
            CELL * 45L / 100L,
            CELL * 65L / 100L,
            3,
            2);
    private static final TransitionPolicy V13_POLICY = new TransitionPolicy(
            CELL * 55L / 100L,
            CELL * 78L / 100L,
            5,
            4);

    private static final int MIN_COHERENT_BAND_WIDTH = 3;
    private static final int V13_SPARSE_ANCHOR_RADIUS = 7;
    private static final int V13_PATCH_HALF_WIDTH = 1;

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
        TransitionIntent intent = transitionIntent(elevation, x, y, false, V12_POLICY);
        if (intent == null) return precisePatch(elevation, x, y);
        return normalizedCardinalPlane(intent.dx(), intent.dy());
    }

    static TerrainSurfacePatch coherentVoxelTransitionPatch(ElevationField elevation, int x, int y) {
        requireElevation(elevation);
        TransitionIntent intent = transitionIntent(elevation, x, y, true, V12_POLICY);
        if (intent == null
                || coherentBandWidth(elevation, x, y, intent, V12_POLICY) < MIN_COHERENT_BAND_WIDTH) {
            return TerrainSurfacePatch.flatTop();
        }
        return normalizedCardinalPlane(intent.dx(), intent.dy());
    }

    /**
     * V13 first finds a slightly broader set of geometrically coherent transition candidates, then
     * keeps irregular short patches along each contour. Hashing includes discrete Z and direction,
     * so neighbouring levels do not repeat a mechanical comb pattern.
     */
    static TerrainSurfacePatch sparseCoherentVoxelTransitionPatch(ElevationField elevation, int x, int y) {
        requireElevation(elevation);
        TransitionIntent intent = transitionIntent(elevation, x, y, true, V13_POLICY);
        if (intent == null
                || coherentBandWidth(elevation, x, y, intent, V13_POLICY) < MIN_COHERENT_BAND_WIDTH
                || !selectedSparsePatch(elevation, x, y, intent)) {
            return TerrainSurfacePatch.flatTop();
        }
        return normalizedCardinalPlane(intent.dx(), intent.dy());
    }

    private static boolean selectedSparsePatch(
            ElevationField elevation,
            int x,
            int y,
            TransitionIntent intent) {
        int sideX = -intent.dy();
        int sideY = intent.dx();
        for (int offset = -V13_PATCH_HALF_WIDTH; offset <= V13_PATCH_HALF_WIDTH; offset++) {
            int anchorX = x + sideX * offset;
            int anchorY = y + sideY * offset;
            TransitionIntent anchorIntent = transitionIntent(
                    elevation, anchorX, anchorY, true, V13_POLICY);
            if (!intent.sameDirection(anchorIntent)) continue;
            if (isSparseAnchor(elevation, anchorX, anchorY, intent, sideX, sideY)) return true;
        }
        return false;
    }

    private static boolean isSparseAnchor(
            ElevationField elevation,
            int x,
            int y,
            TransitionIntent intent,
            int sideX,
            int sideY) {
        long layer = Math.floorDiv(elevation.elevationSubunitsAt(x, y), CELL);
        long centerHash = transitionHash(x, y, layer, intent.dx(), intent.dy());
        for (int distance = 1; distance <= V13_SPARSE_ANCHOR_RADIUS; distance++) {
            if (hasLowerCompatibleHash(
                    elevation,
                    x + sideX * distance,
                    y + sideY * distance,
                    intent,
                    centerHash)) {
                return false;
            }
            if (hasLowerCompatibleHash(
                    elevation,
                    x - sideX * distance,
                    y - sideY * distance,
                    intent,
                    centerHash)) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasLowerCompatibleHash(
            ElevationField elevation,
            int x,
            int y,
            TransitionIntent expected,
            long centerHash) {
        TransitionIntent other = transitionIntent(elevation, x, y, true, V13_POLICY);
        if (!expected.sameDirection(other)) return false;
        long layer = Math.floorDiv(elevation.elevationSubunitsAt(x, y), CELL);
        long hash = transitionHash(x, y, layer, expected.dx(), expected.dy());
        return Long.compareUnsigned(hash, centerHash) < 0;
    }

    private static long transitionHash(int x, int y, long layer, int dx, int dy) {
        long value = 0x9e3779b97f4a7c15L;
        value ^= (long) x * 0xbf58476d1ce4e5b9L;
        value = mix64(value);
        value ^= (long) y * 0x94d049bb133111ebL;
        value = mix64(value);
        value ^= layer * 0xd6e8feb86659fd93L;
        value ^= (long) (dx + 2 * dy) * 0xa0761d6478bd642fL;
        return mix64(value);
    }

    private static long mix64(long value) {
        value ^= value >>> 30;
        value *= 0xbf58476d1ce4e5b9L;
        value ^= value >>> 27;
        value *= 0x94d049bb133111ebL;
        value ^= value >>> 31;
        return value;
    }

    private static TransitionIntent transitionIntent(
            ElevationField elevation,
            int x,
            int y,
            boolean requireCardinalDominance,
            TransitionPolicy policy) {
        if (!elevation.contains(x, y)) return null;
        TerrainSurfacePatch precise = precisePatch(elevation, x, y);
        if (precise.reliefSubunits() >= policy.maximumRawReliefSubunits()) return null;

        long center = elevation.elevationSubunitsAt(x, y);
        long layer = Math.floorDiv(center, CELL);
        long gradientX = precise.gradientXSubunits();
        long gradientY = precise.gradientYSubunits();
        long absoluteX = absolute(gradientX);
        long absoluteY = absolute(gradientY);
        long dominantGradient = Math.max(absoluteX, absoluteY);
        if (dominantGradient == 0L) return null;

        if (!smoothNeighbor(elevation, x - 1, y, center, layer, policy)
                || !smoothNeighbor(elevation, x + 1, y, center, layer, policy)
                || !smoothNeighbor(elevation, x, y - 1, center, layer, policy)
                || !smoothNeighbor(elevation, x, y + 1, center, layer, policy)) {
            return null;
        }

        int dx;
        int dy;
        if (absoluteX >= absoluteY) {
            if (requireCardinalDominance
                    && absoluteX * policy.dominanceDenominator()
                            < absoluteY * policy.dominanceNumerator()) {
                return null;
            }
            dx = gradientX > 0L ? 1 : -1;
            dy = 0;
        } else {
            if (requireCardinalDominance
                    && absoluteY * policy.dominanceDenominator()
                            < absoluteX * policy.dominanceNumerator()) {
                return null;
            }
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
            TransitionIntent center,
            TransitionPolicy policy) {
        int sideX = -center.dy();
        int sideY = center.dx();
        int width = 1;
        width += contiguousSupport(elevation, x, y, center, sideX, sideY, policy);
        width += contiguousSupport(elevation, x, y, center, -sideX, -sideY, policy);
        return width;
    }

    private static int contiguousSupport(
            ElevationField elevation,
            int x,
            int y,
            TransitionIntent center,
            int stepX,
            int stepY,
            TransitionPolicy policy) {
        int support = 0;
        for (int distance = 1; distance < MIN_COHERENT_BAND_WIDTH; distance++) {
            int neighbourX = x + stepX * distance;
            int neighbourY = y + stepY * distance;
            TransitionIntent neighbour = transitionIntent(
                    elevation,
                    neighbourX,
                    neighbourY,
                    true,
                    policy);
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
            long centerLayer,
            TransitionPolicy policy) {
        if (!elevation.contains(x, y)) return true;
        long neighbor = elevation.elevationSubunitsAt(x, y);
        if (absolute(neighbor - center) > policy.maximumNeighborDeltaSubunits()) return false;
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

    private record TransitionPolicy(
            long maximumNeighborDeltaSubunits,
            long maximumRawReliefSubunits,
            int dominanceNumerator,
            int dominanceDenominator) {
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
