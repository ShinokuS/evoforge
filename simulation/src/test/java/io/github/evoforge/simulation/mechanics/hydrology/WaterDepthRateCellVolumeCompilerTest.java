package io.github.evoforge.simulation.mechanics.hydrology;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.evoforge.simulation.kernel.time.SimulationTimeScale;
import io.github.evoforge.simulation.world.space.measurement.CellVolumeRate;
import io.github.evoforge.simulation.world.space.measurement.PhysicalSpaceScale;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import io.github.evoforge.simulation.world.atmosphere.WaterDepthRate;

final class WaterDepthRateCellVolumeCompilerTest {

    @Test
    void oneMillimeterPerSecondOccupiesOneThousandthOfOneMeterTallCellPerSecond() {
        CellVolumeRate compiled = WaterDepthRateCellVolumeCompiler.compile(
                WaterDepthRate.ofMillimeters(1L, Duration.ofSeconds(1L)),
                PhysicalSpaceScale.cubicMillimeters(1_000L),
                SimulationTimeScale.of(Duration.ofSeconds(1L)));

        assertEquals(CellVolumeRate.of(1_000L, 1L), compiled);
    }

    @Test
    void tickDurationAndVerticalCellHeightChangeOnlyRuntimeDiscretization() {
        WaterDepthRate climate = WaterDepthRate.ofMillimeters(1L, Duration.ofSeconds(1L));

        assertEquals(
                CellVolumeRate.of(250L, 1L),
                WaterDepthRateCellVolumeCompiler.compile(
                        climate,
                        PhysicalSpaceScale.cubicMillimeters(1_000L),
                        SimulationTimeScale.of(Duration.ofMillis(250L))));
        assertEquals(
                CellVolumeRate.of(2_000L, 1L),
                WaterDepthRateCellVolumeCompiler.compile(
                        climate,
                        new PhysicalSpaceScale(1_000L, 500L),
                        SimulationTimeScale.of(Duration.ofSeconds(1L))));
    }

    @Test
    void horizontalCellAreaCancelsForUniformSurfaceDepth() {
        WaterDepthRate climate = WaterDepthRate.ofMillimeters(1L, Duration.ofSeconds(1L));
        SimulationTimeScale oneSecond = SimulationTimeScale.of(Duration.ofSeconds(1L));

        assertEquals(
                WaterDepthRateCellVolumeCompiler.compile(
                        climate,
                        new PhysicalSpaceScale(1_000L, 1_000L),
                        oneSecond),
                WaterDepthRateCellVolumeCompiler.compile(
                        climate,
                        new PhysicalSpaceScale(2_000L, 1_000L),
                        oneSecond));
    }
}
