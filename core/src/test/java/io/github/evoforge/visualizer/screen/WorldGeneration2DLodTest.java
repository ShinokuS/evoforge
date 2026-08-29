package io.github.evoforge.visualizer.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

final class WorldGeneration2DLodTest {

    @AfterEach
    void resetTuning() {
        WorldGeneration2DLod.resetTuning();
    }

    @Test
    void ordinaryZoomDoesNotEnterExactPerCellRendererForExpensiveProductionViews() {
        assertTrue(WorldGeneration2DLod.stride(147, 147) >= 2);
        assertTrue(WorldGeneration2DLod.stride(80, 80) >= 2);
        assertTrue(WorldGeneration2DLod.stride(32, 32) >= 2);
        assertTrue(WorldGeneration2DLod.stride(9, 9) >= 2);
        assertEquals(1, WorldGeneration2DLod.stride(8, 8));
    }

    @Test
    void sampledWorkRemainsBoundedAtReportedSixHundredWorldViewport() {
        int stride = WorldGeneration2DLod.stride(147, 147);
        assertEquals(2, stride);
        assertEquals(5_476L, WorldGeneration2DLod.sampledCells(147, 147, stride));
    }
}
