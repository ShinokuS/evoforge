package io.github.evoforge.simulation.world.calibration.soil;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.evoforge.simulation.world.mechanics.measurement.WaterDepthRate;
import java.time.Duration;
import org.junit.jupiter.api.Test;

final class SaxtonRawls2006SoilHydraulicCalibratorTest {

    private final SoilHydraulicCalibrator calibrator =
            new SaxtonRawls2006SoilHydraulicCalibrator();

    @Test
    void calibratesCoarseTextureIntoPhysicalHydraulicFacts() {
        SoilHydraulicProfile result = calibrator.calibrate(
                SoilCompositionProfile.ofPercent(85, 10, 5, 2));

        assertEquals(449_283, result.porosityPartsPerMillion());
        assertEquals(101_858, result.fieldCapacityPartsPerMillion());
        assertEquals(45_026, result.permanentWiltingPointPartsPerMillion());
        assertEquals(
                WaterDepthRate.ofMicrometers(101_472L, Duration.ofHours(1)),
                result.saturatedHydraulicConductivity());
    }

    @Test
    void finerTextureEmergesWithMuchLowerConductivityWithoutATypeBranch() {
        SoilHydraulicProfile coarse = calibrator.calibrate(
                SoilCompositionProfile.ofPercent(85, 10, 5, 2));
        SoilHydraulicProfile fine = calibrator.calibrate(
                SoilCompositionProfile.ofPercent(25, 20, 55, 2));

        assertEquals(503_204, fine.porosityPartsPerMillion());
        assertEquals(442_251, fine.fieldCapacityPartsPerMillion());
        assertEquals(324_736, fine.permanentWiltingPointPartsPerMillion());
        assertEquals(
                WaterDepthRate.ofMicrometers(548L, Duration.ofHours(1)),
                fine.saturatedHydraulicConductivity());
        assertTrue(
                compareRates(
                        coarse.saturatedHydraulicConductivity(),
                        fine.saturatedHydraulicConductivity()) > 0,
                "the empirical algorithm must derive the conductivity contrast from composition");
    }

    private static int compareRates(WaterDepthRate first, WaterDepthRate second) {
        return first.depthNanometersNumerator()
                .multiply(second.durationNanosecondsDenominator())
                .compareTo(second.depthNanometersNumerator()
                        .multiply(first.durationNanosecondsDenominator()));
    }
}
