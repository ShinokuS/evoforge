package io.github.evoforge.simulation.world.warmup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import io.github.evoforge.simulation.world.bootstrap.GeneratedWorldRuntime;
import io.github.evoforge.simulation.world.diagnostics.GeneratedWorldDiagnostics;
import io.github.evoforge.simulation.world.genesis.HydroClimateSpec;
import io.github.evoforge.simulation.world.mechanics.geometry.CellVolumeRate;

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
                profile.climate());
        GeneratedWorldRuntime replay = GeneratedWorldWarmupFixture.create(
                seed,
                profile.climate());
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

        assertEquals(0L, firstTrace.get(0).totalWaterVolume());
        GeneratedWorldDiagnostics finalSnapshot = firstTrace.get(firstTrace.size() - 1);
        if (profile.expectWater()) {
            assertTrue(finalSnapshot.totalWaterVolume() > 0L);
            assertTrue(finalSnapshot.retainedWaterVolume() > 0L);
            assertTrue(finalSnapshot.wetSoilCells() > 0L);
        } else {
            assertTrue(firstTrace.stream()
                    .allMatch(snapshot -> snapshot.totalWaterVolume() == 0L));
        }
    }

    private static List<MatrixProfile> profiles() {
        return List.of(
                new MatrixProfile(
                        "unforced",
                        HydroClimateSpec.UNFORCED,
                        false),
                new MatrixProfile(
                        "fractional-net-supply",
                        HydroClimateSpec.of(
                                CellVolumeRate.of(100_001L, 3L),
                                CellVolumeRate.of(20_003L, 4L)),
                        true));
    }

    /** Internal engine-test profile; these exact rates are not user-facing world controls. */
    private record MatrixProfile(
            String name,
            HydroClimateSpec climate,
            boolean expectWater) {
    }
}
