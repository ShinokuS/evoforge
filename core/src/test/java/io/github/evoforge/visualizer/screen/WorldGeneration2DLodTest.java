package io.github.evoforge.visualizer.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.evoforge.simulation.world.spatial.WorldBounds;
import io.github.evoforge.visualizer.VisualizerCamera;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

final class WorldGeneration2DLodTest {

    @AfterEach
    void resetTuning() {
        WorldGeneration2DLod.resetTuning();
    }

    @Test
    void closeInspectionEntersCellDetailBeforeMicroscopeZoom() {
        assertTrue(WorldGeneration2DLod.stride(147, 147) >= 2);
        assertEquals(2, WorldGeneration2DLod.stride(48, 24));
        assertEquals(1, WorldGeneration2DLod.stride(32, 32));
        assertEquals(1, WorldGeneration2DLod.stride(40, 20));
    }

    @Test
    void overviewUsesNestedPowerOfTwoLevelsInsteadOfEveryIntegerStride() {
        assertEquals(8, WorldGeneration2DLod.stride(600, 600));
        assertEquals(4, WorldGeneration2DLod.stride(300, 300));
        assertEquals(2, WorldGeneration2DLod.stride(150, 150));
    }

    @Test
    void oneCellCameraMovementKeepsTheSameWorldAnchoredLodRange() {
        WorldBounds bounds = new WorldBounds(-300, 299, -300, 299, -96, 96);
        VisualizerCamera.VisibleRange first = WorldGeneration2DLod.alignVisibleRange(
                new VisualizerCamera.VisibleRange(-147, 146, -99, 98),
                bounds,
                4);
        VisualizerCamera.VisibleRange moved = WorldGeneration2DLod.alignVisibleRange(
                new VisualizerCamera.VisibleRange(-146, 147, -98, 99),
                bounds,
                4);

        assertEquals(first, moved);
        assertEquals(new VisualizerCamera.VisibleRange(-148, 147, -100, 99), first);
    }

    @Test
    void detailCacheRangeCanBeWorldAnchoredAndHaloExpanded() {
        WorldBounds bounds = new WorldBounds(0, 2_999, 0, 2_999, -96, 96);
        VisualizerCamera.VisibleRange first = WorldGeneration2DLod.alignVisibleRange(
                new VisualizerCamera.VisibleRange(1_001, 1_030, 1_201, 1_220),
                bounds,
                16);
        VisualizerCamera.VisibleRange moved = WorldGeneration2DLod.alignVisibleRange(
                new VisualizerCamera.VisibleRange(1_002, 1_031, 1_202, 1_221),
                bounds,
                16);

        assertEquals(first, moved);
        assertEquals(
                new VisualizerCamera.VisibleRange(991, 1_040, 1_199, 1_232),
                WorldGeneration2DLod.expandVisibleRange(first, bounds, 1));
    }

    @Test
    void sampledWorkRemainsBoundedAtReportedSixHundredWorldViewport() {
        int stride = WorldGeneration2DLod.stride(147, 147);
        assertEquals(2, stride);
        assertEquals(5_476L, WorldGeneration2DLod.sampledCells(147, 147, stride));
    }
}
