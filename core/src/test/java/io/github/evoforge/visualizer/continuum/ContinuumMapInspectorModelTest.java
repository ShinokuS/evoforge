package io.github.evoforge.visualizer.continuum;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.evoforge.simulation.world.geophysics.MacroGeophysicsPreset;
import org.junit.jupiter.api.Test;

final class ContinuumMapInspectorModelTest {

    @Test
    void standardViewerUsesBalancedMacroProfile() {
        try (ContinuumMapInspectorModel model = ContinuumMapInspectorModel.standard(1600, 900)) {
            assertEquals(MacroGeophysicsPreset.BALANCED, model.preset());
        }
    }

    @Test
    void viewerCanInspectAContrastingAuthoredProfileWithoutChangingWorldSeed() {
        try (ContinuumMapInspectorModel model = ContinuumMapInspectorModel.standard(
                1600,
                900,
                MacroGeophysicsPreset.ARCHIPELAGO)) {
            assertEquals(MacroGeophysicsPreset.ARCHIPELAGO, model.preset());
            assertEquals(ContinuumMapInspectorModel.WORLD_SEED, 0x45A1_0F0E_2026L);
        }
    }
}
