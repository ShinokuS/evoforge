package io.github.evoforge.simulation.world.diagnostics.warmup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.evoforge.simulation.world.bootstrap.AtmosphericForcingPolicy;
import io.github.evoforge.simulation.world.bootstrap.GeneratedWorldRuntime;
import io.github.evoforge.simulation.world.climate.ClimateTemperature;
import io.github.evoforge.simulation.world.diagnostics.GeneratedWorldDiagnostics;
import io.github.evoforge.simulation.world.genesis.ClimateSpec;
import io.github.evoforge.simulation.world.mechanics.geometry.CellVolumeRate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

final class GeneratedWorldWarmupMatrixTest {

    private static final long[] SEEDS = {
            0L,
            1L,
            42L,
            991L,
            123_456_789L
    };
    private static final long[] CHECKPOINTS = {0L, 10L, 25L, 50L};

    @TestFactory
    Stream<DynamicTest> representativeGeneratedWorldMatrixReplaysExactly() {
        List<DynamicTest> tests = new ArrayList<>();
        for (long seed : SEEDS) {
            for (MatrixProfile profile : profiles()) {
                tests.add(DynamicTest.dynamicTest(
                        "seed=" + seed + " profile=" + profile.name(),
                        () -> verify(seed, profile)));
            }
        }
        return tests.stream();
    }

    private static void verify(
            long seed,
            MatrixProfile profile) {
        GeneratedWorldRuntime first = GeneratedWorldWarmupFixture.create(
                seed,
                profile.climate(),
                profile.atmosphericForcingPolicy());
        GeneratedWorldRuntime replay = GeneratedWorldWarmupFixture.create(
                seed,
                profile.climate(),
                profile.atmosphericForcingPolicy());
        GeneratedWorldWarmup warmup = new GeneratedWorldWarmup();

        List<GeneratedWorldDiagnostics> firstTrace = warmup.run(
                first,
                CHECKPOINTS);
        List<GeneratedWorldDiagnostics> replayTrace = warmup.run(
                replay,
                CHECKPOINTS);

        assertEquals(firstTrace, replayTrace);
        assertEquals(50L, first.runtime().time().tick());
        assertEquals(50L, replay.runtime().time().tick());

        for (GeneratedWorldDiagnostics snapshot : firstTrace) {
            assertEquals(seed, snapshot.masterSeed());
            assertEquals(16, snapshot.terrainColumns());
            assertTrue(snapshot.surfaceMatchesAtlas());
            assertTrue(snapshot.terminalBasins() >= 1L);
            assertTrue(snapshot.maximumContributingArea() >= 1L);
        }

        GeneratedWorldDiagnostics initial = firstTrace.get(0);
        assertTrue(initial.generatedInitialWaterVolume() > 0L);
        assertTrue(initial.generatedInitialWaterColumns() > 0);
        assertTrue(initial.generatedShorelineColumns() > 0);
        assertEquals(initial.generatedInitialWaterVolume(), initial.totalWaterVolume());

        GeneratedWorldDiagnostics finalSnapshot = firstTrace.get(firstTrace.size() - 1);
        if (AtmosphericForcingPolicy.CLIMATE_NORMALS.equals(
                profile.atmosphericForcingPolicy())) {
            assertTrue(finalSnapshot.totalWaterVolume() > initial.totalWaterVolume());
            assertTrue(finalSnapshot.retainedWaterVolume() > 0L);
            assertTrue(finalSnapshot.wetSoilCells() > 0L);
        } else {
            assertTrue(firstTrace.stream()
                    .allMatch(snapshot -> snapshot.totalWaterVolume()
                            == initial.totalWaterVolume()));
        }
    }

    private static List<MatrixProfile> profiles() {
        return List.of(
                new MatrixProfile(
                        "isolated-water-balance",
                        ClimateSpec.STANDARD,
                        AtmosphericForcingPolicy.DISABLED),
                new MatrixProfile(
                        "fractional-net-supply",
                        ClimateSpec.of(
                                ClimateTemperature.ofMilliCelsius(12_000),
                                250,
                                CellVolumeRate.of(100_001L, 3L),
                                CellVolumeRate.of(20_003L, 4L)),
                        AtmosphericForcingPolicy.CLIMATE_NORMALS));
    }

    /** Internal engine-test profile; these exact rates are not user-facing world controls. */
    private record MatrixProfile(
            String name,
            ClimateSpec climate,
            AtmosphericForcingPolicy atmosphericForcingPolicy) {
    }
}
