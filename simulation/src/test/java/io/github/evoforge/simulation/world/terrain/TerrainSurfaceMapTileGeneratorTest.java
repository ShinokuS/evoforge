package io.github.evoforge.simulation.world.terrain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.evoforge.simulation.world.continuum.map.ContinuumMapTile;
import io.github.evoforge.simulation.world.continuum.map.ContinuumMapTileKey;
import io.github.evoforge.simulation.world.continuum.model.ContinuumWorldDomain;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class TerrainSurfaceMapTileGeneratorTest {

    @Test
    void flatLandUsesOneGhostBorderAndProducesStableLandPixels() {
        AtomicInteger evaluations = new AtomicInteger();
        ContinuousTerrainSurface flat = (x, y) -> {
            evaluations.incrementAndGet();
            return 240.0d;
        };
        TerrainSurfaceMapTileGenerator generator = new TerrainSurfaceMapTileGenerator(
                new ContinuumWorldDomain(100_000L, 100_000L),
                flat,
                8);

        ContinuumMapTile tile = generator.generate(new ContinuumMapTileKey(0, 0L, 0L, 0L));

        assertEquals(81, evaluations.get(), "8x8 tile should sample only one 9x9 raw-Z lattice");
        int first = tile.luminanceUnsigned(0, 0);
        assertTrue(first >= 128, "positive Terrain Z must remain on the land side of the map palette");
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                assertEquals(first, tile.luminanceUnsigned(x, y));
            }
        }
    }

    @Test
    void realHeightGradientsCreateVisibleHillshadeContrast() {
        ContinuousTerrainSurface ridged = (x, y) -> 350.0d
                + 210.0d * Math.sin(x / 2_200.0d)
                + 90.0d * Math.sin(y / 3_100.0d);
        TerrainSurfaceMapTileGenerator generator = new TerrainSurfaceMapTileGenerator(
                new ContinuumWorldDomain(1_000_000L, 1_000_000L),
                ridged,
                16);

        ContinuumMapTile tile = generator.generate(new ContinuumMapTileKey(8, 0L, 0L, 0L));
        int minimum = 255;
        int maximum = 0;
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                int value = tile.luminanceUnsigned(x, y);
                minimum = Math.min(minimum, value);
                maximum = Math.max(maximum, value);
                assertTrue(value >= 128, "hillshade must not recolor positive Terrain as ocean");
            }
        }
        assertTrue(maximum - minimum >= 12, "map representation must expose readable local relief contrast");
    }

    @Test
    void submergedTerrainAlwaysStaysOnOceanPaletteSide() {
        TerrainSurfaceMapTileGenerator generator = new TerrainSurfaceMapTileGenerator(
                new ContinuumWorldDomain(100_000L, 100_000L),
                (x, y) -> -700.0d + 50.0d * Math.sin(x / 1_500.0d),
                8);
        ContinuumMapTile tile = generator.generate(new ContinuumMapTileKey(6, 0L, 0L, 0L));

        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                assertTrue(tile.luminanceUnsigned(x, y) < 128);
            }
        }
    }

    @Test
    void invalidConstructionAndOutOfDomainKeysAreRejected() {
        ContinuumWorldDomain domain = new ContinuumWorldDomain(1_000L, 1_000L);
        ContinuousTerrainSurface surface = (x, y) -> 0.0d;
        assertThrows(IllegalArgumentException.class, () -> new TerrainSurfaceMapTileGenerator(null, surface, 8));
        assertThrows(IllegalArgumentException.class, () -> new TerrainSurfaceMapTileGenerator(domain, null, 8));
        assertThrows(IllegalArgumentException.class, () -> new TerrainSurfaceMapTileGenerator(domain, surface, 0));

        TerrainSurfaceMapTileGenerator generator = new TerrainSurfaceMapTileGenerator(domain, surface, 8);
        assertThrows(
                IllegalArgumentException.class,
                () -> generator.generate(new ContinuumMapTileKey(generator.maxLevel(), 99L, 0L, 0L)));
    }
}
