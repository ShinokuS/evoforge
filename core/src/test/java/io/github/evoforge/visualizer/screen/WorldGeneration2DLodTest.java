package io.github.evoforge.visualizer.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class WorldGeneration2DLodTest {
    @Test
    void closeInspectionKeepsOneSamplePerCellWithinDetailedBudget() {
        assertEquals(1, WorldGeneration2DLod.stride(90, 90));
        assertEquals(8_100L, WorldGeneration2DLod.sampledCells(90, 90, 1));
    }

    @Test
    void detailedModeStepsToX2ImmediatelyBeyondBudget() {
        int stride = WorldGeneration2DLod.stride(95, 95);
        long sampled = WorldGeneration2DLod.sampledCells(95, 95, stride);

        assertEquals(2, stride);
        assertEquals(2_304L, sampled);
        assertTrue(WorldGeneration2DLod.MAX_SAMPLES < WorldGeneration2DLod.MAX_DETAILED_CELLS);
    }

    @Test
    void mediumOverviewStaysInsideGpuSubmissionBudget() {
        int stride = WorldGeneration2DLod.stride(300, 300);
        long sampled = WorldGeneration2DLod.sampledCells(300, 300, stride);

        assertEquals(4, stride);
        assertEquals(5_625L, sampled);
        assertTrue(sampled <= WorldGeneration2DLod.MAX_SAMPLES);
    }

    @Test
    void largeOverviewReducesSamplingWorkAggressively() {
        int stride = WorldGeneration2DLod.stride(600, 600);
        long sampled = WorldGeneration2DLod.sampledCells(600, 600, stride);

        assertEquals(8, stride);
        assertEquals(5_625L, sampled);
        assertTrue(sampled <= WorldGeneration2DLod.MAX_SAMPLES);
    }

    @Test
    void veryLargeOverviewRemainsBounded() {
        int stride = WorldGeneration2DLod.stride(2048, 2048);
        long sampled = WorldGeneration2DLod.sampledCells(2048, 2048, stride);

        assertTrue(stride >= 27);
        assertTrue(sampled <= WorldGeneration2DLod.MAX_SAMPLES);
    }
}
