package io.github.evoforge.simulation.world.terrain.field;

import io.github.evoforge.simulation.world.continuum.field.ContinuumSampleWindow;
import io.github.evoforge.simulation.world.continuum.field.ContinuumScalarPage;
import io.github.evoforge.simulation.world.continuum.field.ContinuumScalarPageSource;
import io.github.evoforge.simulation.world.continuum.model.ContinuumWorldDomain;
import io.github.evoforge.simulation.world.terrain.genesis.V12ContinuumSlopeCalibration;
import io.github.evoforge.simulation.world.terrain.genesis.V12TerrainRecipe;

/**
 * Bounded materialization of the accepted historical V12 directional slope relaxation.
 *
 * <p>The arithmetic and traversal order inside the working raster are the historical V12 algorithm:
 * alternating forward/reverse in-place cardinal sweeps and the original asymmetric integer split of
 * every excess step. The only Continuum adaptation is representation: a request materializes a
 * finite unit-resolution working raster around the requested window, performs the old sweeps, crops
 * the requested samples, then discards the working raster.
 *
 * <p>When the accepted V12 unrelaxed source is available directly, the halo is filled through its
 * bounded batch path. That preserves every authored value while reusing local land-membership
 * decisions across neighboring cells instead of repeatedly evaluating the same coast/rank queries.
 * Synthetic migration fixtures continue to use the generic point-source path unchanged.
 *
 * <p>Sparse coarse requests are costed before materialization. Nearby samples still share one dense
 * historical working raster, while a request whose sample envelope would be much larger than the sum
 * of independent validated halos is evaluated sample-by-sample. This prevents overview/LOD sampling
 * from turning a handful of distant probes into an O(world-area) raster without changing unit-window
 * behavior or the validated local V12 arithmetic.
 *
 * <p>The historical sweep is not mathematically finite-range for arbitrary adversarial rasters.
 * For the accepted V12 relief field, migration profiling across balanced and full-land oracle worlds
 * found that 48 cells reproduced the old whole-world result bit-for-bit in every tested window. The
 * value is therefore deliberately named a validated migration halo rather than an exact theoretical
 * radius.
 */
public final class V12HistoricalSlopePageSource implements ContinuumScalarPageSource {
    public static final int VALIDATED_HALO_CELLS = 48;
    private static final long MAX_DENSE_SPARSE_ENVELOPE_CELLS = 1_048_576L;
    private static final long SINGLE_SAMPLE_HALO_CELLS =
            (2L * VALIDATED_HALO_CELLS + 1L) * (2L * VALIDATED_HALO_CELLS + 1L);

    private final ContinuumWorldDomain domain;
    private final TerrainElevationField source;
    private final V12ContinuumSlopeCalibration calibration;
    private final int relaxationPasses;

    public V12HistoricalSlopePageSource(
            ContinuumWorldDomain domain,
            TerrainElevationField source,
            V12ContinuumSlopeCalibration calibration,
            V12TerrainRecipe recipe) {
        if (domain == null || source == null || calibration == null || recipe == null) {
            throw new IllegalArgumentException("historical V12 slope page-source inputs must not be null");
        }
        if (recipe.relaxationPasses() < 0) {
            throw new IllegalArgumentException("V12 relaxationPasses must be >= 0");
        }
        this.domain = domain;
        this.source = source;
        this.calibration = calibration;
        this.relaxationPasses = recipe.relaxationPasses();
    }

    @Override
    public ContinuumWorldDomain domain() {
        return domain;
    }

    public int migrationHaloCells() {
        return VALIDATED_HALO_CELLS;
    }

    public int relaxationPasses() {
        return relaxationPasses;
    }

    public long maximumStepSubunits() {
        return calibration.maximumStepSubunits();
    }

    @Override
    public ContinuumScalarPage materialize(ContinuumSampleWindow window) {
        validateWindow(window);
        if (window.step() == 1L || shouldShareDenseEnvelope(window)) {
            return materializeDenseEnvelope(window);
        }
        return materializeSparseSamples(window);
    }

