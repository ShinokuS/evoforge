package io.github.evoforge.simulation.world.calibration.soil;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.evoforge.simulation.time.SimulationTimeScale;
import io.github.evoforge.simulation.world.landscape.soil.SoilProperties;
import io.github.evoforge.simulation.world.mechanics.measurement.WaterDepthRate;
import io.github.evoforge.simulation.world.scale.PhysicalSpaceScale;
import java.time.Duration;
import org.junit.jupiter.api.Test;

final class SoilHydraulicRuntimeCompilerTest {

    private static final PhysicalSpaceScale ONE_METER_CELL =
            PhysicalSpaceScale.cubicMillimeters(1_000L);
    private static final SimulationTimeScale THIRTY_MINUTE_TICK =
            SimulationTimeScale.of(Duration.ofMinutes(30));

    @Test
    void compilesPorosityAndConductivityFromPhysicalScale() {
        SoilHydraulicProfile profile = profile(
                450_000,
                WaterDepthRate.ofMillimeters(8L, Duration.ofHours(1)));

        SoilProperties compiled = SoilHydraulicRuntimeCompiler.compile(
                profile,
                ONE_METER_CELL,
                THIRTY_MINUTE_TICK);

        assertEquals(450_000, compiled.capacity());
        assertEquals(4_000, compiled.permeability());
    }

    @Test
    void verticalCellHeightChangesNormalizedConductivityButHorizontalAreaCancels() {
        SoilHydraulicProfile profile = profile(
                400_000,
                WaterDepthRate.ofMillimeters(8L, Duration.ofHours(1)));

        SoilProperties wideOneMeterTall = SoilHydraulicRuntimeCompiler.compile(
                profile,
                new PhysicalSpaceScale(2_000L, 1_000L),
                THIRTY_MINUTE_TICK);
        SoilProperties wideTwoMeterTall = SoilHydraulicRuntimeCompiler.compile(
                profile,
                new PhysicalSpaceScale(2_000L, 2_000L),
                THIRTY_MINUTE_TICK);

        assertEquals(4_000, wideOneMeterTall.permeability());
        assertEquals(2_000, wideTwoMeterTall.permeability());
    }

    @Test
    void rejectsFractionalRuntimeInfiltrationInsteadOfRounding() {
        SoilHydraulicProfile profile = profile(
                450_000,
                WaterDepthRate.ofNanometers(1L, Duration.ofHours(1)));

        assertThrows(
                IllegalArgumentException.class,
                () -> SoilHydraulicRuntimeCompiler.compile(
                        profile,
                        ONE_METER_CELL,
                        SimulationTimeScale.of(Duration.ofSeconds(1))));
    }

    @Test
    void validatesPhysicalRetentionOrdering() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new SoilHydraulicProfile(
                        -1, 0, 0, WaterDepthRate.ZERO));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SoilHydraulicProfile(
                        450_000, 460_000, 100_000, WaterDepthRate.ZERO));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SoilHydraulicProfile(
                        450_000, 300_000, 310_000, WaterDepthRate.ZERO));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SoilHydraulicProfile(
                        450_000, 300_000, 100_000, null));
    }

    private static SoilHydraulicProfile profile(
            int porosity,
            WaterDepthRate conductivity) {
        return new SoilHydraulicProfile(
                porosity,
                porosity / 2,
                porosity / 4,
                conductivity);
    }
}
