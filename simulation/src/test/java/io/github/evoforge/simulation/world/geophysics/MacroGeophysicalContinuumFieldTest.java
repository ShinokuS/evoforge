package io.github.evoforge.simulation.world.geophysics;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.evoforge.simulation.world.continuum.field.ContinuumMaterializer;
import io.github.evoforge.simulation.world.continuum.field.ContinuumSampleWindow;
import io.github.evoforge.simulation.world.continuum.field.ContinuumScalarField;
import io.github.evoforge.simulation.world.continuum.field.ContinuumScalarPage;
import io.github.evoforge.simulation.world.continuum.model.ContinuumResolution;
import io.github.evoforge.simulation.world.continuum.model.ContinuumWorldDomain;
import io.github.evoforge.simulation.world.continuum.page.ContinuumPageKey;
import io.github.evoforge.simulation.world.continuum.page.ContinuumPageLayout;
import org.junit.jupiter.api.Test;

final class MacroGeophysicalContinuumFieldTest {

    @Test
    void sharedCoordinatesRemainIdenticalAcrossResolutionLevels() {
        ContinuumWorldDomain domain = new ContinuumWorldDomain(20_000_000L, 20_000_000L);
        MacroGeophysicalField geophysics = new DeterministicMacroGeophysicalField(91_337L, 5L);
        ContinuumScalarField continuumField = new MacroGeophysicalContinuumField(geophysics);
        ContinuumMaterializer materializer = new ContinuumMaterializer(domain, continuumField);
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
    void overlappingWindowsCannotCreateARepresentationSeam() {
        ContinuumWorldDomain domain = new ContinuumWorldDomain(10_000_000L, 10_000_000L);
        ContinuumScalarField field = new MacroGeophysicalContinuumField(
                new DeterministicMacroGeophysicalField(42L, 1L));
        ContinuumMaterializer materializer = new ContinuumMaterializer(domain, field);

        ContinuumScalarPage left = materializer.materialize(
                new ContinuumSampleWindow(1_000_000L, 2_000_000L, 65, 64, 4_096L));
        ContinuumScalarPage right = materializer.materialize(
                new ContinuumSampleWindow(1_262_144L, 2_000_000L, 65, 64, 4_096L));

        for (int y = 0; y < 64; y++) {
            assertEquals(left.sample(64, y), right.sample(0, y));
        }
    }

    @Test
    void unrelatedMaterializationDoesNotChangeRequestedMacroArea() {
        ContinuumWorldDomain domain = new ContinuumWorldDomain(50_000_000L, 50_000_000L);
        ContinuumScalarField field = new MacroGeophysicalContinuumField(
                new DeterministicMacroGeophysicalField(777L, 9L));
        ContinuumMaterializer materializer = new ContinuumMaterializer(domain, field);
        ContinuumSampleWindow target = new ContinuumSampleWindow(5_000_000L, 4_000_000L, 48, 48, 8_192L);

        double[] before = materializer.materialize(target).copySamples();
        materializer.materialize(new ContinuumSampleWindow(31_000_000L, 27_000_000L, 96, 96, 32_768L));
        double[] after = materializer.materialize(target).copySamples();

        assertArrayEquals(before, after);
    }
}
