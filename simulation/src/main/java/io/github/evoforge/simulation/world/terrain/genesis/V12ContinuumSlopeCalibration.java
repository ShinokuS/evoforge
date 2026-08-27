package io.github.evoforge.simulation.world.terrain.genesis;

import io.github.evoforge.simulation.world.terrain.field.TerrainElevationField;

/**
 * Compact operating facts for the Continuum replacement of legacy V12 directional slope sweeps.
 *
 * <p>The old implementation used four in-place whole-raster scan passes. That traversal is
 * intentionally not reproduced because one pass can propagate a correction across an arbitrarily
 * large connected land component. The Continuum implementation preserves the authored maximum
 * cardinal slope through a bounded Lipschitz projection instead.</p>
 */
public record V12ContinuumSlopeCalibration(
        long maximumStepSubunits,
        long maximumLandHeightSubunits,
        int exactHaloCells) {

    private static final int PPM = 1_000_000;

    public V12ContinuumSlopeCalibration {
        if (maximumStepSubunits <= 0L) {
            throw new IllegalArgumentException("maximumStepSubunits must be > 0");
        }
        if (maximumLandHeightSubunits <= 0L) {
            throw new IllegalArgumentException("maximumLandHeightSubunits must be > 0");
        }
        if (exactHaloCells < 0) {
            throw new IllegalArgumentException("exactHaloCells must be >= 0");
        }
    }

    public static V12ContinuumSlopeCalibration compile(
            V12TerrainCalibration terrain,
            V12TerrainRecipe recipe,
            int maximumLandHeightCells) {
        if (terrain == null || recipe == null) {
            throw new IllegalArgumentException("V12 slope calibration inputs must not be null");
        }
        if (maximumLandHeightCells <= 0) {
            throw new IllegalArgumentException("maximumLandHeightCells must be > 0");
        }

        int stepPpm = recipe.minimumStepPpm()
                + (int) ((long) terrain.ruggednessPpm()
                        * (recipe.maximumStepPpm() - recipe.minimumStepPpm()) / PPM);
        long maximumStepSubunits = Math.max(
                1L,
                TerrainElevationField.SUBUNITS_PER_CELL * (long) stepPpm / PPM);
        long maximumLandHeightSubunits = Math.multiplyExact(
                (long) maximumLandHeightCells,
                TerrainElevationField.SUBUNITS_PER_CELL);
        long verticalRange = maximumLandHeightSubunits - 1L;
        int exactHaloCells = Math.toIntExact(verticalRange / maximumStepSubunits);

        return new V12ContinuumSlopeCalibration(
                maximumStepSubunits,
                maximumLandHeightSubunits,
                exactHaloCells);
    }
}
