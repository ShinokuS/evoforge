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
 * the requested samples, then discards the working raster.</p>
 *
 * <p>The historical sweep is not mathematically finite-range for arbitrary adversarial rasters.
 * For the accepted V12 relief field, migration profiling across balanced and full-land oracle worlds
 * found that 48 cells reproduced the old whole-world result bit-for-bit in every tested window. The
 * value is therefore deliberately named a validated migration halo rather than an exact theoretical
 * radius.</p>
 */
public final class V12HistoricalSlopePageSource implements ContinuumScalarPageSource {
    public static final int VALIDATED_HALO_CELLS = 48;

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
        boolean[] land = new boolean[haloArea];
        int cursor = 0;
        for (int y = 0; y < haloHeight; y++) {
            long worldY = haloMinY + y;
            for (int x = 0; x < haloWidth; x++, cursor++) {
                long value = source.elevationSubunitsAt(haloMinX + x, worldY);
                if (value > calibration.maximumLandHeightSubunits()) {
                    throw new IllegalStateException(
                            "V12 source height exceeds calibrated land-height bound");
                }
                elevations[cursor] = value;
                land[cursor] = value > 0L;
            }
        }

        historicalDirectionalRelax(
                elevations,
                land,
                haloWidth,
                haloHeight,
                calibration.maximumStepSubunits(),
                calibration.maximumLandHeightSubunits(),
                relaxationPasses);

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
