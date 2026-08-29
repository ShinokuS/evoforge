package io.github.evoforge.visualizer.screen;

import io.github.evoforge.simulation.world.atlas.ElevationField;
import io.github.evoforge.simulation.world.spatial.WorldBounds;
import io.github.evoforge.visualizer.VisualizerCamera;
import java.util.Arrays;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Read-through field for 2D overview rendering.
 *
 * <p>The regular block-centre lattice is materialized through the elevation bulk contract once. A
 * possible truncated edge block is filled with O(axis) point reads. The contour lattice is preloaded
 * the same way. Existing renderer code can then keep asking for the historical block-centre
 * coordinates without triggering duplicate terrain page materializations for surface, water and
 * contour passes.
 *
 * <p>Large previews may register an already prepared bounded presentation field for the exact
 * elevation object. Overview materialization then reads that field instead of reopening the
 * authoritative terrain pipeline on the render thread. The mapping is weak and presentation-only;
 * detailed {@code LOD x1} renderer reads never pass through this class and remain authoritative.</p>
 *
 * <p>The most recent stable viewport/LOD result is retained across frames. A stationary overview
 * therefore performs no terrain work after the first materialization; pan, zoom, LOD change or a new
 * elevation field invalidates the entry by identity/value key. Only one viewport is retained, so
 * preview memory stays bounded.</p>
 */
final class WorldGenerationOverviewElevationField implements ElevationField {
    private static final Map<ElevationField, ElevationField> FALLBACKS = new WeakHashMap<>();

    private static ElevationField cachedDelegate;
    private static VisualizerCamera.VisibleRange cachedVisible;
    private static int cachedStride;
    private static WorldGenerationOverviewElevationField cachedField;

    private final ElevationField delegate;
    private final SampleGrid overview;
    private final SampleGrid contours;

    private WorldGenerationOverviewElevationField(
            ElevationField delegate,
            SampleGrid overview,
            SampleGrid contours) {
        this.delegate = delegate;
        this.overview = overview;
        this.contours = contours;
    }

    static synchronized void registerFallback(
            ElevationField authoritative,
            ElevationField fallback) {
        if (authoritative == null || fallback == null) {
            throw new IllegalArgumentException("overview fallback fields must not be null");
        }
        if (!sameBounds(authoritative.bounds(), fallback.bounds())) {
            throw new IllegalArgumentException("overview fallback must share authoritative bounds");
        }
        FALLBACKS.put(authoritative, fallback);
        if (cachedDelegate == authoritative) {
            clearCache();
        }
    }

    static synchronized ElevationField preload(
            ElevationField elevation,
            VisualizerCamera.VisibleRange visible,
            int overviewStride) {
        if (elevation == null || visible == null || overviewStride <= 1) {
            throw new IllegalArgumentException("overview preload requires elevation/range and stride > 1");
        }
        if (cachedField != null
                && cachedDelegate == elevation
                && cachedStride == overviewStride
                && visible.equals(cachedVisible)) {
            return cachedField;
        }
        ElevationField presentationSource = FALLBACKS.getOrDefault(elevation, elevation);
        SampleGrid overview = SampleGrid.materialize(presentationSource, visible, overviewStride);
        int contourStride = Math.multiplyExact(overviewStride, 2);
        SampleGrid contours = SampleGrid.materialize(presentationSource, visible, contourStride);
        WorldGenerationOverviewElevationField prepared = new WorldGenerationOverviewElevationField(
                presentationSource, overview, contours);
        cachedDelegate = elevation;
        cachedVisible = visible;
        cachedStride = overviewStride;
        cachedField = prepared;
        return prepared;
    }

    static synchronized void invalidate(ElevationField elevation) {
        if (elevation == null) return;
        FALLBACKS.remove(elevation);
        if (cachedDelegate == elevation) {
            clearCache();
        }
    }

    @Override
    public WorldBounds bounds() {
        return delegate.bounds();
    }

    @Override
    public int elevationAt(int x, int y) {
        return Math.toIntExact(Math.floorDiv(elevationSubunitsAt(x, y), SUBUNITS_PER_CELL));
    }

    @Override
    public long elevationSubunitsAt(int x, int y) {
        Long value = overview.find(x, y);
        if (value != null) return value;
        value = contours.find(x, y);
        return value != null ? value : delegate.elevationSubunitsAt(x, y);
    }

