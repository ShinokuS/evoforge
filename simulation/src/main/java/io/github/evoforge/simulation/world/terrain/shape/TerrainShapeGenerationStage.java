package io.github.evoforge.simulation.world.terrain.shape;

import io.github.evoforge.simulation.world.atlas.ElevationField;
import io.github.evoforge.simulation.world.mechanics.geometry.Shape;
import io.github.evoforge.simulation.world.spatial.WorldBounds;
import java.util.List;

/**
 * Fits precise generated elevation to the available material-agnostic surface templates.
 *
 * <p>The algorithm never branches on a concrete Shape. It compares only surface geometry; the
 * selected template carries an opaque runtime Shape override for later materialization. Abrupt or
 * poorly represented terrain falls back to ordinary full-cell geometry instead of forcing access.</p>
 */
public final class TerrainShapeGenerationStage implements TerrainShapeGenerator {
    private final TerrainShapePalette palette;
    private final TerrainShapeCalibration calibration;

    public TerrainShapeGenerationStage(
            TerrainShapePalette palette,
            TerrainShapeCalibration calibration) {
        if (palette == null || calibration == null) {
            throw new IllegalArgumentException("terrain shape generation dependencies must not be null");
        }
        this.palette = palette;
        this.calibration = calibration;
    }

    public static TerrainShapeGenerationStage standard() {
        return new TerrainShapeGenerationStage(
                TerrainShapePalette.standard(),
                TerrainShapeCalibration.representative());
    }

    @Override
    public TerrainShapeField generate(ElevationField elevation) {
        if (elevation == null) throw new IllegalArgumentException("elevation must not be null");
        WorldBounds bounds = elevation.bounds();
        int width = Math.toIntExact((long) bounds.maxX() - bounds.minX() + 1L);
        int length = Math.toIntExact((long) bounds.maxY() - bounds.minY() + 1L);
        int area = Math.toIntExact(Math.multiplyExact((long) width, length));
        List<TerrainShapeTemplate> templates = palette.templates();
        byte[] selected = new byte[area];
        long overrides = 0L;

        int index = 0;
        for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
            for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                TerrainSurfacePatch target = targetPatch(elevation, x, y);
                int selectedIndex = bestTemplate(target, templates);
                selected[index++] = (byte) selectedIndex;
                if (templates.get(selectedIndex).shapeOverride().isPresent()) overrides++;
            }
        }
        return new DenseTerrainShapeField(bounds, width, templates, selected, overrides);
    }

    private int bestTemplate(
            TerrainSurfacePatch target,
            List<TerrainShapeTemplate> templates) {
        TerrainSurfacePatch baselineSurface = templates.get(0).surface();
        long baselineError = target.meanAbsoluteError(baselineSurface);
        long targetRelief = target.reliefSubunits();
        int best = 0;
        long bestError = baselineError;

        for (int index = 1; index < templates.size(); index++) {
            TerrainSurfacePatch candidate = templates.get(index).surface();
            long reliefError = absoluteDifference(targetRelief, candidate.reliefSubunits());
            if (reliefError > calibration.maximumReliefErrorSubunits()) continue;

            long error = target.meanAbsoluteError(candidate);
            if (error > calibration.maximumMeanEdgeErrorSubunits()) continue;
            if (baselineError - error < calibration.minimumMeanErrorImprovementSubunits()) continue;
            if (error < bestError) {
                best = index;
                bestError = error;
            }
        }
        return best;
    }

    private static TerrainSurfacePatch targetPatch(ElevationField elevation, int x, int y) {
        long center = elevation.elevationSubunitsAt(x, y);
        long cell = ElevationField.SUBUNITS_PER_CELL;
        long base = Math.multiplyExact(Math.floorDiv(center, cell), cell);
        return new TerrainSurfacePatch(
                boundaryHeight(elevation, x, y, x - 1L, y, center) - base,
                boundaryHeight(elevation, x, y, x + 1L, y, center) - base,
                boundaryHeight(elevation, x, y, x, y - 1L, center) - base,
                boundaryHeight(elevation, x, y, x, y + 1L, center) - base);
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

    private static long absoluteDifference(long first, long second) {
        long difference = Math.subtractExact(first, second);
        if (difference == Long.MIN_VALUE) throw new ArithmeticException("surface difference exceeds signed range");
        return Math.abs(difference);
    }

    private static final class DenseTerrainShapeField implements TerrainShapeField {
        private final WorldBounds bounds;
        private final int width;
        private final List<TerrainShapeTemplate> templates;
        private final byte[] selected;
        private final long overrideCount;

        private DenseTerrainShapeField(
                WorldBounds bounds,
                int width,
                List<TerrainShapeTemplate> templates,
                byte[] selected,
                long overrideCount) {
            this.bounds = bounds;
            this.width = width;
            this.templates = templates;
            this.selected = selected;
            this.overrideCount = overrideCount;
        }

        @Override public WorldBounds bounds() { return bounds; }

        @Override
        public TerrainSurfacePatch surfaceAt(int x, int y) {
            return templateAt(x, y).surface();
        }

        @Override
        public Shape shapeOverrideAt(int x, int y) {
            return templateAt(x, y).shapeOverride().orElse(null);
        }

        @Override public long overrideCount() { return overrideCount; }

        private TerrainShapeTemplate templateAt(int x, int y) {
            if (x < bounds.minX() || x > bounds.maxX()
                    || y < bounds.minY() || y > bounds.maxY()) {
                throw new IllegalArgumentException(
                        "terrain shape coordinate outside world bounds: (" + x + ", " + y + ")");
            }
            int cell = (y - bounds.minY()) * width + (x - bounds.minX());
            return templates.get(Byte.toUnsignedInt(selected[cell]));
        }
    }
}
