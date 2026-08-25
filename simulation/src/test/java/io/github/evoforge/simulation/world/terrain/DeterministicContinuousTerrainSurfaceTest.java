package io.github.evoforge.simulation.world.terrain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.evoforge.simulation.world.geophysics.MacroGeophysicalField;
import io.github.evoforge.simulation.world.geophysics.MacroGeophysics;
import io.github.evoforge.simulation.world.geophysics.MacroGeophysicsDefinition;
import io.github.evoforge.simulation.world.geophysics.MacroGeophysicsPreset;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class DeterministicContinuousTerrainSurfaceTest {
    private static final long SEED = 0x0123456789ABCDEFL;
    private static final long GEOPHYSICS_REVISION = 7L;
    private static final long SURFACE_REVISION = 12L;
    private static final TerrainSurfaceDefinition DEFINITION = TerrainSurfaceDefinition.balanced();

    @Test
    void addressedCoordinatesAreStableAcrossFreshEquivalentSurfaces() {
        ContinuousTerrainSurface first = surface();
        ContinuousTerrainSurface second = surface();
        long[][] probes = {
            {0L, 0L},
            {123_456L, 789_012L},
            {-987_654_321L, 123_456_789L},
            {1L << 40, -(1L << 39)},
            {4_321_000L, 6_543_000L}
        };

        for (long[] probe : probes) {
            double value = first.surfaceZAt(probe[0], probe[1]);
            assertTrue(Double.isFinite(value));
            assertTrue(value >= -4_096.0d && value <= 4_096.0d);
            assertEquals(value, second.surfaceZAt(probe[0], probe[1]));
        }
    }

    @Test
    void queryOrderAndUnrelatedQueriesDoNotChangeSurfaceTruth() {
        ContinuousTerrainSurface surface = surface();
        List<long[]> probes = List.of(
                new long[] {10L, 20L},
                new long[] {900_000L, 1_200_000L},
                new long[] {-700_000L, 333_333L},
                new long[] {1L << 35, -(1L << 34)});
        double[] before = probes.stream()
                .mapToDouble(point -> surface.surfaceZAt(point[0], point[1]))
                .toArray();

        for (int index = probes.size() - 1; index >= 0; index--) {
            long[] point = probes.get(index);
            surface.surfaceZAt(point[0] + 44_000_000L, point[1] - 17_000_000L);
        }

        for (int index = 0; index < probes.size(); index++) {
            long[] point = probes.get(index);
            assertEquals(before[index], surface.surfaceZAt(point[0], point[1]));
        }
    }

    @Test
    void seedSurfaceRevisionAndBothDefinitionsParticipateInAddressedTruth() {
        long x = 3_456_789L;
        long y = 7_654_321L;
        double baseline = surface().surfaceZAt(x, y);
        MacroGeophysicalField macro = macro(MacroGeophysicsPreset.BALANCED.definition());

        assertNotEquals(
                baseline,
                TerrainSurfaceEvolution.create(SEED + 1L, SURFACE_REVISION, macro, DEFINITION).surfaceZAt(x, y));
        assertNotEquals(
                baseline,
                TerrainSurfaceEvolution.create(SEED, SURFACE_REVISION + 1L, macro, DEFINITION).surfaceZAt(x, y));
        assertNotEquals(
                baseline,
                TerrainSurfaceEvolution.create(
                                SEED,
                                SURFACE_REVISION,
                                macro,
                                TerrainSurfaceDefinition.of(0.92d, 0.80d, 0.10d, 0.30d))
                        .surfaceZAt(x, y));

        MacroGeophysicsDefinition differentMacro = MacroGeophysicsDefinition.of(0.30d, 0.80d, 0.75d, 0.12d, 0.20d);
        assertNotEquals(
                baseline,
                TerrainSurfaceEvolution.create(
                                SEED,
                                SURFACE_REVISION,
                                macro(differentMacro),
                                DEFINITION)
                        .surfaceZAt(x, y));
    }

    @Test
    void submergenceIsDerivedOnlyFromTheContinuousSurfaceAndSeaDatum() {
        ContinuousTerrainSurface surface = surface();

        for (long y = 0L; y <= 8_000_000L; y += 421_337L) {
            for (long x = 0L; x <= 8_000_000L; x += 379_123L) {
                double z = surface.surfaceZAt(x, y);
                assertTrue(Double.isFinite(z));
                assertTrue(z >= -4_096.0d && z <= 4_096.0d);
                assertEquals(z < ContinuousTerrainSurface.SEA_DATUM, surface.isSubmergedAt(x, y));
            }
        }
    }

    @Test
    void coastalBandCanRefineTheStage5ZeroContourOnBothSides() {
        MacroGeophysicalField exactCoast = (x, y) -> 0.0d;
        ContinuousTerrainSurface surface = TerrainSurfaceEvolution.create(
                0x45A10F0E2026L,
                3L,
                exactCoast,
                DEFINITION);
        int above = 0;
        int below = 0;

        for (long y = 0L; y < 1_000_000L; y += 16_384L) {
            for (long x = 0L; x < 1_000_000L; x += 16_384L) {
                double z = surface.surfaceZAt(x, y);
                if (z > 0.0d) above++;
                if (z < 0.0d) below++;
            }
        }

        assertTrue(above > 100, "Stage 6 should be able to create capes/islets inside the coastal band");
        assertTrue(below > 100, "Stage 6 should be able to create bays/coves inside the coastal band");
    }

    @Test
    void coastalRefinementRemainsLocalAndCannotFlipDeepLandOrDeepOcean() {
        ContinuousTerrainSurface deepLand = TerrainSurfaceEvolution.create(
                SEED,
                SURFACE_REVISION,
                (x, y) -> 0.50d,
                DEFINITION);
        ContinuousTerrainSurface deepOcean = TerrainSurfaceEvolution.create(
                SEED,
                SURFACE_REVISION,
                (x, y) -> -0.50d,
                DEFINITION);

        for (long y = 0L; y <= 4_000_000L; y += 71_111L) {
            for (long x = 0L; x <= 4_000_000L; x += 65_537L) {
                assertTrue(deepLand.surfaceZAt(x, y) > 0.0d, "deep continental support must stay land");
                assertTrue(deepOcean.surfaceZAt(x, y) < 0.0d, "deep ocean support must stay submerged");
            }
        }
    }

    @Test
    void finerObservationRevealsRealDeterministicStructureRatherThanOnlyInterpolation() {
        ContinuousTerrainSurface surface = surface();
        long originX = 2_000_000L;
        long originY = 2_000_000L;
        long coarseStep = 65_536L;
        int meaningfulResiduals = 0;
        double largestResidual = 0.0d;

        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                long x0 = originX + x * coarseStep;
                long y0 = originY + y * coarseStep;
                double z00 = surface.surfaceZAt(x0, y0);
                double z10 = surface.surfaceZAt(x0 + coarseStep, y0);
                double z01 = surface.surfaceZAt(x0, y0 + coarseStep);
                double z11 = surface.surfaceZAt(x0 + coarseStep, y0 + coarseStep);
                double interpolatedMidpoint = (z00 + z10 + z01 + z11) * 0.25d;
                double actualMidpoint = surface.surfaceZAt(
                        x0 + coarseStep / 2L,
                        y0 + coarseStep / 2L);
                double residual = Math.abs(actualMidpoint - interpolatedMidpoint);
                largestResidual = Math.max(largestResidual, residual);
                if (residual > 2.0d) meaningfulResiduals++;
            }
        }

        assertTrue(largestResidual > 10.0d, "finer observation should reveal subordinate causal relief");
        assertTrue(meaningfulResiduals >= 16, "added detail must be spatially substantial");
    }

    @Test
    void nearFieldZoomRevealsDetailBelowTheOldKilocellFloor() {
        MacroGeophysicalField inland = (x, y) -> 0.35d;
        ContinuousTerrainSurface surface = TerrainSurfaceEvolution.create(
                0x45A10F0E2026L,
                3L,
                inland,
                DEFINITION);
        long coarseStep = 1_024L;
        int meaningfulResiduals = 0;
        double largestResidual = 0.0d;

        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                long x0 = 1_000_000L + x * coarseStep;
                long y0 = 1_000_000L + y * coarseStep;
                double z00 = surface.surfaceZAt(x0, y0);
                double z10 = surface.surfaceZAt(x0 + coarseStep, y0);
                double z01 = surface.surfaceZAt(x0, y0 + coarseStep);
                double z11 = surface.surfaceZAt(x0 + coarseStep, y0 + coarseStep);
                double interpolatedMidpoint = (z00 + z10 + z01 + z11) * 0.25d;
                double actualMidpoint = surface.surfaceZAt(
                        x0 + coarseStep / 2L,
                        y0 + coarseStep / 2L);
                double residual = Math.abs(actualMidpoint - interpolatedMidpoint);
                largestResidual = Math.max(largestResidual, residual);
                if (residual > 0.10d) meaningfulResiduals++;
            }
        }

        assertTrue(largestResidual > 0.5d, "close zoom must reveal sub-kilocell terrain structure");
        assertTrue(meaningfulResiduals >= 120, "close-scale detail must be widespread in active terrain");
    }

    @Test
    void ruggedSurfaceStillAvoidsBlockScaleSpikesAndCheckerboards() {
        ContinuousTerrainSurface rugged = TerrainSurfaceEvolution.create(
                0x45A10F0E2026L,
                3L,
                MacroGeophysics.create(
                        0x45A10F0E2026L,
                        1L,
                        MacroGeophysicsPreset.BALANCED.definition()),
                TerrainSurfaceDefinition.of(1.0d, 1.0d, 0.0d, 0.0d));

        long[][] origins = {
            {0L, 0L},
            {1_000_000L, 1_000_000L},
            {4_000_000L, 8_000_000L},
            {10_000_000L, 3_000_000L},
            {15_000_000L, 12_000_000L}
        };
        for (long[] origin : origins) {
            AntiNoiseMetrics metrics = antiNoiseMetrics(rugged, origin[0], origin[1], 96);
            assertTrue(metrics.maxAdjacentDelta < 0.40d, "unit-cell surface slope is too steep/high-frequency");
            assertTrue(metrics.maxSecondDifference < 0.10d, "unit-cell curvature is too high-frequency");
            assertEquals(0, metrics.isolatedQuantizedSamples, "single-cell Z spikes/pits are forbidden at the source");
            assertEquals(0, metrics.checkerboards, "checkerboard/corner-supported Z noise is forbidden at the source");
        }
    }

    @Test
    void creationIsLazyAndDoesNotMaterializeAnyWorldArea() {
        AtomicInteger macroReads = new AtomicInteger();
        MacroGeophysicalField countedMacro = (x, y) -> {
            macroReads.incrementAndGet();
            return 0.25d;
        };

        ContinuousTerrainSurface surface = TerrainSurfaceEvolution.create(1L, 1L, countedMacro, DEFINITION);
        assertEquals(0, macroReads.get());
        surface.surfaceZAt(10L, 20L);
        assertEquals(1, macroReads.get());
    }

    @Test
    void invalidCreationDependenciesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> TerrainSurfaceEvolution.create(1L, 1L, null, DEFINITION));
        assertThrows(
                IllegalArgumentException.class,
                () -> TerrainSurfaceEvolution.create(1L, 1L, macro(MacroGeophysicsPreset.BALANCED.definition()), null));
    }

    private static ContinuousTerrainSurface surface() {
        return TerrainSurfaceEvolution.create(
                SEED,
                SURFACE_REVISION,
                macro(MacroGeophysicsPreset.BALANCED.definition()),
                DEFINITION);
    }

    private static MacroGeophysicalField macro(MacroGeophysicsDefinition definition) {
        return MacroGeophysics.create(SEED, GEOPHYSICS_REVISION, definition);
    }

    private static AntiNoiseMetrics antiNoiseMetrics(
            ContinuousTerrainSurface surface,
            long originX,
            long originY,
            int side) {
        double[][] z = new double[side][side];
        long[][] quantized = new long[side][side];
        for (int y = 0; y < side; y++) {
            for (int x = 0; x < side; x++) {
                z[y][x] = surface.surfaceZAt(originX + x, originY + y);
                quantized[y][x] = Math.round(z[y][x]);
            }
        }

        double maxAdjacentDelta = 0.0d;
        double maxSecondDifference = 0.0d;
        int isolated = 0;
        int checkerboards = 0;

        for (int y = 0; y < side; y++) {
            for (int x = 0; x < side - 1; x++) {
                maxAdjacentDelta = Math.max(maxAdjacentDelta, Math.abs(z[y][x + 1] - z[y][x]));
            }
        }
        for (int y = 0; y < side - 1; y++) {
            for (int x = 0; x < side; x++) {
                maxAdjacentDelta = Math.max(maxAdjacentDelta, Math.abs(z[y + 1][x] - z[y][x]));
            }
        }
        for (int y = 0; y < side - 1; y++) {
            for (int x = 0; x < side - 1; x++) {
                long a = quantized[y][x];
                long b = quantized[y][x + 1];
                long c = quantized[y + 1][x];
                long d = quantized[y + 1][x + 1];
                if (a == d && b == c && a != b) checkerboards++;
            }
        }
        for (int y = 1; y < side - 1; y++) {
            for (int x = 1; x < side - 1; x++) {
                maxSecondDifference = Math.max(
                        maxSecondDifference,
                        Math.abs(z[y][x + 1] - 2.0d * z[y][x] + z[y][x - 1]));
                maxSecondDifference = Math.max(
                        maxSecondDifference,
                        Math.abs(z[y + 1][x] - 2.0d * z[y][x] + z[y - 1][x]));

                int sameNeighbours = 0;
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        if (dx == 0 && dy == 0) continue;
                        if (quantized[y + dy][x + dx] == quantized[y][x]) sameNeighbours++;
                    }
                }
                if (sameNeighbours == 0) isolated++;
            }
        }

        return new AntiNoiseMetrics(maxAdjacentDelta, maxSecondDifference, isolated, checkerboards);
    }

    private record AntiNoiseMetrics(
            double maxAdjacentDelta,
            double maxSecondDifference,
            int isolatedQuantizedSamples,
            int checkerboards) {}
}
