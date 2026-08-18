package io.github.evoforge.visualizer.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.evoforge.simulation.world.spatial.WorldBounds;
import org.junit.jupiter.api.Test;

final class WorldGenerationPreviewSettingsTest {

    @Test
    void defaultsMatchCurrentV12PreviewInputs() {
        WorldGenerationPreviewSettings settings = new WorldGenerationPreviewSettings();
        WorldGenerationPreviewConfig snapshot = settings.snapshot();

        assertEquals(64, snapshot.width());
        assertEquals(64, snapshot.length());
        assertEquals(1L, snapshot.seed());
        assertEquals(350_000, snapshot.coveragePpm());
        assertEquals(750_000, snapshot.scalePpm());
        assertEquals(250_000, snapshot.fragmentationPpm());
        assertEquals(600_000, snapshot.reliefPpm());
        assertEquals(250_000, snapshot.localReliefPpm());
        assertEquals(4_096L, snapshot.columnCount());
        assertEquals(new WorldBounds(-32, 31, -32, 31, -12, 12), snapshot.bounds());
    }

    @Test
    void widthAndLengthAcceptArbitraryManualValuesWithinStressRange() {
        WorldGenerationPreviewSettings settings = new WorldGenerationPreviewSettings();

        settings.width(300);
        settings.length(175);

        WorldGenerationPreviewConfig snapshot = settings.snapshot();
        assertEquals(300, snapshot.width());
        assertEquals(175, snapshot.length());
        assertEquals(52_500L, snapshot.columnCount());
        assertEquals(new WorldBounds(-150, 149, -87, 87, -12, 12), snapshot.bounds());
    }

    @Test
    void snapshotIsStableWhileDraftContinuesChanging() {
        WorldGenerationPreviewSettings settings = new WorldGenerationPreviewSettings();
        WorldGenerationPreviewConfig generated = settings.snapshot();

        settings.width(1_023);
        settings.length(777);
        settings.coveragePpm(650_000);
        settings.reliefPpm(900_000);
        settings.localReliefPpm(800_000);
        settings.seed(44L);

        assertEquals(64, generated.width());
        assertEquals(64, generated.length());
        assertEquals(350_000, generated.coveragePpm());
        assertEquals(600_000, generated.reliefPpm());
        assertEquals(250_000, generated.localReliefPpm());
        assertEquals(1L, generated.seed());

        WorldGenerationPreviewConfig next = settings.snapshot();
        assertEquals(1_023, next.width());
        assertEquals(777, next.length());
        assertEquals(650_000, next.coveragePpm());
        assertEquals(900_000, next.reliefPpm());
        assertEquals(800_000, next.localReliefPpm());
        assertEquals(44L, next.seed());
    }

    @Test
    void dimensionsAcceptInclusiveInteractiveStressRange() {
        WorldGenerationPreviewSettings settings = new WorldGenerationPreviewSettings();

        settings.width(WorldGenerationPreviewSettings.MIN_HORIZONTAL_DIMENSION);
        settings.length(WorldGenerationPreviewSettings.MAX_HORIZONTAL_DIMENSION);

        assertEquals(32, settings.width());
        assertEquals(2_048, settings.length());
        assertEquals(2_048, settings.maxHorizontalDimension());
    }

    @Test
    void settingsRejectOutOfRangeDimensionsAndIntent() {
        WorldGenerationPreviewSettings settings = new WorldGenerationPreviewSettings();

        assertThrows(IllegalArgumentException.class, () -> settings.width(31));
        assertThrows(IllegalArgumentException.class, () -> settings.length(2_049));
        assertThrows(IllegalArgumentException.class, () -> settings.coveragePpm(-1));
        assertThrows(IllegalArgumentException.class, () -> settings.reliefPpm(1_000_001));
        assertThrows(IllegalArgumentException.class, () -> settings.localReliefPpm(-1));
        assertThrows(IllegalArgumentException.class, () -> settings.localReliefPpm(1_000_001));
    }
}
