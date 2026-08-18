package io.github.evoforge.visualizer.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class WorldGeneration2DLodTest {
    @Test
    void closeInspectionKeepsOneSamplePerCell() {
        assertEquals(1, WorldGeneration2DLod.stride(128, 128));
        assertEquals(16_384L, WorldGeneration2DLod.sampledCells(128, 128, 1));
    }

    @Test
    void largeOverviewReducesSamplingWork() {
        int stride = WorldGeneration2DLod.stride(600, 600);
        long sampled = WorldGeneration2DLod.sampledCells(600, 600, stride);

        assertTrue(stride > 1);
        assertTrue(sampled <= WorldGeneration2DLod.MAX_SAMPLES);
        assertTrue(sampled < 360_000L);
    }

    @Test
    void veryLargeOverviewRemainsBounded() {
        int stride = WorldGeneration2DLod.stride(2048, 2048);
        long sampled = WorldGeneration2DLod.sampledCells(2048, 2048, stride);

        assertTrue(stride >= 6);
        assertTrue(sampled <= WorldGeneration2DLod.MAX_SAMPLES);
    }
}
