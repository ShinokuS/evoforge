package io.github.evoforge.visualizer.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.evoforge.simulation.world.atlas.ElevationField;
import io.github.evoforge.simulation.world.spatial.WorldBounds;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class WorldGenerationElevationGridTest {

    @Test
    void largePreviewUsesOneBulkInteriorAndOnlyPointSamplesBoundaryStrips() {
        AtomicInteger bulkCalls = new AtomicInteger();
        AtomicInteger pointCalls = new AtomicInteger();
        ElevationField elevation = new ElevationField() {
            private final WorldBounds bounds = new WorldBounds(0, 999, 0, 999, -96, 96);

            @Override
            public WorldBounds bounds() {
                return bounds;
            }

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
                for (int y = 0; y < sampleHeight; y++) {
                    int worldY = Math.toIntExact((long) minY + y * step);
                    for (int x = 0; x < sampleWidth; x++, cursor++) {
                        int worldX = Math.toIntExact((long) minX + x * step);
                        target[cursor] = value(worldX, worldY);
                    }
                }
            }

            private long value(int x, int y) {
                return x * 1_000_000L + y;
            }
        };

        WorldGenerationElevationGrid grid = WorldGenerationElevationGrid.sample(elevation, 160);

        assertEquals(144, grid.width());
        assertEquals(144, grid.height());
        assertEquals(1, bulkCalls.get());
        assertEquals(287, pointCalls.get());
        assertEquals(0, grid.xAt(0));
        assertEquals(994, grid.xAt(142));
        assertEquals(999, grid.xAt(143));
        assertEquals(999, grid.yAt(143));
        assertEquals(999_000_999L, grid.elevationSubunitsAt(143, 143));

        // The immediate large-world fallback must not snap an entire seven-cell interval to one
        // source sample. A linear field should be reconstructed linearly between grid coordinates.
        assertEquals(3_000_004L, grid.presentationFallback().elevationSubunitsAt(3, 4));
    }
}
