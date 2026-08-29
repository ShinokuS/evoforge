package io.github.evoforge.visualizer.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.evoforge.simulation.world.atlas.ElevationField;
import io.github.evoforge.simulation.world.spatial.WorldBounds;
import io.github.evoforge.visualizer.VisualizerCamera;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class WorldGenerationOverviewElevationFieldTest {

    @Test
    void overviewSurfaceWaterAndContourReadsReuseTwoBulkLattices() {
        AtomicInteger bulkCalls = new AtomicInteger();
        AtomicInteger pointCalls = new AtomicInteger();
        ElevationField source = new ElevationField() {
            private final WorldBounds bounds = new WorldBounds(0, 999, 0, 999, -96, 96);

            @Override public WorldBounds bounds() { return bounds; }
            @Override public int elevationAt(int x, int y) {
                return Math.toIntExact(Math.floorDiv(elevationSubunitsAt(x, y), SUBUNITS_PER_CELL));
            }
            @Override public long elevationSubunitsAt(int x, int y) {
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
                for (int y = 0; y < sampleHeight; y++) {
                    int worldY = Math.toIntExact((long) minY + y * step);
                    for (int x = 0; x < sampleWidth; x++, cursor++) {
                        int worldX = Math.toIntExact((long) minX + x * step);
                        target[cursor] = value(worldX, worldY);
                    }
                }
            }
            private long value(int x, int y) { return x * 1_000_000L + y; }
        };

        ElevationField cached = WorldGenerationOverviewElevationField.preload(
                source,
                new VisualizerCamera.VisibleRange(0, 999, 0, 999),
                14);

        assertEquals(2, bulkCalls.get());
        assertEquals(214, pointCalls.get());
        int beforeCachedReads = pointCalls.get();
        assertEquals(7_000_007L, cached.elevationSubunitsAt(7, 7));
        assertEquals(14_000_014L, cached.elevationSubunitsAt(14, 14));
        assertEquals(beforeCachedReads, pointCalls.get());

        assertEquals(0L, cached.elevationSubunitsAt(0, 0));
        assertEquals(beforeCachedReads + 1, pointCalls.get());
    }
}