    private boolean shouldShareDenseEnvelope(ContinuumSampleWindow window) {
        long requestedMaxX = window.xAt(window.width() - 1);
        long requestedMaxY = window.yAt(window.height() - 1);
        long haloMinX = Math.max(0L, window.minX() - VALIDATED_HALO_CELLS);
        long haloMinY = Math.max(0L, window.minY() - VALIDATED_HALO_CELLS);
        long haloMaxX = Math.min(domain.width() - 1L, requestedMaxX + VALIDATED_HALO_CELLS);
        long haloMaxY = Math.min(domain.height() - 1L, requestedMaxY + VALIDATED_HALO_CELLS);
        long haloWidth = haloMaxX - haloMinX + 1L;
        long haloHeight = haloMaxY - haloMinY + 1L;
        long denseCells = saturatedMultiply(haloWidth, haloHeight);
        long samples = Math.multiplyExact((long) window.width(), window.height());
        long independentCells = saturatedMultiply(samples, SINGLE_SAMPLE_HALO_CELLS);
        return denseCells <= MAX_DENSE_SPARSE_ENVELOPE_CELLS && denseCells <= independentCells;
    }

    private ContinuumScalarPage materializeDenseEnvelope(ContinuumSampleWindow window) {
        long requestedMaxX = window.xAt(window.width() - 1);
        long requestedMaxY = window.yAt(window.height() - 1);
        long haloMinX = Math.max(0L, window.minX() - VALIDATED_HALO_CELLS);
        long haloMinY = Math.max(0L, window.minY() - VALIDATED_HALO_CELLS);
        long haloMaxX = Math.min(domain.width() - 1L, requestedMaxX + VALIDATED_HALO_CELLS);
        long haloMaxY = Math.min(domain.height() - 1L, requestedMaxY + VALIDATED_HALO_CELLS);
        int haloWidth = Math.toIntExact(haloMaxX - haloMinX + 1L);
        int haloHeight = Math.toIntExact(haloMaxY - haloMinY + 1L);
        int haloArea = Math.multiplyExact(haloWidth, haloHeight);

        long[] elevations = new long[haloArea];
        fillSourceWindow(haloMinX, haloMinY, haloWidth, haloHeight, elevations);
        relaxWorkingRaster(elevations, haloWidth, haloHeight);

        double[] output = new double[Math.multiplyExact(window.width(), window.height())];
        int sample = 0;
        for (int y = 0; y < window.height(); y++) {
            int localY = Math.toIntExact(window.yAt(y) - haloMinY);
            for (int x = 0; x < window.width(); x++, sample++) {
                int localX = Math.toIntExact(window.xAt(x) - haloMinX);
                output[sample] = elevations[localY * haloWidth + localX];
            }
        }
        return new ContinuumScalarPage(window, output);
    }

    private ContinuumScalarPage materializeSparseSamples(ContinuumSampleWindow window) {
        double[] output = new double[Math.multiplyExact(window.width(), window.height())];
        int cursor = 0;
        for (int sampleY = 0; sampleY < window.height(); sampleY++) {
            long y = window.yAt(sampleY);
            for (int sampleX = 0; sampleX < window.width(); sampleX++, cursor++) {
                output[cursor] = materializeSingleSample(window.xAt(sampleX), y);
            }
        }
        return new ContinuumScalarPage(window, output);
    }

    private long materializeSingleSample(long x, long y) {
        long haloMinX = Math.max(0L, x - VALIDATED_HALO_CELLS);
        long haloMinY = Math.max(0L, y - VALIDATED_HALO_CELLS);
        long haloMaxX = Math.min(domain.width() - 1L, x + VALIDATED_HALO_CELLS);
        long haloMaxY = Math.min(domain.height() - 1L, y + VALIDATED_HALO_CELLS);
        int haloWidth = Math.toIntExact(haloMaxX - haloMinX + 1L);
        int haloHeight = Math.toIntExact(haloMaxY - haloMinY + 1L);
        long[] elevations = new long[Math.multiplyExact(haloWidth, haloHeight)];
        fillSourceWindow(haloMinX, haloMinY, haloWidth, haloHeight, elevations);
        relaxWorkingRaster(elevations, haloWidth, haloHeight);
        int localX = Math.toIntExact(x - haloMinX);
        int localY = Math.toIntExact(y - haloMinY);
        return elevations[localY * haloWidth + localX];
    }

