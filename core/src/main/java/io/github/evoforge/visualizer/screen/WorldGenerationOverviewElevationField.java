package io.github.evoforge.visualizer.screen;

import io.github.evoforge.simulation.world.atlas.ElevationField;
import io.github.evoforge.simulation.world.spatial.WorldBounds;
import io.github.evoforge.visualizer.VisualizerCamera;
import java.util.Arrays;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Read-through field for 2D overview rendering.
 *
 * <p>The regular block-centre lattice is materialized through the elevation bulk contract once. A
 * possible truncated edge block is filled with O(axis) point reads. The contour lattice is preloaded
 * the same way. Existing renderer code can then keep asking for the historical block-centre
 * coordinates without triggering duplicate terrain page materializations for surface, water and
 * contour passes.
 *
 * <p>Large previews register an already prepared bounded presentation field for the authoritative
 * elevation object. A camera move or LOD change is rendered immediately from that field, then the
 * same viewport is refined from authoritative V15 terrain on one low-priority background worker.
 * The render thread therefore never waits for production terrain materialization merely because the
 * user pans or zooms. Only the newest queued refinement is retained; obsolete camera work is dropped.
 *
 * <p>The bounded fallback is presentation-only. Once a refinement completes, the exact sampled
 * lattice replaces it for that viewport. No fallback value is written back into terrain state.</p>
 */
final class WorldGenerationOverviewElevationField implements ElevationField {
    private static final Map<ElevationField, ElevationField> FALLBACKS = new WeakHashMap<>();
    private static final ThreadPoolExecutor REFINER = new ThreadPoolExecutor(
            1,
            1,
            0L,
            TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(1),
            runnable -> {
                Thread thread = new Thread(runnable, "evoforge-world-preview-refinement");
                thread.setDaemon(true);
                thread.setPriority(Math.max(Thread.MIN_PRIORITY, Thread.NORM_PRIORITY - 1));
                return thread;
            },
            new ThreadPoolExecutor.DiscardOldestPolicy());

    private static ElevationField cachedDelegate;
    private static VisualizerCamera.VisibleRange cachedVisible;
    private static int cachedStride;
    private static WorldGenerationOverviewElevationField cachedField;

    private static ElevationField refinedDelegate;
    private static VisualizerCamera.VisibleRange refinedVisible;
    private static int refinedStride;
    private static WorldGenerationOverviewElevationField refinedField;

