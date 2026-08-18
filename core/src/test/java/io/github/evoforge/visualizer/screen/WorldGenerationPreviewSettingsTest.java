package io.github.evoforge.visualizer.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.evoforge.simulation.world.spatial.WorldBounds;
import org.junit.jupiter.api.Test;

final class WorldGenerationPreviewSettingsTest {

    @Test
    void defaultsMatchCurrentV10PreviewInputs() {
        WorldGenerationPreviewSettings settings = new WorldGenerationPreviewSettings();
        WorldGenerationPreviewConfig snapshot = settings.snapshot();

        assertEquals(64, snapshot.width());
        assertEquals(64, snapshot.height());
        assertEquals(1L, snapshot.seed());
        assertEquals(350_000, snapshot.coveragePpm());
        assertEquals(750_000, snapshot.scalePpm());
        assertEquals(250_000, snapshot.fragmentationPpm());
        assertEquals(600_000, snapshot.reliefPpm());
        assertEquals(4_096L, snapshot.columnCount());
        assertEquals(new WorldBounds(-32, 31, -32, 31, -12, 12), snapshot.bounds());
    }

    @Test
    void widthAndHeightRemainIndependentStressPresets() {
        WorldGenerationPreviewSettings settings = new WorldGenerationPreviewSettings();

        settings.width(256);
        settings.height(32);

        WorldGenerationPreviewConfig snapshot = settings.snapshot();
        assertEquals(256, snapshot.width());
        assertEquals(32, snapshot.height());
        assertEquals(8_192L, snapshot.columnCount());
        assertEquals(new WorldBounds(-128, 127, -16, 15, -12, 12), snapshot.bounds());
    }

    @Test
    void snapshotIsStableWhileDraftContinuesChanging() {
        WorldGenerationPreviewSettings settings = new WorldGenerationPreviewSettings();
        WorldGenerationPreviewConfig generated = settings.snapshot();

        settings.width(1024);
        settings.coveragePpm(650_000);
        settings.reliefPpm(900_000);
        settings.seed(44L);

        assertEquals(64, generated.width());
        assertEquals(350_000, generated.coveragePpm());
        assertEquals(600_000, generated.reliefPpm());
        assertEquals(1L, generated.seed());

        WorldGenerationPreviewConfig next = settings.snapshot();
        assertEquals(1024, next.width());
        assertEquals(650_000, next.coveragePpm());
        assertEquals(900_000, next.reliefPpm());
        assertEquals(44L, next.seed());
    }

    @Test
    void legacyDimensionSteppingStillClampsToStressRange() {
        WorldGenerationPreviewSettings settings = new WorldGenerationPreviewSettings();

        for (int i = 0; i < 20; i++) {
            settings.adjustWidth(1);
            settings.adjustHeight(-1);
        }

        assertEquals(2_048, settings.width());
        assertEquals(32, settings.height());
        assertEquals(2_048, settings.maxHorizontalDimension());
    }

    @Test
    void settingsRejectUnsupportedDimensionsAndOutOfRangeIntent() {
        WorldGenerationPreviewSettings settings = new WorldGenerationPreviewSettings();

        assertThrows(IllegalArgumentException.class, () -> settings.width(100));
        assertThrows(IllegalArgumentException.class, () -> settings.coveragePpm(-1));
        assertThrows(IllegalArgumentException.class, () -> settings.reliefPpm(1_000_001));
    }
}
