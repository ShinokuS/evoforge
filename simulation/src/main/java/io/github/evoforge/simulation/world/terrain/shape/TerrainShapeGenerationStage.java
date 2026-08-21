package io.github.evoforge.simulation.world.terrain.shape;

import io.github.evoforge.simulation.world.atlas.ElevationField;
import io.github.evoforge.simulation.world.genesis.GenerationRevision;
import io.github.evoforge.simulation.world.mechanics.geometry.Shape;
import io.github.evoforge.simulation.world.spatial.WorldBounds;
import java.util.List;

/**
 * Fits generated elevation targets to the available material-agnostic surface templates.
 *
 * <p>The algorithm never branches on a concrete Shape. It compares only surface geometry; the
 * selected template carries an opaque runtime Shape override for later materialization. Abrupt or
 * poorly represented terrain falls back to ordinary full-cell geometry instead of forcing access.</p>
 */
public final class TerrainShapeGenerationStage implements TerrainShapeGenerator {
    private final TerrainShapePalette palette;
    private final TerrainShapeCalibration calibration;
    private final TerrainSurfaceTargetSampler targetSampler;

    public TerrainShapeGenerationStage(
            TerrainShapePalette palette,
            TerrainShapeCalibration calibration) {
        this(palette, calibration, TerrainSurfaceTargetSampler.precise());
    }

    public TerrainShapeGenerationStage(
            TerrainShapePalette palette,
            TerrainShapeCalibration calibration,
            TerrainSurfaceTargetSampler targetSampler) {
        if (palette == null || calibration == null || targetSampler == null) {
            throw new IllegalArgumentException("terrain shape generation dependencies must not be null");
        }
        this.palette = palette;
        this.calibration = calibration;
        this.targetSampler = targetSampler;
    }

    /** Stable original precise fitting policy used by pre-V11 generated worlds and direct tests. */
    public static TerrainShapeGenerationStage standard() {
        return new TerrainShapeGenerationStage(
                TerrainShapePalette.standard(),
                TerrainShapeCalibration.representative(),
                TerrainSurfaceTargetSampler.precise());
    }

    /** Revision-aware generated-world compiler policy; Shape identity never participates. */
    public static TerrainShapeGenerationStage forRevision(GenerationRevision revision) {
        if (revision == null) throw new IllegalArgumentException("generation revision must not be null");
        return new TerrainShapeGenerationStage(
                TerrainShapePalette.standard(),
                TerrainShapeCalibration.representative(),
                targetsForRevision(revision, TerrainSurfaceTargetSampler.precise()));
    }

    /** Uses this stage's palette/calibration while selecting only the revision-specific target law. */
    @Override
    public TerrainShapeField generate(GenerationRevision revision, ElevationField elevation) {
        if (revision == null) throw new IllegalArgumentException("generation revision must not be null");
        TerrainSurfaceTargetSampler targets = targetsForRevision(revision, targetSampler);
        if (targets == targetSampler) return generate(elevation);
        return new TerrainShapeGenerationStage(palette, calibration, targets).generate(elevation);
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
                TerrainSurfacePatch target = targetSampler.sample(elevation, x, y);
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

    private static TerrainSurfaceTargetSampler targetsForRevision(
            GenerationRevision revision,
            TerrainSurfaceTargetSampler fallback) {
        if (GenerationRevision.V15.equals(revision)
                || GenerationRevision.V14.equals(revision)
                || GenerationRevision.V13.equals(revision)) {
            return TerrainSurfaceTargetSampler.sparseCoherentVoxelTransitions();
        }
        if (GenerationRevision.V12.equals(revision)) {
            return TerrainSurfaceTargetSampler.coherentVoxelTransitions();
        }
        if (GenerationRevision.V11.equals(revision)) {
            return TerrainSurfaceTargetSampler.smoothVoxelTransitions();
        }
        return fallback;
    }

    private static long absoluteDifference(long first, long second) {
        long difference = Math.subtractExact(first, second);
        if (difference == Long.MIN_VALUE) {
            throw new ArithmeticException("surface difference exceeds signed range");
        }
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
