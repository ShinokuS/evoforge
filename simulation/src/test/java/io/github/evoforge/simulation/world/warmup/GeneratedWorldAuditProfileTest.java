package io.github.evoforge.simulation.world.warmup;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.evoforge.simulation.world.bootstrap.GeneratedWorldRuntime;
import io.github.evoforge.simulation.world.diagnostics.GeneratedWorldDiagnostics;
import io.github.evoforge.simulation.world.diagnostics.GeneratedWorldDiagnosticsFormat;
import io.github.evoforge.simulation.world.genesis.HydroClimateSpec;
import io.github.evoforge.simulation.world.mechanics.geometry.CellVolumeRate;

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
        long endTick = Long.getLong(
                "evoforge.generated.audit.ticks",
                100L);
        if (endTick < 4L) {
            throw new IllegalArgumentException(
                    "evoforge.generated.audit.ticks must be >= 4");
        }
        long[] checkpoints = {
                0L,
                endTick / 4L,
                endTick / 2L,
                endTick
        };

        for (long seed : SEEDS) {
            for (AuditProfile profile : profiles()) {
                GeneratedWorldRuntime world = GeneratedWorldWarmupFixture.create(
                        seed,
                        profile.climate());
                List<GeneratedWorldDiagnostics> trace =
                        new GeneratedWorldWarmup().run(world, checkpoints);

                for (GeneratedWorldDiagnostics snapshot : trace) {
                    assertTrue(snapshot.surfaceMatchesAtlas());
                    System.out.println(
                            "scenario=" + profile.name()
                                    + " "
                                    + GeneratedWorldDiagnosticsFormat.line(snapshot));
                }
            }
        }
    }

    private static List<AuditProfile> profiles() {
        return List.of(
                new AuditProfile(
                        "unforced",
                        HydroClimateSpec.UNFORCED),
                new AuditProfile(
                        "fractional-net-supply",
                        HydroClimateSpec.of(
                                CellVolumeRate.of(100_001L, 3L),
                                CellVolumeRate.of(20_003L, 4L))));
    }

    /** Developer audit inputs, not a user-facing climate preset contract. */
    private record AuditProfile(
            String name,
            HydroClimateSpec climate) {
    }
}
