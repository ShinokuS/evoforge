package io.github.evoforge.visualizer.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.evoforge.simulation.world.spatial.WorldBounds;
import org.junit.jupiter.api.Test;

final class WorldGenerationPreviewSettingsTest {

    @Test
    void defaultsMatchExistingPreviewFootprint() {
        WorldGenerationPreviewSettings settings = new WorldGenerationPreviewSettings();

        assertEquals(64, settings.width());
        assertEquals(64, settings.height());
        assertEquals(4_096L, settings.columnCount());
        assertEquals(new WorldBounds(-32, 31, -32, 31, -12, 12), settings.bounds());
    }

    @Test
    void widthAndHeightScaleIndependentlyAcrossStressPresets() {
        WorldGenerationPreviewSettings settings = new WorldGenerationPreviewSettings();

        settings.adjustWidth(1);
        settings.adjustWidth(1);
        settings.adjustHeight(-1);

        assertEquals(256, settings.width());
        assertEquals(32, settings.height());
        assertEquals(8_192L, settings.columnCount());
        assertEquals(new WorldBounds(-128, 127, -16, 15, -12, 12), settings.bounds());
    }

    @Test
    void dimensionsClampToInteractiveStressRange() {
        WorldGenerationPreviewSettings settings = new WorldGenerationPreviewSettings();

        for (int i = 0; i < 20; i++) {
            settings.adjustWidth(1);
            settings.adjustHeight(-1);
        }

        assertEquals(2_048, settings.width());
        assertEquals(32, settings.height());
        assertEquals(2_048, settings.maxHorizontalDimension());
    }
}
