package io.github.evoforge.visualizer.visual;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class ProceduralWaterArtTest {

    @Test
    void calmPulseBreathesSymmetricallyWithoutDirectionalPhase() {
        int[] pulse = new int[ProceduralWaterArt.FRAME_COUNT];
        for (int frame = 0; frame < pulse.length; frame++) {
            pulse[frame] = ProceduralWaterArt.calmPulseLevel(frame);
        }

        assertArrayEquals(new int[] {0, 1, 2, 3, 2, 1}, pulse);
    }

    @Test
    void calmPulseRepeatsBySharedAnimationFrame() {
        assertEquals(
                ProceduralWaterArt.calmPulseLevel(2),
                ProceduralWaterArt.calmPulseLevel(2 + ProceduralWaterArt.FRAME_COUNT));
        assertEquals(
                ProceduralWaterArt.calmPulseLevel(5),
                ProceduralWaterArt.calmPulseLevel(-1));
    }
}
