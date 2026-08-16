package io.github.evoforge.simulation.world.warmup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.evoforge.simulation.world.bootstrap.GeneratedWorldRuntime;
import io.github.evoforge.simulation.world.diagnostics.GeneratedWorldDiagnostics;
import io.github.evoforge.simulation.world.genesis.HydroClimateSpec;

final class GeneratedWorldWarmupTest {

    @Test
    void capturesRequestedAbsoluteCheckpointsThroughOrdinaryRuntimeStepper() {
        GeneratedWorldRuntime world = GeneratedWorldWarmupFixture.create(
                42L,
                HydroClimateSpec.UNFORCED);

        List<GeneratedWorldDiagnostics> snapshots =
                new GeneratedWorldWarmup().run(world, 0L, 3L, 7L);

        assertEquals(List.of(0L, 3L, 7L), snapshots.stream()
                .map(GeneratedWorldDiagnostics::tick)
                .toList());
        assertEquals(7L, world.runtime().time().tick());
        assertTrue(snapshots.stream()
                .allMatch(GeneratedWorldDiagnostics::surfaceMatchesAtlas));
        assertTrue(snapshots.stream()
                .allMatch(snapshot -> snapshot.totalWaterVolume() == 0L));
    }

    @Test
    void rejectsInvalidOrPastCheckpointSequences() {
        GeneratedWorldWarmup warmup = new GeneratedWorldWarmup();

        assertThrows(
                IllegalArgumentException.class,
                () -> warmup.run(
                        GeneratedWorldWarmupFixture.create(1L, HydroClimateSpec.UNFORCED)));
        assertThrows(
                IllegalArgumentException.class,
                () -> warmup.run(
                        GeneratedWorldWarmupFixture.create(1L, HydroClimateSpec.UNFORCED),
                        -1L));
        assertThrows(
                IllegalArgumentException.class,
                () -> warmup.run(
                        GeneratedWorldWarmupFixture.create(1L, HydroClimateSpec.UNFORCED),
                        0L, 0L));
        assertThrows(
                IllegalArgumentException.class,
                () -> warmup.run(
                        GeneratedWorldWarmupFixture.create(1L, HydroClimateSpec.UNFORCED),
                        2L, 1L));

        GeneratedWorldRuntime advanced = GeneratedWorldWarmupFixture.create(
                1L,
                HydroClimateSpec.UNFORCED);
        advanced.runtime().stepper().advance();
        assertThrows(
                IllegalArgumentException.class,
                () -> warmup.run(advanced, 0L));
    }
}
