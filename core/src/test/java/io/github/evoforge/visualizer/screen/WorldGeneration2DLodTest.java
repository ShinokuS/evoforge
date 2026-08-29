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
    void closestReachableInspectionEntersExactRendererWithoutOpeningLargeViews() {
        assertTrue(WorldGeneration2DLod.stride(147, 147) >= 2);
        assertTrue(WorldGeneration2DLod.stride(80, 80) >= 2);
        assertTrue(WorldGeneration2DLod.stride(32, 32) >= 2);
        assertEquals(2, WorldGeneration2DLod.stride(12, 12));
        assertEquals(1, WorldGeneration2DLod.stride(11, 11));
        assertEquals(1, WorldGeneration2DLod.stride(10, 8));
    }

    @Test
    void actualDesktopCameraMinimumZoomCanReachExactLod() {
        VisualizerCamera camera = new VisualizerCamera();
        camera.resize(1_600, 765);
        camera.setView(0f, 0f, 0.25f);
        VisualizerCamera.VisibleRange visible = camera.visibleRange();
        int width = visible.maxX() - visible.minX() + 1;
        int length = visible.maxY() - visible.minY() + 1;

        assertTrue(Math.multiplyExact((long) width, length) <= WorldGeneration2DLod.detailedCellBudget());
        assertEquals(1, WorldGeneration2DLod.stride(width, length));
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
    void sampledWorkRemainsBoundedAtReportedSixHundredWorldViewport() {
        int stride = WorldGeneration2DLod.stride(147, 147);
        assertEquals(2, stride);
        assertEquals(5_476L, WorldGeneration2DLod.sampledCells(147, 147, stride));
    }
}
