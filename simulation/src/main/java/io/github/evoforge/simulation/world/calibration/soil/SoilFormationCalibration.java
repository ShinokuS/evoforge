package io.github.evoforge.simulation.world.calibration.soil;

import io.github.evoforge.simulation.definition.NormalizedValue;
import io.github.evoforge.simulation.world.atlas.ElevationField;

/**
 * Explicit parameters for the first geomorphic Soil-formation model.
 *
 * <p>These values belong to the replaceable model rather than authored material Definitions or
 * runtime mechanics. Characteristic morphology scales feed smooth saturating responses; the
 * normalized fineness shift bounds how far local geomorphic history may move a material away from
 * its authored archetype.</p>
 */
public record SoilFormationCalibration(
        long convexityCharacteristicSubunits,
        long concavityCharacteristicSubunits,
        NormalizedValue maximumMineralFinenessShift) {

    public SoilFormationCalibration {
        if (convexityCharacteristicSubunits <= 0L || concavityCharacteristicSubunits <= 0L) {
            throw new IllegalArgumentException(
                    "soil formation characteristic morphology scales must be positive");
        }
        if (maximumMineralFinenessShift == null) {
            throw new IllegalArgumentException(
                    "maximum mineral fineness shift must not be null");
        }
    }

    /** Conservative first-world calibration; callers may inject another model calibration. */
    public static SoilFormationCalibration representative() {
        return new SoilFormationCalibration(
                ElevationField.SUBUNITS_PER_CELL,
                ElevationField.SUBUNITS_PER_CELL,
                NormalizedValue.ofPartsPerMillion(200_000));
    }
}
