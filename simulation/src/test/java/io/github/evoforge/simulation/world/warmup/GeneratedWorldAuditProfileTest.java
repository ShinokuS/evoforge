package io.github.evoforge.simulation.world.warmup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.evoforge.simulation.world.bootstrap.AtmosphericForcingPolicy;
import io.github.evoforge.simulation.world.bootstrap.GeneratedWorldRuntime;
import io.github.evoforge.simulation.world.climate.ClimateTemperature;
import io.github.evoforge.simulation.world.diagnostics.GeneratedWorldDiagnostics;
import io.github.evoforge.simulation.world.diagnostics.GeneratedWorldDiagnosticsFormat;
import io.github.evoforge.simulation.world.genesis.ClimateSpec;
import io.github.evoforge.simulation.world.genesis.GenerationRevision;
import io.github.evoforge.simulation.world.mechanics.measurement.WaterDepthRate;
import io.github.evoforge.simulation.world.spatial.WorldBounds;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("generated-world-audit")
final class GeneratedWorldAuditProfileTest {

    private static final long[] SEEDS = {0L, 1L, 42L, 991L, 123_456_789L};
    private static final Duration CLIMATE_YEAR = Duration.ofDays(365L);

    @Test
    void printsRepresentativeGeneratedWorldCheckpoints() {
        long endTick = Long.getLong("evoforge.generated.audit.ticks", 100L);
        int side = Integer.getInteger("evoforge.generated.audit.side", 32);
        if (endTick < 4L) {
            throw new IllegalArgumentException("evoforge.generated.audit.ticks must be >= 4");
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
                        bounds,
                        profile.atmosphericForcingPolicy(),
                        GenerationRevision.V11);
                assertEquals(GenerationRevision.V11, world.atlas().genesis().generationRevision());

                List<GeneratedWorldDiagnostics> trace =
                        new GeneratedWorldWarmup().run(world, checkpoints);

                GeneratedWorldDiagnostics initial = trace.get(0);
                assertTrue(initial.geologyProvinces() >= 1);
                assertTrue(initial.geologyUnits() > 1, "generated geology collapsed to one unit");
                assertTrue(initial.generatedInitialWaterVolume() > 0L);
                assertTrue(initial.generatedInitialWaterColumns() > 0);
                assertTrue(initial.generatedShorelineColumns() > 0);
                assertEquals(initial.generatedInitialWaterVolume(), initial.totalWaterVolume());

                for (GeneratedWorldDiagnostics snapshot : trace) {
                    assertTrue(snapshot.surfaceMatchesAtlas());
                    assertEquals(initial.geologyProvinces(), snapshot.geologyProvinces());
                    assertEquals(initial.geologyUnits(), snapshot.geologyUnits());
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
                            "revision=" + GenerationRevision.V11.value()
                                    + " scenario=" + profile.name()
                                    + " side=" + side
                                    + " "
                                    + GeneratedWorldDiagnosticsFormat.line(snapshot));
                }

                GeneratedWorldDiagnostics end = trace.get(trace.size() - 1);
                if (AtmosphericForcingPolicy.CLIMATE_NORMALS.equals(
                        profile.atmosphericForcingPolicy())) {
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
                        "isolated-water-balance",
                        physicalClimate(500L, 500L),
                        AtmosphericForcingPolicy.DISABLED),
                new AuditProfile(
                        "fractional-net-supply",
                        physicalClimate(800L, 200L),
                        AtmosphericForcingPolicy.CLIMATE_NORMALS));
    }

    private static ClimateSpec physicalClimate(
            long precipitationMillimetersPerYear,
            long evaporationMillimetersPerYear) {
        return ClimateSpec.physical(
                ClimateTemperature.ofMilliCelsius(12_000),
                250,
                WaterDepthRate.ofMillimeters(precipitationMillimetersPerYear, CLIMATE_YEAR),
                WaterDepthRate.ofMillimeters(evaporationMillimetersPerYear, CLIMATE_YEAR));
    }

    /** Developer audit inputs, not a user-facing climate preset contract. */
    private record AuditProfile(
            String name,
            ClimateSpec climate,
            AtmosphericForcingPolicy atmosphericForcingPolicy) { }
}
