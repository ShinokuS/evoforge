package io.github.evoforge.visualizer.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import io.github.evoforge.simulation.world.atlas.ElevationField;
import io.github.evoforge.simulation.world.spatial.WorldBounds;
import io.github.evoforge.visualizer.VisualizerCamera;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class WorldGenerationOverviewElevationFieldTest {

    @Test
    void overviewSurfaceWaterAndContourReadsReuseBulkLatticesAndBoundaryStrips() {
        AtomicInteger bulkCalls = new AtomicInteger();
        AtomicInteger pointCalls = new AtomicInteger();
        ElevationField source = countingField(bulkCalls, pointCalls);

        ElevationField cached = WorldGenerationOverviewElevationField.preload(
                source,
                new VisualizerCamera.VisibleRange(0, 999, 0, 999),
                14);

        assertEquals(8, bulkCalls.get());
        assertEquals(0, pointCalls.get());
        int beforeCachedReads = pointCalls.get();
        assertEquals(7_000_007L, cached.elevationSubunitsAt(7, 7));
        assertEquals(14_000_014L, cached.elevationSubunitsAt(14, 14));
        assertEquals(beforeCachedReads, pointCalls.get());

        assertEquals(0L, cached.elevationSubunitsAt(0, 0));
        assertEquals(beforeCachedReads + 1, pointCalls.get());
        WorldGenerationOverviewElevationField.invalidate(source);
    }

    @Test
    void oneCellCameraMovementInsideSameLodBlocksReusesThePreparedOverview() {
        AtomicInteger bulkCalls = new AtomicInteger();
        AtomicInteger pointCalls = new AtomicInteger();
        ElevationField source = countingField(bulkCalls, pointCalls);

        ElevationField first = WorldGenerationOverviewElevationField.preload(
                source,
                new VisualizerCamera.VisibleRange(91, 929, 70, 908),
                4);
        assertEquals(2, bulkCalls.get());
        assertEquals(92_000_073L, first.elevationSubunitsAt(92, 73));
        assertEquals(0, pointCalls.get());

        ElevationField moved = WorldGenerationOverviewElevationField.preload(
                source,
                new VisualizerCamera.VisibleRange(90, 930, 69, 909),
                4);
        assertEquals(2, bulkCalls.get());
        assertEquals(92_000_073L, moved.elevationSubunitsAt(92, 73));
        assertEquals(0, pointCalls.get());
        WorldGenerationOverviewElevationField.invalidate(source);
    }

    @Test
    void preparedLargeWorldGridKeepsPanAndZoomOffAuthoritativeTerrainSynchronously() {
        AtomicInteger bulkCalls = new AtomicInteger();
        AtomicInteger pointCalls = new AtomicInteger();
        ElevationField source = countingField(bulkCalls, pointCalls);

        WorldGenerationOverviewElevationField.refinementEnabledForTests(false);
        try {
            WorldGenerationElevationGrid.sample(source, 160);
            bulkCalls.set(0);
            pointCalls.set(0);

            ElevationField first = WorldGenerationOverviewElevationField.preload(
                    source,
                    new VisualizerCamera.VisibleRange(0, 999, 0, 999),
                    8);
            first.elevationSubunitsAt(4, 4);
            first.elevationSubunitsAt(996, 996);

            ElevationField panned = WorldGenerationOverviewElevationField.preload(
                    source,
                    new VisualizerCamera.VisibleRange(91, 929, 70, 908),
                    4);
            panned.elevationSubunitsAt(94, 73);

            assertEquals(0, bulkCalls.get());
            assertEquals(0, pointCalls.get());
        } finally {
            WorldGenerationOverviewElevationField.invalidate(source);
            WorldGenerationOverviewElevationField.refinementEnabledForTests(true);
        }
    }

    @Test
    void cellDetailPreloadAlsoStaysOffAuthoritativeTerrainSynchronously() {
        AtomicInteger bulkCalls = new AtomicInteger();
        AtomicInteger pointCalls = new AtomicInteger();
        ElevationField source = countingField(bulkCalls, pointCalls);
        VisualizerCamera.VisibleRange detail = new VisualizerCamera.VisibleRange(400, 449, 500, 533);

        WorldGenerationOverviewElevationField.refinementEnabledForTests(false);
        try {
            WorldGenerationElevationGrid.sample(source, 96);
            bulkCalls.set(0);
            pointCalls.set(0);

            ElevationField cellDetail = WorldGenerationOverviewElevationField.preload(
                    source,
                    detail,
                    1);
            for (int x = detail.minX(); x <= detail.maxX(); x++) {
                for (int y = detail.minY(); y <= detail.maxY(); y++) {
                    cellDetail.elevationSubunitsAt(x, y);
                }
            }

            assertFalse(WorldGenerationOverviewElevationField.isRefined(source, detail, 1));
            assertEquals(0, bulkCalls.get());
            assertEquals(0, pointCalls.get());
        } finally {
            WorldGenerationOverviewElevationField.invalidate(source);
            WorldGenerationOverviewElevationField.refinementEnabledForTests(true);
        }
    }

    private static ElevationField countingField(
            AtomicInteger bulkCalls,
            AtomicInteger pointCalls) {
        return new ElevationField() {
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
    }
}