    private void fillSourceWindow(
            long minX,
            long minY,
            int width,
            int height,
            long[] elevations) {
        if (source instanceof V12UnrelaxedLandElevationField acceptedV12) {
            acceptedV12.fillWindow(minX, minY, width, height, elevations);
        } else {
            fillFromPointSource(minX, minY, width, height, elevations);
        }
    }

    private void relaxWorkingRaster(long[] elevations, int width, int height) {
        int area = Math.multiplyExact(width, height);
        boolean[] land = new boolean[area];
        for (int cell = 0; cell < area; cell++) {
            long value = elevations[cell];
            if (value > calibration.maximumLandHeightSubunits()) {
                throw new IllegalStateException(
                        "V12 source height exceeds calibrated land-height bound");
            }
            land[cell] = value > 0L;
        }

        historicalDirectionalRelax(
                elevations,
                land,
                width,
                height,
                calibration.maximumStepSubunits(),
                calibration.maximumLandHeightSubunits(),
                relaxationPasses);
    }

    private void fillFromPointSource(
            long minX,
            long minY,
            int width,
            int height,
            long[] elevations) {
        int cursor = 0;
        for (int y = 0; y < height; y++) {
            long worldY = minY + y;
            for (int x = 0; x < width; x++, cursor++) {
                elevations[cursor] = source.elevationSubunitsAt(minX + x, worldY);
            }
        }
    }

    private static void historicalDirectionalRelax(
            long[] elevations,
            boolean[] land,
            int width,
            int height,
            long maximumStep,
            long maximumHeight,
            int passes) {
        for (int pass = 0; pass < passes; pass++) {
            boolean reverse = (pass & 1) != 0;
            if (!reverse) {
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        int cell = y * width + x;
                        if (!land[cell]) continue;
                        if (x + 1 < width) {
                            relaxPair(elevations, land, cell, cell + 1, maximumStep, maximumHeight);
                        }
                        if (y + 1 < height) {
                            relaxPair(elevations, land, cell, cell + width, maximumStep, maximumHeight);
                        }
                    }
                }
            } else {
                for (int y = height - 1; y >= 0; y--) {
                    for (int x = width - 1; x >= 0; x--) {
                        int cell = y * width + x;
                        if (!land[cell]) continue;
                        if (x > 0) {
                            relaxPair(elevations, land, cell, cell - 1, maximumStep, maximumHeight);
                        }
                        if (y > 0) {
                            relaxPair(elevations, land, cell, cell - width, maximumStep, maximumHeight);
                        }
                    }
                }
            }
        }
    }

    private static void relaxPair(
            long[] elevations,
            boolean[] land,
            int first,
            int second,
            long maximumStep,
            long maximumHeight) {
        if (!land[first] || !land[second]) return;
        long difference = elevations[first] - elevations[second];
        long magnitude = Math.abs(difference);
        if (magnitude <= maximumStep) return;

        long excess = magnitude - maximumStep;
        long firstCorrection = (excess + 1L) / 2L;
        long secondCorrection = excess - firstCorrection;
        if (difference > 0L) {
            elevations[first] = clampLandHeight(elevations[first] - firstCorrection, maximumHeight);
            elevations[second] = clampLandHeight(elevations[second] + secondCorrection, maximumHeight);
        } else {
            elevations[first] = clampLandHeight(elevations[first] + firstCorrection, maximumHeight);
            elevations[second] = clampLandHeight(elevations[second] - secondCorrection, maximumHeight);
        }
    }

    private static long clampLandHeight(long value, long maximumHeight) {
        return Math.max(1L, Math.min(maximumHeight, value));
    }

    private static long saturatedMultiply(long first, long second) {
        if (first <= 0L || second <= 0L) return 0L;
        if (first > Long.MAX_VALUE / second) return Long.MAX_VALUE;
        return first * second;
    }

    private void validateWindow(ContinuumSampleWindow window) {
        if (window == null) {
            throw new IllegalArgumentException("window must not be null");
        }
        long maxX = window.xAt(window.width() - 1);
        long maxY = window.yAt(window.height() - 1);
        if (!domain.contains(window.minX(), window.minY()) || !domain.contains(maxX, maxY)) {
            throw new IllegalArgumentException("window lies outside the historical V12 slope domain");
        }
    }
}
