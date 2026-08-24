package io.github.evoforge.visualizer.continuum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.evoforge.simulation.world.geophysics.MacroGeophysicsDefinition;
import io.github.evoforge.simulation.world.geophysics.MacroGeophysicsPreset;
import org.junit.jupiter.api.Test;

final class ContinuumMapInspectorModelTest {

    @Test
    void standardViewerUsesBalancedMacroProfile() {
        try (ContinuumMapInspectorModel model = ContinuumMapInspectorModel.standard(1600, 900)) {
            assertEquals(MacroGeophysicsPreset.BALANCED, model.preset());
            assertEquals(MacroGeophysicsPreset.BALANCED.definition(), model.definition());
            assertEquals("balanced", model.profileName());
        }
    }

    @Test
    void viewerCanInspectAContrastingAuthoredProfileWithoutChangingWorldSeed() {
        try (ContinuumMapInspectorModel model = ContinuumMapInspectorModel.standard(
                1600,
                900,
                MacroGeophysicsPreset.ARCHIPELAGO)) {
            assertEquals(MacroGeophysicsPreset.ARCHIPELAGO, model.preset());
            assertEquals(MacroGeophysicsPreset.ARCHIPELAGO.definition(), model.definition());
            assertEquals(ContinuumMapInspectorModel.WORLD_SEED, 0x45A1_0F0E_2026L);
        }
    }

    @Test
    void customDefinitionRebuildsMapSourceWithoutMovingTheCamera() {
        try (ContinuumMapInspectorModel model = ContinuumMapInspectorModel.standard(1600, 900)) {
            model.panPixels(-173d, 91d);
            model.zoomAt(1.22d, 710d, 390d);
            double centerX = model.centerX();
            double centerY = model.centerY();
            double scale = model.pixelsPerWorldUnit();
            MacroGeophysicsDefinition custom = MacroGeophysicsDefinition.of(0.78d, 0.52d, 0.44d, 0.63d, 0.31d);

            assertTrue(model.applyDefinition(custom));

            assertEquals(custom, model.definition());
            assertNull(model.preset());
            assertEquals("custom", model.profileName());
            assertEquals(centerX, model.centerX());
            assertEquals(centerY, model.centerY());
            assertEquals(scale, model.pixelsPerWorldUnit());
            assertEquals(ContinuumMapInspectorModel.WORLD_SEED, 0x45A1_0F0E_2026L);
        }
    }
}
