package io.github.evoforge.simulation.world.warmup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.evoforge.simulation.world.bootstrap.GeneratedWorldRuntime;
import io.github.evoforge.simulation.world.diagnostics.GeneratedWorldDiagnostics;
import io.github.evoforge.simulation.world.diagnostics.GeneratedWorldDiagnosticsFormat;
import io.github.evoforge.simulation.world.genesis.HydroClimateSpec;
import io.github.evoforge.simulation.world.mechanics.geometry.CellVolumeRate;
import io.github.evoforge.simulation.world.spatial.WorldBounds;

@Tag("generated-world-audit")
final class GeneratedWorldAuditProfileTest {

    private static final long[] SEEDS = {
            0L,
            1L,
            42L,
            991L,
            123_456_789L
    };

    @Test
    void printsRepresentativeGeneratedWorldCheckpoints() {
        long endTick = Long.getLong("evoforge.generated.audit.ticks", 100L);
        int side = Integer.getInteger("evoforge.generated.audit.side", 32);
        if (endTick < 4L) {
            throw new IllegalArgumentException(
                    "evoforge.generated.audit.ticks must be >= 4");
        }
        if (side < 8 || side > 128) {
            throw new IllegalArgumentException(
                    "evoforge.generated.audit.side must be between 8 and 128");
        }

        long[] checkpoints = {0L, endTick / 4L, endTick / 2L, endTick};
        WorldBounds bounds = representativeBounds(side);

        for (long seed : SEEDS) {
            for (AuditProfile profile : profiles()) {
                GeneratedWorldRuntime world = GeneratedWorldWarmupFixture.create(
                        seed,
                        profile.climate(),
                        bounds);
                List<GeneratedWorldDiagnostics> trace =
                        new GeneratedWorldWarmup().run(world, checkpoints);

                GeneratedWorldDiagnostics initial = trace.get(0);
                assertTrue(initial.generatedInitialWaterVolume() > 0L);
                assertTrue(initial.generatedInitialWaterColumns() > 0);
                assertTrue(initial.generatedShorelineColumns() > 0);
                assertEquals(
                        initial.generatedInitialWaterVolume(),
                        initial.totalWaterVolume());

                for (GeneratedWorldDiagnostics snapshot : trace) {
                    assertTrue(snapshot.surfaceMatchesAtlas());
                    assertEquals(
                            initial.generatedInitialWaterVolume(),
                            snapshot.generatedInitialWaterVolume());
                    assertEquals(
                            initial.generatedInitialWaterColumns(),
                            snapshot.generatedInitialWaterColumns());
                    assertEquals(
                            initial.generatedShorelineColumns(),
                            snapshot.generatedShorelineColumns());
                    System.out.println(
                            "scenario=" + profile.name()
                                    + " side=" + side
                                    + " "
                                    + GeneratedWorldDiagnosticsFormat.line(snapshot));
                }

                GeneratedWorldDiagnostics end = trace.get(trace.size() - 1);
                if (profile.hasAtmosphericSupply()) {
                    assertTrue(end.totalWaterVolume() > initial.totalWaterVolume());
                    assertTrue(end.retainedWaterVolume() > 0L);
                } else {
                    assertTrue(trace.stream().allMatch(snapshot ->
                            snapshot.totalWaterVolume() == initial.totalWaterVolume()));
                    assertTrue(end.retainedWaterVolume() > 0L);
                }
            }
        }
    }

    private static WorldBounds representativeBounds(int side) {
        int min = -side / 2;
        int max = min + side - 1;
        return new WorldBounds(min, max, min, max, -32, 32);
    }

    private static List<AuditProfile> profiles() {
        return List.of(
                new AuditProfile(
                        "unforced",
                        HydroClimateSpec.UNFORCED,
                        false),
                new AuditProfile(
                        "fractional-net-supply",
                        HydroClimateSpec.of(
                                CellVolumeRate.of(100_001L, 3L),
                                CellVolumeRate.of(20_003L, 4L)),
                        true));
    }

    /** Developer audit inputs, not a user-facing climate preset contract. */
    private record AuditProfile(
            String name,
            HydroClimateSpec climate,
            boolean hasAtmosphericSupply) {
    }
}
