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
    void detailedModeStepsToX2BeforeTheOldHighCostFringe() {
        int stride = WorldGeneration2DLod.stride(129, 128);
        long sampled = WorldGeneration2DLod.sampledCells(129, 128, stride);

        assertEquals(2, stride);
        assertEquals(4_160L, sampled);
        assertTrue(WorldGeneration2DLod.MAX_DETAILED_CELLS < WorldGeneration2DLod.MAX_SAMPLES);
    }

    @Test
    void mediumOverviewAlreadyReducesSubmissionWork() {
        int stride = WorldGeneration2DLod.stride(300, 300);
        long sampled = WorldGeneration2DLod.sampledCells(300, 300, stride);

        assertEquals(3, stride);
        assertEquals(10_000L, sampled);
    }

    @Test
    void largeOverviewReducesSamplingWorkAggressively() {
        int stride = WorldGeneration2DLod.stride(600, 600);
        long sampled = WorldGeneration2DLod.sampledCells(600, 600, stride);

        assertEquals(5, stride);
        assertEquals(14_400L, sampled);
        assertTrue(sampled <= WorldGeneration2DLod.MAX_SAMPLES);
    }

    @Test
    void veryLargeOverviewRemainsBounded() {
        int stride = WorldGeneration2DLod.stride(2048, 2048);
        long sampled = WorldGeneration2DLod.sampledCells(2048, 2048, stride);

        assertTrue(stride >= 15);
        assertTrue(sampled <= WorldGeneration2DLod.MAX_SAMPLES);
    }
}
