package io.github.evoforge.simulation.world.calibration.soil;

import io.github.evoforge.simulation.world.mechanics.measurement.WaterDepthRate;

/**
 * Algorithm-independent physical hydraulic properties of one soil material.
 *
 * <p>This is preparation-time calibrated data, not a runtime CellVolume/tick contract. Water
 * contents are exact dimensionless fractions in parts per million; saturated hydraulic
 * conductivity remains a physical depth-per-time rate. The current runtime compiler consumes
 * porosity and conductivity, while field capacity and permanent wilting point are retained as
 * durable hydraulic information for later unsaturated-flow, evaporation and ecology models.</p>
 */
public record SoilHydraulicProfile(
        int porosityPartsPerMillion,
        int fieldCapacityPartsPerMillion,
        int permanentWiltingPointPartsPerMillion,
        WaterDepthRate saturatedHydraulicConductivity) {

    public static final int FRACTION_SCALE = 1_000_000;

    public SoilHydraulicProfile {
        requireFraction(porosityPartsPerMillion, "soil porosity");
        requireFraction(fieldCapacityPartsPerMillion, "soil field capacity");
        requireFraction(permanentWiltingPointPartsPerMillion, "soil permanent wilting point");
        if (permanentWiltingPointPartsPerMillion > fieldCapacityPartsPerMillion) {
            throw new IllegalArgumentException(
                    "permanent wilting point must not exceed field capacity");
        }
        if (fieldCapacityPartsPerMillion > porosityPartsPerMillion) {
            throw new IllegalArgumentException("field capacity must not exceed porosity");
        }
        if (saturatedHydraulicConductivity == null) {
            throw new IllegalArgumentException(
                    "saturated hydraulic conductivity must not be null");
        }
    }

    public static SoilHydraulicProfile ofPercent(
            int porosityPercent,
            int fieldCapacityPercent,
            int permanentWiltingPointPercent,
            WaterDepthRate saturatedHydraulicConductivity) {
        return new SoilHydraulicProfile(
                percentToPartsPerMillion(porosityPercent, "porosity"),
                percentToPartsPerMillion(fieldCapacityPercent, "field capacity"),
                percentToPartsPerMillion(permanentWiltingPointPercent, "permanent wilting point"),
                saturatedHydraulicConductivity);
    }

    private static int percentToPartsPerMillion(int percent, String label) {
        if (percent < 0 || percent > 100) {
            throw new IllegalArgumentException(label + " percent must be within 0..100");
        }
        return Math.multiplyExact(percent, 10_000);
    }

    private static void requireFraction(int value, String label) {
        if (value < 0 || value > FRACTION_SCALE) {
            throw new IllegalArgumentException(
                    label + " must be within 0..1_000_000 parts per million");
        }
    }
}