    private static ElevationField pendingDelegate;
    private static VisualizerCamera.VisibleRange pendingVisible;
    private static int pendingStride;
    private static long pendingToken;
    private static long refinementSequence;
    private static boolean refinementEnabled = true;

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
        if (cachedDelegate == authoritative) clearCache();
        if (refinedDelegate == authoritative) clearRefinement();
        if (pendingDelegate == authoritative) cancelPending();
    }

    static synchronized ElevationField preload(
            ElevationField elevation,
            VisualizerCamera.VisibleRange visible,
            int overviewStride) {
        if (elevation == null || visible == null || overviewStride <= 1) {
            throw new IllegalArgumentException("overview preload requires elevation/range and stride > 1");
        }
        if (matches(refinedDelegate, refinedVisible, refinedStride, elevation, visible, overviewStride)
                && refinedField != null) {
            cache(elevation, visible, overviewStride, refinedField);
            return refinedField;
        }

        ElevationField presentationSource = FALLBACKS.getOrDefault(elevation, elevation);
        if (cachedField != null
                && matches(cachedDelegate, cachedVisible, cachedStride, elevation, visible, overviewStride)) {
            if (presentationSource != elevation) {
                scheduleRefinement(elevation, visible, overviewStride);
            }
            return cachedField;
        }

        WorldGenerationOverviewElevationField prepared = materialize(
                presentationSource, visible, overviewStride);
        cache(elevation, visible, overviewStride, prepared);
        if (presentationSource != elevation) {
            scheduleRefinement(elevation, visible, overviewStride);
        }
        return prepared;
    }

    static synchronized void invalidate(ElevationField elevation) {
        if (elevation == null) return;
        FALLBACKS.remove(elevation);
        if (cachedDelegate == elevation) clearCache();
        if (refinedDelegate == elevation) clearRefinement();
        if (pendingDelegate == elevation) cancelPending();
    }

    /** Package-private deterministic switch for tests which assert synchronous fallback reads. */
    static synchronized void refinementEnabledForTests(boolean enabled) {
        refinementEnabled = enabled;
        if (!enabled) cancelPending();
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

    private static WorldGenerationOverviewElevationField materialize(
            ElevationField source,
            VisualizerCamera.VisibleRange visible,
            int stride) {
        SampleGrid overview = SampleGrid.materialize(source, visible, stride);
        SampleGrid contours = SampleGrid.materialize(source, visible, Math.multiplyExact(stride, 2));
        return new WorldGenerationOverviewElevationField(source, overview, contours);
    }

    private static synchronized void scheduleRefinement(
            ElevationField authoritative,
            VisualizerCamera.VisibleRange visible,
            int stride) {
        if (!refinementEnabled) return;
        if (matches(refinedDelegate, refinedVisible, refinedStride, authoritative, visible, stride)) return;
        if (matches(pendingDelegate, pendingVisible, pendingStride, authoritative, visible, stride)) return;

        long token = ++refinementSequence;
        pendingDelegate = authoritative;
        pendingVisible = visible;
        pendingStride = stride;
        pendingToken = token;
        REFINER.execute(() -> refine(authoritative, visible, stride, token));
    }

    private static void refine(
            ElevationField authoritative,
            VisualizerCamera.VisibleRange visible,
            int stride,
            long token) {
        WorldGenerationOverviewElevationField prepared;
        try {
            prepared = materialize(authoritative, visible, stride);
        } catch (RuntimeException ignored) {
            synchronized (WorldGenerationOverviewElevationField.class) {
                if (pendingToken == token) cancelPending();
            }
            return;
        }

        synchronized (WorldGenerationOverviewElevationField.class) {
            if (pendingToken != token
                    || pendingDelegate != authoritative
                    || !visible.equals(pendingVisible)
                    || pendingStride != stride
                    || !FALLBACKS.containsKey(authoritative)) {
                return;
            }
            refinedDelegate = authoritative;
            refinedVisible = visible;
            refinedStride = stride;
            refinedField = prepared;
            if (matches(cachedDelegate, cachedVisible, cachedStride, authoritative, visible, stride)) {
                cachedField = prepared;
            }
            pendingDelegate = null;
            pendingVisible = null;
            pendingStride = 0;
            pendingToken = 0L;
        }
    }

    private static boolean matches(
            ElevationField storedDelegate,
            VisualizerCamera.VisibleRange storedVisible,
            int storedStride,
            ElevationField requestedDelegate,
            VisualizerCamera.VisibleRange requestedVisible,
            int requestedStride) {
        return storedDelegate == requestedDelegate
                && storedStride == requestedStride
                && requestedVisible.equals(storedVisible);
    }

    private static void cache(
            ElevationField elevation,
            VisualizerCamera.VisibleRange visible,
            int stride,
            WorldGenerationOverviewElevationField field) {
        cachedDelegate = elevation;
        cachedVisible = visible;
        cachedStride = stride;
        cachedField = field;
    }

    private static void clearCache() {
        cachedDelegate = null;
        cachedVisible = null;
        cachedStride = 0;
        cachedField = null;
    }

    private static void clearRefinement() {
        refinedDelegate = null;
        refinedVisible = null;
        refinedStride = 0;
        refinedField = null;
    }

    private static void cancelPending() {
        refinementSequence++;
        pendingDelegate = null;
        pendingVisible = null;
        pendingStride = 0;
        pendingToken = 0L;
        REFINER.getQueue().clear();
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
