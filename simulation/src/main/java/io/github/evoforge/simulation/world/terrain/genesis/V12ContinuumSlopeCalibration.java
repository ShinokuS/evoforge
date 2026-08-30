package io.github.evoforge.simulation.world.terrain.genesis;

import io.github.evoforge.simulation.world.terrain.field.TerrainElevationField;

/** Compact calibrated slope facts used by the accepted historical V12 relief pass. */
public record V12ContinuumSlopeCalibration(
        long maximumStepSubunits,
        long maximumLandHeightSubunits) {

    private static final int PPM = 1_000_000;

    public V12ContinuumSlopeCalibration {
        if (maximumStepSubunits <= 0L) {
            throw new IllegalArgumentException("maximumStepSubunits must be > 0");
        }
        if (maximumLandHeightSubunits <= 0L) {
            throw new IllegalArgumentException("maximumLandHeightSubunits must be > 0");
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

        return new V12ContinuumSlopeCalibration(
                maximumStepSubunits,
                maximumLandHeightSubunits);
    }
}
