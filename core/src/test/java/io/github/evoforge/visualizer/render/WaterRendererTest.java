package io.github.evoforge.visualizer.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.github.evoforge.simulation.world.space.measurement.CellVolume;
import io.github.evoforge.visualizer.visual.WaterMotion;
import io.github.evoforge.visualizer.visual.WaterOpticalDepthResolver;

final class WaterRendererTest {

    @Test
    void opacityRisesWithFiniteCellFillAndSaturatesAtCapacity() {
        int capacity = 1_000_000;

        float dry = WaterRenderer.opacityFor(0, capacity);
        float shallow = WaterRenderer.opacityFor(100_000, capacity);
        float medium = WaterRenderer.opacityFor(500_000, capacity);
        float full = WaterRenderer.opacityFor(capacity, capacity);
        float excess = WaterRenderer.opacityFor(1_500_000, capacity);

        assertEquals(0f, dry);
        assertTrue(shallow > dry);
        assertTrue(medium > shallow);
        assertTrue(full > medium);
        assertTrue(full < 0.9f);
        assertEquals(full, excess);
    }

    @Test
    void surfaceOpacityStartsNonZeroAndDeepWaterBecomesEffectivelyOpaque() {
        float film = WaterRenderer.surfaceOpacityForDepth(1);
        float halfCell = WaterRenderer.surfaceOpacityForDepth(CellVolume.FULL / 2);
        float oneCell = WaterRenderer.surfaceOpacityForDepth(CellVolume.FULL);
        float twoCells = WaterRenderer.surfaceOpacityForDepth(CellVolume.FULL * 2);
        float deep = WaterRenderer.surfaceOpacityForDepth(
                WaterOpticalDepthResolver.MAX_OPTICAL_DEPTH);
        float deeper = WaterRenderer.surfaceOpacityForDepth(Integer.MAX_VALUE);

        assertTrue(film >= 0.18f);
        assertTrue(halfCell > film);
        assertTrue(oneCell > halfCell);
        assertTrue(twoCells > oneCell);
        assertTrue(deep >= 0.95f, "deep Water should visually hide the bottom");
        assertEquals(deep, deeper);
    }

    @Test
    void invalidOrUnavailableCapacityDrawsNoWater() {
        assertEquals(0f, WaterRenderer.opacityFor(100_000, 0));
        assertEquals(0f, WaterRenderer.opacityFor(-1, 1_000_000));
        assertEquals(0f, WaterRenderer.surfaceOpacityForDepth(0));
    }

    @Test
    void lowerCutawayLayersRemainReadableButAreVisuallyDepthCoded() {
        assertEquals(1f, WaterRenderer.depthOpacity(0));
        assertTrue(WaterRenderer.depthOpacity(1) < 1f);
        assertTrue(WaterRenderer.depthOpacity(2) < WaterRenderer.depthOpacity(1));
        assertTrue(WaterRenderer.depthOpacity(6) < WaterRenderer.depthOpacity(3));
        assertEquals(
                WaterRenderer.depthOpacity(6),
                WaterRenderer.depthOpacity(12));
    }

    @Test
    void calmWaterUsesOneStaticFrameAcrossNeighbouringCellsAndWallClockPhases() {
        int first = WaterRenderer.presentationFrame(
                WaterMotion.CALM,
                0,
                0,
                4,
                2);
        int second = WaterRenderer.presentationFrame(
                WaterMotion.CALM,
                17,
                -9,
                1,
                5);

        assertEquals(first, second);
        assertEquals(0, first);
    }
}