    @Override
    public void fillElevationSubunits(
            int minX,
            int minY,
            int sampleWidth,
            int sampleHeight,
            long step,
            long[] target) {
        delegate.fillElevationSubunits(minX, minY, sampleWidth, sampleHeight, step, target);
    }

    private static void clearCache() {
        cachedDelegate = null;
        cachedVisible = null;
        cachedStride = 0;
        cachedField = null;
    }

    private static boolean sameBounds(WorldBounds left, WorldBounds right) {
        return left.minX() == right.minX()
                && left.maxX() == right.maxX()
                && left.minY() == right.minY()
                && left.maxY() == right.maxY()
                && left.minZ() == right.minZ()
                && left.maxZ() == right.maxZ();
    }

    private static final class SampleGrid {
        private final int[] xCoordinates;
        private final int[] yCoordinates;
        private final long[] values;

        private SampleGrid(int[] xCoordinates, int[] yCoordinates, long[] values) {
            this.xCoordinates = xCoordinates;
            this.yCoordinates = yCoordinates;
            this.values = values;
        }

        static SampleGrid materialize(
                ElevationField elevation,
                VisualizerCamera.VisibleRange visible,
                int stride) {
            Axis xAxis = axis(visible.minX(), visible.maxX(), stride);
            Axis yAxis = axis(visible.minY(), visible.maxY(), stride);
            int width = xAxis.coordinates().length;
            int height = yAxis.coordinates().length;
            long[] values = new long[Math.multiplyExact(width, height)];

            if (xAxis.regularCount() > 0 && yAxis.regularCount() > 0) {
                long[] regular = new long[Math.multiplyExact(
                        xAxis.regularCount(), yAxis.regularCount())];
                elevation.fillElevationSubunits(
                        xAxis.coordinates()[0],
                        yAxis.coordinates()[0],
                        xAxis.regularCount(),
                        yAxis.regularCount(),
                        stride,
                        regular);
                for (int y = 0; y < yAxis.regularCount(); y++) {
                    System.arraycopy(
                            regular,
                            y * xAxis.regularCount(),
                            values,
                            y * width,
                            xAxis.regularCount());
                }
            }

            if (xAxis.hasBoundarySample()) {
                int boundaryX = width - 1;
                int worldX = xAxis.coordinates()[boundaryX];
                for (int y = 0; y < yAxis.regularCount(); y++) {
                    values[y * width + boundaryX] = elevation.elevationSubunitsAt(
                            worldX, yAxis.coordinates()[y]);
                }
            }
            if (yAxis.hasBoundarySample()) {
                int boundaryY = height - 1;
                int worldY = yAxis.coordinates()[boundaryY];
                for (int x = 0; x < xAxis.regularCount(); x++) {
                    values[boundaryY * width + x] = elevation.elevationSubunitsAt(
                            xAxis.coordinates()[x], worldY);
                }
            }
            if (xAxis.hasBoundarySample() && yAxis.hasBoundarySample()) {
                values[(height - 1) * width + width - 1] = elevation.elevationSubunitsAt(
                        xAxis.coordinates()[width - 1], yAxis.coordinates()[height - 1]);
            }

            if (xAxis.regularCount() == 0 || yAxis.regularCount() == 0) {
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        values[y * width + x] = elevation.elevationSubunitsAt(
                                xAxis.coordinates()[x], yAxis.coordinates()[y]);
                    }
                }
            }
            return new SampleGrid(xAxis.coordinates(), yAxis.coordinates(), values);
        }

        Long find(int x, int y) {
            int sampleX = Arrays.binarySearch(xCoordinates, x);
            if (sampleX < 0) return null;
            int sampleY = Arrays.binarySearch(yCoordinates, y);
            if (sampleY < 0) return null;
            return values[sampleY * xCoordinates.length + sampleX];
        }

        private static Axis axis(int minimum, int maximum, int stride) {
            int length = maximum - minimum + 1;
            int regularCount = length / stride;
            int remainder = length % stride;
            boolean boundary = remainder != 0;
            int[] coordinates = new int[regularCount + (boundary ? 1 : 0)];
            for (int block = 0; block < regularCount; block++) {
                coordinates[block] = minimum + block * stride + stride / 2;
            }
            if (boundary) {
                int blockStart = minimum + regularCount * stride;
                coordinates[coordinates.length - 1] = blockStart + remainder / 2;
            }
            return new Axis(coordinates, regularCount, boundary);
        }

        private record Axis(int[] coordinates, int regularCount, boolean hasBoundarySample) {}
    }
}
