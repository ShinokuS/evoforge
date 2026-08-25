package io.github.evoforge.visualizer.continuum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.evoforge.simulation.world.continuum.field.ContinuumSampleWindow;
import io.github.evoforge.simulation.world.continuum.field.ContinuumScalarPage;
import io.github.evoforge.simulation.world.geophysics.MacroGeophysicsDefinition;
import io.github.evoforge.simulation.world.geophysics.MacroGeophysicsPreset;
import io.github.evoforge.simulation.world.terrain.TerrainSurfaceDefinition;
import org.junit.jupiter.api.Test;

final class ContinuumMapInspectorModelTest {

    @Test
    void standardViewerUsesBalancedMacroProfileSurfaceDefinitionAndDefaultSeed() {
        try (ContinuumMapInspectorModel model = ContinuumMapInspectorModel.standard(1600, 900)) {
            assertEquals(MacroGeophysicsPreset.BALANCED, model.preset());
            assertEquals(MacroGeophysicsPreset.BALANCED.definition(), model.definition());
            assertEquals(TerrainSurfaceDefinition.balanced(), model.surfaceDefinition());
            assertEquals("balanced", model.profileName());
            assertEquals(ContinuumMapInspectorModel.DEFAULT_WORLD_SEED, model.seed());
        }
    }

    @Test
    void viewerCanStartFromExplicitSeed() {
        long seed = 987_654_321L;
        try (ContinuumMapInspectorModel model = ContinuumMapInspectorModel.standard(
                1600,
                900,
                MacroGeophysicsPreset.ARCHIPELAGO,
                seed)) {
            assertEquals(MacroGeophysicsPreset.ARCHIPELAGO, model.preset());
            assertEquals(MacroGeophysicsPreset.ARCHIPELAGO.definition(), model.definition());
            assertEquals(TerrainSurfaceDefinition.balanced(), model.surfaceDefinition());
            assertEquals(seed, model.seed());
        }
    }

    @Test
    void customDefinitionsRebuildOneSourceWithoutMovingCameraOrChangingSeed() {
        try (ContinuumMapInspectorModel model = ContinuumMapInspectorModel.standard(1600, 900)) {
            model.panPixels(-173d, 91d);
            model.zoomAt(1.22d, 710d, 390d);
            double centerX = model.centerX();
            double centerY = model.centerY();
            double scale = model.pixelsPerWorldUnit();
            long seed = model.seed();
            long sourceRevision = model.sourceRevision();
            MacroGeophysicsDefinition macro = MacroGeophysicsDefinition.of(0.78d, 0.52d, 0.44d, 0.63d, 0.31d);
            TerrainSurfaceDefinition surface = TerrainSurfaceDefinition.of(0.84d, 0.77d, 0.58d, 0.36d);

            assertTrue(model.applyDefinitions(macro, surface));

            assertEquals(macro, model.definition());
            assertEquals(surface, model.surfaceDefinition());
            assertNull(model.preset());
            assertEquals("custom", model.profileName());
            assertEquals(centerX, model.centerX());
            assertEquals(centerY, model.centerY());
            assertEquals(scale, model.pixelsPerWorldUnit());
            assertEquals(seed, model.seed());
            assertEquals(sourceRevision + 1L, model.sourceRevision());
            assertFalse(model.applyDefinitions(macro, surface));
        }
    }

    @Test
    void surfaceOnlyChangePreservesMacroPresetSeedAndCamera() {
        try (ContinuumMapInspectorModel model = ContinuumMapInspectorModel.standard(
                1600,
                900,
                MacroGeophysicsPreset.SUPERCONTINENT)) {
            model.panPixels(81d, -49d);
            double centerX = model.centerX();
            double centerY = model.centerY();
            long seed = model.seed();
            TerrainSurfaceDefinition surface = TerrainSurfaceDefinition.of(0.95d, 0.82d, 0.70d, 0.25d);

            assertTrue(model.applySurfaceDefinition(surface));

            assertEquals(surface, model.surfaceDefinition());
            assertEquals(MacroGeophysicsPreset.SUPERCONTINENT, model.preset());
            assertEquals(MacroGeophysicsPreset.SUPERCONTINENT.definition(), model.definition());
            assertEquals(seed, model.seed());
            assertEquals(centerX, model.centerX());
            assertEquals(centerY, model.centerY());
            assertFalse(model.applySurfaceDefinition(surface));
        }
    }

    @Test
    void changingSeedPreservesBothAuthoredLayersAndCamera() {
        try (ContinuumMapInspectorModel model = ContinuumMapInspectorModel.standard(
                1600,
                900,
                MacroGeophysicsPreset.ARCHIPELAGO)) {
            TerrainSurfaceDefinition surface = TerrainSurfaceDefinition.of(0.74d, 0.67d, 0.21d, 0.44d);
            assertTrue(model.applySurfaceDefinition(surface));
            model.panPixels(127d, -64d);
            model.zoomAt(1.22d, 800d, 450d);
            double centerX = model.centerX();
            double centerY = model.centerY();
            double scale = model.pixelsPerWorldUnit();
            MacroGeophysicsDefinition macro = model.definition();

            assertTrue(model.applySeed(42L));

            assertEquals(42L, model.seed());
            assertEquals(MacroGeophysicsPreset.ARCHIPELAGO, model.preset());
            assertEquals(macro, model.definition());
            assertEquals(surface, model.surfaceDefinition());
            assertEquals(centerX, model.centerX());
            assertEquals(centerY, model.centerY());
            assertEquals(scale, model.pixelsPerWorldUnit());
            assertFalse(model.applySeed(42L));
        }
    }

    @Test
    void rawThreeDimensionalSamplingUsesTheSameBoundedSurfaceSource() {
        try (ContinuumMapInspectorModel model = ContinuumMapInspectorModel.standard(1600, 900)) {
            ContinuumScalarPage coarse = model.materializeSurface(
                    new ContinuumSampleWindow(4_000_000L, 5_000_000L, 65, 65, 8_192L));
            long sharedX = coarse.window().xAt(32);
            long sharedY = coarse.window().yAt(32);
            ContinuumScalarPage exact = model.materializeSurface(
                    new ContinuumSampleWindow(sharedX, sharedY, 1, 1, 1L));

            assertEquals(coarse.sample(32, 32), exact.sample(0, 0));
            assertTrue(Double.isFinite(exact.sample(0, 0)));
        }
    }
}
