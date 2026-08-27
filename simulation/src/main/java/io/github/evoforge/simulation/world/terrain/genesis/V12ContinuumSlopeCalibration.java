package io.github.evoforge.simulation.world.terrain.genesis;

import io.github.evoforge.simulation.world.terrain.field.TerrainElevationField;

/**
 * Compact calibrated V12 slope facts shared by Continuum terrain projections.
 *
 * <p>The accepted historical V12 algorithm uses {@link #maximumStepSubunits()} and
 * {@link #maximumLandHeightSubunits()} while its bounded Continuum materializer owns a separately
 * validated migration halo. {@link #exactHaloCells()} is retained only for the alternate symmetric
 * Lipschitz projection: for that mathematical projection the finite height range gives an exact
 * theoretical influence radius. It must not be interpreted as the historical directional-sweep
 * migration radius.</p>
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
