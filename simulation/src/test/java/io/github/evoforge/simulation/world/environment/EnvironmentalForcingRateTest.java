package io.github.evoforge.simulation.world.environment;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.evoforge.simulation.world.environment.evaporation.EvaporationSchedule;
import io.github.evoforge.simulation.world.environment.precipitation.PrecipitationSchedule;
import io.github.evoforge.simulation.world.mechanics.geometry.CellVolumeRate;
import org.junit.jupiter.api.Test;

final class EnvironmentalForcingRateTest {

    @Test
    void endlessPrecipitationExposesExactMeanRate() {
        PrecipitationSchedule schedule = new PrecipitationSchedule(120_000, 30L);

        assertEquals(new CellVolumeRate(4_000L, 1L), schedule.meanRatePerColumn());
    }

    @Test
    void cyclicPrecipitationUsesActualPulseCountPerCycle() {
        PrecipitationSchedule schedule = PrecipitationSchedule.cyclic(
                120_000,
                30L,
                100L,
                400L);

        // Pulses occur at 30, 60 and 90: 360_000 cell-volume units per 400 ticks.
        assertEquals(new CellVolumeRate(900L, 1L), schedule.meanRatePerColumn());
    }

    @Test
    void evaporationExposesExactMeanPotentialRemovalRate() {
        EvaporationSchedule schedule = new EvaporationSchedule(75_000, 40L);

        assertEquals(new CellVolumeRate(1_875L, 1L), schedule.meanRatePerColumn());
    }
}
