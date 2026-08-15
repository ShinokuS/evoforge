package io.github.evoforge.visualizer.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class RainRendererTest {

    @Test
    void rainDensityIsBoundedAndScalesWithIntensity() {
        int dryEdge = RainRenderer.activeStreakCount(0f);
        int medium = RainRenderer.activeStreakCount(0.5f);
        int heavy = RainRenderer.activeStreakCount(1f);

        assertTrue(dryEdge > 0);
        assertTrue(medium > dryEdge);
        assertTrue(heavy > medium);
        assertEquals(RainRenderer.MAX_STREAKS, heavy);
    }

    @Test
    void intensityIsClampedForPresentationBudget() {
        assertEquals(
                RainRenderer.activeStreakCount(0f),
                RainRenderer.activeStreakCount(-3f));
        assertEquals(
                RainRenderer.MAX_STREAKS,
                RainRenderer.activeStreakCount(5f));
    }
}
