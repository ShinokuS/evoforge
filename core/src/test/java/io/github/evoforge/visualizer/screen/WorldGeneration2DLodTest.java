package io.github.evoforge.visualizer.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    void fastDefaultEntersCellDetailBeforeMicroscopeZoom() {
        assertTrue(WorldGeneration2DLod.stride(147, 147) >= 2);
        assertEquals(1, WorldGeneration2DLod.stride(90, 90));
        assertEquals(1, WorldGeneration2DLod.stride(96, 80));
    }

    @Test
    void performanceSliderCanKeepSeveralHundredCellsPerAxisAtX1() {
        WorldGeneration2DLod.detailedCellBudget(90_000L);
        assertEquals(1, WorldGeneration2DLod.stride(300, 300));

        WorldGeneration2DLod.detailedCellBudget(WorldGeneration2DLod.MAX_DETAILED_CELLS);
        assertEquals(1, WorldGeneration2DLod.stride(500, 500));
    }

    @Test
    void x1UsesHysteresisInsteadOfFlappingAtTheBudgetBoundary() {
        WorldGeneration2DLod.detailedCellBudget(90_000L);
        assertEquals(1, WorldGeneration2DLod.stride(300, 300));
        assertEquals(1, WorldGeneration2DLod.stride(320, 300));
        assertTrue(WorldGeneration2DLod.stride(380, 320) > 1);
    }

    @Test
    void overviewUsesNestedPowerOfTwoLevelsInsteadOfEveryIntegerStride() {
        assertEquals(8, WorldGeneration2DLod.stride(600, 600));
        assertEquals(4, WorldGeneration2DLod.stride(300, 300));
        assertEquals(2, WorldGeneration2DLod.stride(150, 150));
    }

    @Test
    void raisedDetailedRangeCannotSkipTheX2Parent() {
        WorldGeneration2DLod.detailedCellBudget(90_000L);

        assertEquals(4, WorldGeneration2DLod.stride(500, 500));
        assertEquals(2, WorldGeneration2DLod.stride(400, 400));
        assertEquals(2, WorldGeneration2DLod.stride(300, 300));
        assertEquals(1, WorldGeneration2DLod.stride(290, 290));
    }

    @Test
    void maximumDetailedRangeStillDescendsThroughEveryNestedLevel() {
        WorldGeneration2DLod.detailedCellBudget(WorldGeneration2DLod.MAX_DETAILED_CELLS);

        assertEquals(4, WorldGeneration2DLod.stride(800, 800));
        assertEquals(2, WorldGeneration2DLod.stride(650, 650));
        assertEquals(1, WorldGeneration2DLod.stride(480, 480));
    }

    @Test
    void x1PrewarmGetsTheWholeAdjacentX2Band() {
        WorldGeneration2DLod.detailedCellBudget(90_000L);

        assertTrue(WorldGeneration2DLod.detailWarmupUseful(424, 424));
        assertFalse(WorldGeneration2DLod.detailWarmupUseful(425, 425));
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
