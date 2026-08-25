package io.github.evoforge.simulation.world.terrain;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.evoforge.simulation.world.continuum.field.ContinuumMaterializer;
import io.github.evoforge.simulation.world.continuum.field.ContinuumSampleWindow;
import io.github.evoforge.simulation.world.continuum.field.ContinuumScalarField;
import io.github.evoforge.simulation.world.continuum.field.ContinuumScalarPage;
import io.github.evoforge.simulation.world.continuum.model.ContinuumResolution;
import io.github.evoforge.simulation.world.continuum.model.ContinuumWorldDomain;
import io.github.evoforge.simulation.world.continuum.page.ContinuumPageKey;
import io.github.evoforge.simulation.world.continuum.page.ContinuumPageLayout;
import io.github.evoforge.simulation.world.geophysics.MacroGeophysics;
import io.github.evoforge.simulation.world.geophysics.MacroGeophysicsPreset;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class ContinuousTerrainSurfaceContinuumFieldTest {

    @Test
    void sharedCoordinatesRemainIdenticalAcrossResolutionLevels() {
        ContinuumWorldDomain domain = new ContinuumWorldDomain(20_000_000L, 20_000_000L);
        ContinuumScalarField field = continuumField(91_337L, 5L);
        ContinuumMaterializer materializer = new ContinuumMaterializer(domain, field);
        ContinuumPageLayout coarseLayout = new ContinuumPageLayout(
                domain,
                32,
                32,
                new ContinuumResolution(12));
        ContinuumScalarPage coarse = materializer.materialize(coarseLayout.windowFor(new ContinuumPageKey(3L, 2L)));

        int[][] probes = {{0, 0}, {7, 13}, {19, 3}, {31, 31}};
        for (int[] probe : probes) {
            long worldX = coarse.window().xAt(probe[0]);
            long worldY = coarse.window().yAt(probe[1]);
            ContinuumScalarPage exact = materializer.materialize(
                    new ContinuumSampleWindow(worldX, worldY, 1, 1, 1L));
            assertEquals(exact.sample(0, 0), coarse.sample(probe[0], probe[1]));
        }
    }

    @Test
    void horizontalVerticalAndDiagonalOverlapsCannotCreateRepresentationSeams() {
        ContinuumWorldDomain domain = new ContinuumWorldDomain(10_000_000L, 10_000_000L);
        ContinuumMaterializer materializer = new ContinuumMaterializer(domain, continuumField(42L, 1L));
        long step = 4_096L;

        ContinuumScalarPage left = materializer.materialize(
                new ContinuumSampleWindow(1_000_000L, 2_000_000L, 65, 64, step));
        ContinuumScalarPage right = materializer.materialize(
                new ContinuumSampleWindow(1_262_144L, 2_000_000L, 65, 64, step));
        for (int y = 0; y < 64; y++) {
            assertEquals(left.sample(64, y), right.sample(0, y));
        }

        ContinuumScalarPage bottom = materializer.materialize(
                new ContinuumSampleWindow(2_000_000L, 1_000_000L, 64, 65, step));
        ContinuumScalarPage top = materializer.materialize(
                new ContinuumSampleWindow(2_000_000L, 1_262_144L, 64, 65, step));
        for (int x = 0; x < 64; x++) {
            assertEquals(bottom.sample(x, 64), top.sample(x, 0));
        }

        ContinuumScalarPage lowerLeft = materializer.materialize(
                new ContinuumSampleWindow(3_000_000L, 3_000_000L, 65, 65, step));
        ContinuumScalarPage upperRight = materializer.materialize(
                new ContinuumSampleWindow(3_131_072L, 3_131_072L, 65, 65, step));
        for (int y = 0; y <= 32; y++) {
            for (int x = 0; x <= 32; x++) {
                assertEquals(lowerLeft.sample(x + 32, y + 32), upperRight.sample(x, y));
            }
        }
    }

    @Test
    void unrelatedMaterializationDoesNotChangeRequestedSurfaceArea() {
        ContinuumWorldDomain domain = new ContinuumWorldDomain(50_000_000L, 50_000_000L);
        ContinuumMaterializer materializer = new ContinuumMaterializer(domain, continuumField(777L, 9L));
        ContinuumSampleWindow target = new ContinuumSampleWindow(5_000_000L, 4_000_000L, 48, 48, 8_192L);

        double[] before = materializer.materialize(target).copySamples();
        materializer.materialize(new ContinuumSampleWindow(31_000_000L, 27_000_000L, 96, 96, 32_768L));
        double[] after = materializer.materialize(target).copySamples();

        assertArrayEquals(before, after);
    }

    @Test
    void workCountDependsOnlyOnRequestedSamplesNotLogicalWorldArea() {
        int side = 128;
        for (long logicalSide : new long[] {16_000_000L, 1_000_000_000L, 1_000_000_000_000L}) {
            AtomicInteger evaluations = new AtomicInteger();
            ContinuousTerrainSurface counted = (x, y) -> {
                evaluations.incrementAndGet();
                return x * 0.000001d + y * 0.000002d;
            };
            ContinuumMaterializer materializer = new ContinuumMaterializer(
                    new ContinuumWorldDomain(logicalSide, logicalSide),
                    new ContinuousTerrainSurfaceContinuumField(counted));
            materializer.materialize(new ContinuumSampleWindow(0L, 0L, side, side, 1L));
            assertEquals(side * side, evaluations.get());
        }
    }

    @Test
    void adapterKeepsSeaDatumCenteredAndClampsDiagnosticExtremes() {
        assertEquals(0.5d, new ContinuousTerrainSurfaceContinuumField((x, y) -> 0.0d).sample(1L, 2L));
        assertEquals(0.0d, new ContinuousTerrainSurfaceContinuumField((x, y) -> -100_000.0d).sample(1L, 2L));
        assertEquals(1.0d, new ContinuousTerrainSurfaceContinuumField((x, y) -> 100_000.0d).sample(1L, 2L));
        assertThrows(IllegalArgumentException.class, () -> new ContinuousTerrainSurfaceContinuumField(null));
    }

    private static ContinuumScalarField continuumField(long seed, long surfaceRevision) {
        return new ContinuousTerrainSurfaceContinuumField(TerrainSurfaceEvolution.create(
                seed,
                surfaceRevision,
                MacroGeophysics.create(seed, 1L, MacroGeophysicsPreset.BALANCED.definition()),
                TerrainSurfaceDefinition.balanced()));
    }
}
