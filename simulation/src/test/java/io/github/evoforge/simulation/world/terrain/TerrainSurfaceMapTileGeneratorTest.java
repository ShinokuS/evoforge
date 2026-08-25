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
    void flatLandUsesCenteredGhostBorderAndProducesStableLandPixels() {
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

        assertEquals(100, evaluations.get(), "8x8 tile should sample only one 10x10 centered raw-Z lattice");
        int first = tile.luminanceUnsigned(0, 0);
        assertTrue(first >= 128, "positive Terrain Z must remain on the land side of the packed map code");
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                assertEquals(first, tile.luminanceUnsigned(x, y));
            }
        }
    }

    @Test
    void realHeightGradientsCreateVisibleHillshadeWithoutChangingLandOwnership() {
        ContinuousTerrainSurface ridged = (x, y) -> 350.0d
                + 95.0d * Math.sin(x / 2_200.0d)
                + 45.0d * Math.sin(y / 3_100.0d);
        TerrainSurfaceMapTileGenerator generator = new TerrainSurfaceMapTileGenerator(
                new ContinuumWorldDomain(1_000_000L, 1_000_000L),
                ridged,
                16);

        ContinuumMapTile tile = generator.generate(new ContinuumMapTileKey(8, 0L, 0L, 0L));
        int minimumShade = 7;
        int maximumShade = 0;
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                int value = tile.luminanceUnsigned(x, y);
                assertTrue(value >= 128, "hillshade must not recolor positive Terrain as ocean");
                int shade = value & 0x07;
                minimumShade = Math.min(minimumShade, shade);
                maximumShade = Math.max(maximumShade, shade);
            }
        }
        assertTrue(maximumShade > minimumShade, "real gradients must occupy more than one hillshade band");
    }

    @Test
    void hillshadeBitsCannotTurnOneElevationBandIntoAnotherHueBand() {
        ContinuousTerrainSurface tilted = (x, y) -> 420.0d + x * 0.008d;
        TerrainSurfaceMapTileGenerator generator = new TerrainSurfaceMapTileGenerator(
                new ContinuumWorldDomain(200_000L, 200_000L),
                tilted,
                16);
        ContinuumMapTile tile = generator.generate(new ContinuumMapTileKey(6, 0L, 0L, 0L));

        int expectedHeightBand = -1;
        for (int y = 1; y < 15; y++) {
            for (int x = 1; x < 15; x++) {
                int code = tile.luminanceUnsigned(x, y);
                int heightBand = (code & 0x78) >>> 3;
                if (expectedHeightBand < 0) expectedHeightBand = heightBand;
                assertEquals(expectedHeightBand, heightBand,
                        "slope lighting is confined to the three shade bits");
            }
        }
    }

    @Test
    void scaleAwareSourceReceivesRequestedLodSpacingInsteadOfBeingPointAliased() {
        RecordingMapSurface surface = new RecordingMapSurface();
        TerrainSurfaceMapTileGenerator generator = new TerrainSurfaceMapTileGenerator(
                new ContinuumWorldDomain(1_000_000L, 1_000_000L),
                surface,
                8);

        generator.generate(new ContinuumMapTileKey(9, 0L, 0L, 0L));

        assertEquals(512L, surface.lastSpacing);
        assertTrue(surface.mapReads > 0);
        assertEquals(0, surface.exactReads, "derived map must use the scale-aware projection when available");
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

    private static final class RecordingMapSurface
            implements ContinuousTerrainSurface, TerrainSurfaceMapObservation {
        private long lastSpacing;
        private int mapReads;
        private int exactReads;

        @Override
        public double surfaceZAt(long x, long y) {
            exactReads++;
            return 300.0d;
        }

        @Override
        public double surfaceZForMapAt(long x, long y, long sampleSpacing) {
            lastSpacing = sampleSpacing;
            mapReads++;
            return 300.0d;
        }
    }
}
