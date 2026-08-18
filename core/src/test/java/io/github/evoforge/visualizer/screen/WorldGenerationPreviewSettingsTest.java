package io.github.evoforge.visualizer.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        assertFalse(settings.randomSeedOnGenerate());
        assertEquals(350_000, snapshot.coveragePpm());
        assertEquals(750_000, snapshot.scalePpm());
        assertEquals(250_000, snapshot.fragmentationPpm());
        assertEquals(600_000, snapshot.reliefPpm());
        assertEquals(450_000, snapshot.localReliefPpm());
        assertEquals(500_000, snapshot.landformScalePpm());
        assertEquals(350_000, snapshot.ruggednessPpm());
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
        settings.landformScalePpm(250_000);
        settings.ruggednessPpm(900_000);
        settings.seed(44L);

        assertEquals(64, generated.width());
        assertEquals(64, generated.length());
        assertEquals(350_000, generated.coveragePpm());
        assertEquals(600_000, generated.reliefPpm());
        assertEquals(450_000, generated.localReliefPpm());
        assertEquals(500_000, generated.landformScalePpm());
        assertEquals(350_000, generated.ruggednessPpm());
        assertEquals(1L, generated.seed());

        WorldGenerationPreviewConfig next = settings.snapshot();
        assertEquals(1_023, next.width());
        assertEquals(777, next.length());
        assertEquals(650_000, next.coveragePpm());
        assertEquals(900_000, next.reliefPpm());
        assertEquals(800_000, next.localReliefPpm());
        assertEquals(250_000, next.landformScalePpm());
        assertEquals(900_000, next.ruggednessPpm());
        assertEquals(44L, next.seed());
    }

    @Test
    void randomSeedModeChangesOnlyTheSeedUsedForTheNextSnapshot() {
        WorldGenerationPreviewSettings settings = new WorldGenerationPreviewSettings();
        settings.seed(17L);

        assertEquals(17L, settings.prepareSeedForGeneration(() -> 99L));
        assertEquals(17L, settings.seed());

        settings.randomSeedOnGenerate(true);
        assertTrue(settings.randomSeedOnGenerate());
        assertEquals(-4_321L, settings.prepareSeedForGeneration(() -> -4_321L));
        assertEquals(-4_321L, settings.seed());
        assertEquals(-4_321L, settings.snapshot().seed());

        settings.randomSeedOnGenerate(false);
        assertEquals(-4_321L, settings.prepareSeedForGeneration(() -> 123L));
        assertEquals(-4_321L, settings.seed());
    }

    @Test
    void randomSeedResolutionRejectsMissingSource() {
        WorldGenerationPreviewSettings settings = new WorldGenerationPreviewSettings();
        assertThrows(IllegalArgumentException.class, () -> settings.prepareSeedForGeneration(null));
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
        assertThrows(IllegalArgumentException.class, () -> settings.landformScalePpm(-1));
        assertThrows(IllegalArgumentException.class, () -> settings.landformScalePpm(1_000_001));
        assertThrows(IllegalArgumentException.class, () -> settings.ruggednessPpm(-1));
        assertThrows(IllegalArgumentException.class, () -> settings.ruggednessPpm(1_000_001));
    }
}
