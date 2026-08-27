package io.github.evoforge.simulation.world.terrain.genesis;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class LegacyV15RandomTest {

    @Test
    void reproducesCapturedLegacyV15SamplesBitExactly() {
        assertEquals(
                0x128e6678f2ad1efdL,
                new LegacyV15Random(0L).sampleElevation("world:landmass", 0L, 0L, 0L));
        assertEquals(
                0xa515e8608b693ed5L,
                new LegacyV15Random(1L).sampleElevation("world:v12-uplift", 12L, 34L, 0L));
        assertEquals(
                0xd602a3cdaa44de1fL,
                new LegacyV15Random(76_558_044_635_174L)
                        .sampleElevation("world:v12-landform-feature", -3L, 7L, 2L));
        assertEquals(
                0xa4798247f81e8088L,
                new LegacyV15Random(-42L)
                        .sampleElevation("world:v14-phase-geography", 1_234L, -5_678L, 0L));
    }
}
