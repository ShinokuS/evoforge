package io.github.evoforge.visualizer.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

final class WorldGeneration3DDetailTest {
    @AfterEach
    void reset() {
        WorldGeneration3DDetail.resetTuning();
    }

    @Test
    void defaultKeepsExistingPreviewDensity() {
        assertEquals(160, WorldGeneration3DDetail.maxAxisSamples());
        assertEquals(160, WorldGeneration3DDetail.sampleCount(2_000));
        assertEquals(120, WorldGeneration3DDetail.sampleCount(120));
    }

    @Test
    void higherSettingKeepsMoreMeshSamplesOnLargeWorlds() {
        WorldGeneration3DDetail.maxAxisSamples(240);

        assertEquals(240, WorldGeneration3DDetail.sampleCount(2_000));
    }

    @Test
    void unsafeMeshSizesAreRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> WorldGeneration3DDetail.maxAxisSamples(63));
        assertThrows(
                IllegalArgumentException.class,
                () -> WorldGeneration3DDetail.maxAxisSamples(256));
    }
}
