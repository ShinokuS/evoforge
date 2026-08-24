package io.github.evoforge.visualizer.continuum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.evoforge.simulation.world.geophysics.MacroGeophysicsDefinition;
import io.github.evoforge.simulation.world.geophysics.MacroGeophysicsPreset;
import org.junit.jupiter.api.Test;

final class ContinuumMapInspectorModelTest {

    @Test
    void standardViewerUsesBalancedMacroProfileAndDefaultSeed() {
        try (ContinuumMapInspectorModel model = ContinuumMapInspectorModel.standard(1600, 900)) {
            assertEquals(MacroGeophysicsPreset.BALANCED, model.preset());
            assertEquals(MacroGeophysicsPreset.BALANCED.definition(), model.definition());
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
            assertEquals(seed, model.seed());
        }
    }

    @Test
    void customDefinitionRebuildsMapSourceWithoutMovingCameraOrChangingSeed() {
        try (ContinuumMapInspectorModel model = ContinuumMapInspectorModel.standard(1600, 900)) {
            model.panPixels(-173d, 91d);
            model.zoomAt(1.22d, 710d, 390d);
            double centerX = model.centerX();
            double centerY = model.centerY();
            double scale = model.pixelsPerWorldUnit();
            long seed = model.seed();
            MacroGeophysicsDefinition custom = MacroGeophysicsDefinition.of(0.78d, 0.52d, 0.44d, 0.63d, 0.31d);

            assertTrue(model.applyDefinition(custom));

            assertEquals(custom, model.definition());
            assertNull(model.preset());
            assertEquals("custom", model.profileName());
            assertEquals(centerX, model.centerX());
            assertEquals(centerY, model.centerY());
            assertEquals(scale, model.pixelsPerWorldUnit());
            assertEquals(seed, model.seed());
        }
    }

    @Test
    void changingSeedRebuildsMapSourceWithoutMovingCameraOrChangingProfile() {
        try (ContinuumMapInspectorModel model = ContinuumMapInspectorModel.standard(
                1600,
                900,
                MacroGeophysicsPreset.ARCHIPELAGO)) {
            model.panPixels(127d, -64d);
            model.zoomAt(1.22d, 800d, 450d);
            double centerX = model.centerX();
            double centerY = model.centerY();
            double scale = model.pixelsPerWorldUnit();
            MacroGeophysicsDefinition definition = model.definition();

            assertTrue(model.applySeed(42L));

            assertEquals(42L, model.seed());
            assertEquals(MacroGeophysicsPreset.ARCHIPELAGO, model.preset());
            assertEquals(definition, model.definition());
            assertEquals(centerX, model.centerX());
            assertEquals(centerY, model.centerY());
            assertEquals(scale, model.pixelsPerWorldUnit());
            assertFalse(model.applySeed(42L));
        }
    }
}
