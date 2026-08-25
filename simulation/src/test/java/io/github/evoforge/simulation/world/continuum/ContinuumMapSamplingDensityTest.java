package io.github.evoforge.simulation.world.continuum;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.evoforge.simulation.world.continuum.map.ContinuumMapViewport;
import org.junit.jupiter.api.Test;

final class ContinuumMapSamplingDensityTest {

    @Test
    void zoomRequestsFinerLodBeforeTileTexelsAreHeavilyMagnified() {
        ContinuumMapViewport viewport = new ContinuumMapViewport(
                16_000_000L,
                16_000_000L,
                128,
                17,
                1,
                1900,
                900);

        for (int index = 0; index < 36; index++) {
            int level = viewport.desiredLevel();
            if (level > 0) {
                double displayedTilePixels = viewport.tileWorldSpan(level) * viewport.pixelsPerWorldUnit();
                assertTrue(displayedTilePixels <= 200.0001d,
                        "map LOD must change before a 128-sample tile is stretched far beyond its texel density");
            }
            viewport.zoomAt(1.18d, 950d, 450d);
        }
    }
}
