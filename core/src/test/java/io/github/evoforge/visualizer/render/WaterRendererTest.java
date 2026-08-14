package io.github.evoforge.visualizer.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

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
        assertTrue(full < 0.75f);
        assertEquals(full, excess);
    }

    @Test
    void invalidOrUnavailableCapacityDrawsNoWater() {
        assertEquals(0f, WaterRenderer.opacityFor(100_000, 0));
        assertEquals(0f, WaterRenderer.opacityFor(-1, 1_000_000));
    }
}
