package io.github.evoforge.visualizer.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.github.evoforge.simulation.world.atlas.ElevationField;
import io.github.evoforge.simulation.world.genesis.GenerationRevision;
import io.github.evoforge.simulation.world.geometry.Shape;
import io.github.evoforge.simulation.world.spatial.WorldBounds;
import io.github.evoforge.simulation.world.terrain.shape.TerrainShapeField;
import io.github.evoforge.simulation.world.terrain.shape.TerrainShapeGenerationStage;
import io.github.evoforge.visualizer.VisualizerCamera;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class WorldGenerationExactDetailTilesTest {

    @Test
    void exactTileFrameMatchesCanonicalShapeFitAndSurvivesPanReturn() throws Exception {
        AtomicInteger bulkCalls = new AtomicInteger();
        AtomicInteger pointCalls = new AtomicInteger();
        ElevationField source = deterministicElevation(bulkCalls, pointCalls);
        TerrainShapeField canonical = TerrainShapeGenerationStage
                .forRevision(GenerationRevision.V15)
                .generate(source);
        bulkCalls.set(0);
        pointCalls.set(0);

        VisualizerCamera.VisibleRange first = new VisualizerCamera.VisibleRange(40, 139, 50, 149);
        VisualizerCamera.VisibleRange moved = new VisualizerCamera.VisibleRange(68, 167, 50, 149);

        try {
            assertNull(WorldGenerationExactDetailTiles.request(source, first));
            WorldGenerationExactDetailTiles.awaitIdleForTests(5_000L);
            WorldGenerationExactDetailTiles.DetailFrame firstFrame =
                    WorldGenerationExactDetailTiles.request(source, first);
            assertNotNull(firstFrame);
            assertMatches(source, canonical, firstFrame, first);
            long firstChecksum = checksum(firstFrame, first);

            WorldGenerationExactDetailTiles.request(source, moved);
            WorldGenerationExactDetailTiles.awaitIdleForTests(5_000L);
            WorldGenerationExactDetailTiles.DetailFrame movedFrame =
                    WorldGenerationExactDetailTiles.request(source, moved);
            assertNotNull(movedFrame);
            assertMatches(source, canonical, movedFrame, moved);

            WorldGenerationExactDetailTiles.DetailFrame returned =
                    WorldGenerationExactDetailTiles.request(source, first);
            if (returned == null) {
                WorldGenerationExactDetailTiles.awaitIdleForTests(5_000L);
                returned = WorldGenerationExactDetailTiles.request(source, first);
            }
            assertNotNull(returned);
            assertEquals(firstChecksum, checksum(returned, first));

            assertEquals(0, pointCalls.get(),
                    "x1 tile loading must use bounded bulk elevation requests only");
        } finally {
            WorldGenerationExactDetailTiles.invalidate(source);
        }
    }

    private static void assertMatches(
            ElevationField source,
            TerrainShapeField canonical,
            WorldGenerationExactDetailTiles.DetailFrame frame,
            VisualizerCamera.VisibleRange visible) {
        for (int y = visible.minY(); y <= visible.maxY(); y += 11) {
            for (int x = visible.minX(); x <= visible.maxX(); x += 13) {
                assertEquals(source.elevationSubunitsAt(x, y), frame.elevation().elevationSubunitsAt(x, y));
                assertEquals(canonical.surfaceAt(x, y), frame.shapes().surfaceAt(x, y));
                Shape expected = canonical.shapeOverrideAt(x, y);
                Shape actual = frame.shapes().shapeOverrideAt(x, y);
                assertEquals(expected == null, actual == null);
                if (expected != null) assertEquals(expected.getClass(), actual.getClass());
            }
        }
    }

    private static long checksum(
            WorldGenerationExactDetailTiles.DetailFrame frame,
            VisualizerCamera.VisibleRange visible) {
        long hash = 0xcbf29ce484222325L;
        for (int y = visible.minY(); y <= visible.maxY(); y += 7) {
            for (int x = visible.minX(); x <= visible.maxX(); x += 7) {
                hash ^= frame.elevation().elevationSubunitsAt(x, y);
                hash *= 0x100000001b3L;
                hash ^= frame.shapes().surfaceAt(x, y).hashCode();
                hash *= 0x100000001b3L;
            }
        }
        return hash;
    }

    private static ElevationField deterministicElevation(
            AtomicInteger bulkCalls,
            AtomicInteger pointCalls) {
        return new ElevationField() {
            private final WorldBounds bounds = new WorldBounds(0, 255, 0, 255, -96, 96);

            @Override public WorldBounds bounds() { return bounds; }

            @Override
            public int elevationAt(int x, int y) {
                return Math.toIntExact(Math.floorDiv(elevationSubunitsAt(x, y), SUBUNITS_PER_CELL));
            }

            @Override
            public long elevationSubunitsAt(int x, int y) {
                pointCalls.incrementAndGet();
                return value(x, y);
            }

            @Override
            public void fillElevationSubunits(
                    int minX,
                    int minY,
                    int sampleWidth,
                    int sampleHeight,
                    long step,
                    long[] target) {
                bulkCalls.incrementAndGet();
                int cursor = 0;
                for (int sy = 0; sy < sampleHeight; sy++) {
                    int y = Math.toIntExact((long) minY + sy * step);
                    for (int sx = 0; sx < sampleWidth; sx++, cursor++) {
                        int x = Math.toIntExact((long) minX + sx * step);
                        target[cursor] = value(x, y);
                    }
                }
            }

            private long value(int x, int y) {
                double broad = 4.0 * StrictMath.sin(x * 0.055) + 3.0 * StrictMath.cos(y * 0.047);
                double ridge = 0.7 * StrictMath.sin((x + y) * 0.19);
                return Math.round((8.0 + broad + ridge) * SUBUNITS_PER_CELL);
            }
        };
    }
}
