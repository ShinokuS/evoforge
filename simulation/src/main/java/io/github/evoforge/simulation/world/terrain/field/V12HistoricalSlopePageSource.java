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
 * <p>Unit-resolution requests preserve the migration-validated historical implementation: a finite
 * working raster plus a 48-cell halo is filled with the accepted V12 unrelaxed field, then the old
 * alternating forward/reverse in-place cardinal sweeps are executed with the original asymmetric
 * integer split. The requested unit window is cropped from that working raster.
 *
 * <p>Coarse Continuum requests are presentation/resolution queries rather than hidden unit-world
 * materializations. The accepted V12 field is evaluated as one sampled batch on the real-coordinate
 * lattice, then the same directional sweep law is executed with the allowed cardinal step scaled by
 * the world distance between samples. Work follows requested samples, not covered world area.</p>
 *
 * <p>The historical sweep is not mathematically finite-range for arbitrary adversarial rasters.
 * For the accepted V12 relief field, migration profiling across balanced and full-land oracle worlds
 * found that 48 cells reproduced the old whole-world result bit-for-bit in every tested unit window.
 * The value is therefore deliberately named a validated migration halo rather than an exact
 * theoretical radius.</p>
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
        return window.step() == 1L
                ? materializeUnitWindow(window)
                : materializeCoarseWindow(window);
    }

    private ContinuumScalarPage materializeUnitWindow(ContinuumSampleWindow window) {
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
        relaxWorkingRaster(
                elevations,
                haloWidth,
                haloHeight,
                calibration.maximumStepSubunits());

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

    private ContinuumScalarPage materializeCoarseWindow(ContinuumSampleWindow window) {
        int area = Math.multiplyExact(window.width(), window.height());
        long[] elevations = new long[area];
        if (source instanceof V12UnrelaxedLandElevationField acceptedV12) {
            acceptedV12.fillSampleWindow(window, elevations);
        } else {
            int cursor = 0;
            for (int sampleY = 0; sampleY < window.height(); sampleY++) {
                long y = window.yAt(sampleY);
                for (int sampleX = 0; sampleX < window.width(); sampleX++, cursor++) {
                    elevations[cursor] = source.elevationSubunitsAt(window.xAt(sampleX), y);
                }
            }
        }

        long scaledMaximumStep = scaledMaximumStep(window.step());
        relaxWorkingRaster(elevations, window.width(), window.height(), scaledMaximumStep);
        double[] output = new double[area];
        for (int cell = 0; cell < area; cell++) output[cell] = elevations[cell];
        return new ContinuumScalarPage(window, output);
    }

    private long scaledMaximumStep(long step) {
        long maximumStep = calibration.maximumStepSubunits();
        if (maximumStep == 0L || step == 0L) return 0L;
        if (maximumStep > Long.MAX_VALUE / step) {
            return calibration.maximumLandHeightSubunits();
        }
        return Math.min(
                calibration.maximumLandHeightSubunits(),
                maximumStep * step);
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

    private void relaxWorkingRaster(
            long[] elevations,
            int width,
            int height,
            long maximumStep) {
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
                maximumStep,
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
