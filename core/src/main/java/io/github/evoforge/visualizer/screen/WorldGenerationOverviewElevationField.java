package io.github.evoforge.visualizer.screen;

import io.github.evoforge.simulation.world.atlas.ElevationField;
import io.github.evoforge.simulation.world.spatial.WorldBounds;
import io.github.evoforge.visualizer.VisualizerCamera;
import java.util.Arrays;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Read-through field for 2D overview and cell-detail presentation.
 *
 * <p>The presentation lattice is anchored to world-space LOD blocks rather than to the current camera
 * edge. Small camera movements therefore reuse the same samples instead of shifting the entire
 * rendered lattice by one cell. Queries that fall between stable coarse sample centres are linearly
 * reconstructed for presentation, so camera movement does not snap between neighbouring samples.
 *
 * <p>Large previews register an already prepared bounded presentation field for the authoritative
 * elevation object. A camera move or LOD change is rendered immediately from that field. Authoritative
 * V15 refinement is deliberately debounced until the camera has remained on the same world-anchored
 * range for a short interval. This applies to stride {@code 1} as well: entering cell-detail rendering
 * must never turn the render thread into an authoritative terrain worker.
 *
 * <p>The bounded fallback and interpolation are presentation-only. Once a stable refinement completes,
 * authoritative sampled values back the same world-anchored lattice. Nothing is written into terrain
 * state.</p>
 */
final class WorldGenerationOverviewElevationField implements ElevationField {
    private static final long REFINEMENT_SETTLE_MILLIS = 180L;
    private static final Map<ElevationField, ElevationField> FALLBACKS = new WeakHashMap<>();
    private static final ScheduledThreadPoolExecutor REFINER = new ScheduledThreadPoolExecutor(
            1,
            runnable -> {
                Thread thread = new Thread(runnable, "evoforge-world-preview-refinement");
                thread.setDaemon(true);
                thread.setPriority(Math.max(Thread.MIN_PRIORITY, Thread.NORM_PRIORITY - 1));
                return thread;
            });

    static {
        REFINER.setRemoveOnCancelPolicy(true);
    }

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
    private static ScheduledFuture<?> pendingFuture;
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

    static synchronized boolean hasFallback(ElevationField elevation) {
        return elevation != null && FALLBACKS.containsKey(elevation);
    }

    static synchronized boolean isRefined(
            ElevationField elevation,
            VisualizerCamera.VisibleRange visible,
            int stride) {
        if (elevation == null || visible == null || stride < 1) return false;
        VisualizerCamera.VisibleRange stableVisible = WorldGeneration2DLod.alignVisibleRange(
                visible,
                elevation.bounds(),
                stride);
        return refinedField != null
                && matches(
                        refinedDelegate,
                        refinedVisible,
                        refinedStride,
                        elevation,
                        stableVisible,
                        stride);
    }

    static synchronized ElevationField preload(
            ElevationField elevation,
            VisualizerCamera.VisibleRange visible,
            int presentationStride) {
        if (elevation == null || visible == null || presentationStride < 1) {
            throw new IllegalArgumentException(
                    "presentation preload requires elevation/range and stride >= 1");
        }
        VisualizerCamera.VisibleRange stableVisible = WorldGeneration2DLod.alignVisibleRange(
                visible,
                elevation.bounds(),
                presentationStride);
        if (matches(
                        refinedDelegate,
                        refinedVisible,
                        refinedStride,
                        elevation,
                        stableVisible,
                        presentationStride)
                && refinedField != null) {
            cache(elevation, stableVisible, presentationStride, refinedField);
            return refinedField;
        }

        ElevationField presentationSource = FALLBACKS.getOrDefault(elevation, elevation);
        if (cachedField != null
                && matches(
                        cachedDelegate,
                        cachedVisible,
                        cachedStride,
                        elevation,
                        stableVisible,
                        presentationStride)) {
            if (presentationSource != elevation) {
                scheduleRefinement(elevation, stableVisible, presentationStride);
            }
            return cachedField;
        }

        WorldGenerationOverviewElevationField prepared = materialize(
                presentationSource,
                stableVisible,
                presentationStride);
        cache(elevation, stableVisible, presentationStride, prepared);
        if (presentationSource != elevation) {
            scheduleRefinement(elevation, stableVisible, presentationStride);
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
        if (value != null) return value;
        value = overview.interpolate(x, y);
        if (value != null) return value;
        value = contours.interpolate(x, y);
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
        VisualizerCamera.VisibleRange overviewVisible = WorldGeneration2DLod.alignVisibleRange(
                visible,
                source.bounds(),
                stride);
        int contourStride = Math.multiplyExact(stride, 2);
        VisualizerCamera.VisibleRange contourVisible = WorldGeneration2DLod.alignVisibleRange(
                visible,
                source.bounds(),
                contourStride);
        SampleGrid overview = SampleGrid.materialize(source, overviewVisible, stride);
        SampleGrid contours = SampleGrid.materialize(source, contourVisible, contourStride);
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
        if (pendingFuture != null) pendingFuture.cancel(false);
        pendingDelegate = authoritative;
        pendingVisible = visible;
        pendingStride = stride;
        pendingToken = token;
        pendingFuture = REFINER.schedule(
                () -> refine(authoritative, visible, stride, token),
                REFINEMENT_SETTLE_MILLIS,
                TimeUnit.MILLISECONDS);
    }

    private static void refine(
            ElevationField authoritative,
            VisualizerCamera.VisibleRange visible,
            int stride,
            long token) {
        synchronized (WorldGenerationOverviewElevationField.class) {
            if (!pendingMatches(authoritative, visible, stride, token)) return;
        }

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
            if (!pendingMatches(authoritative, visible, stride, token)
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
            clearPendingState();
        }
    }

    private static boolean pendingMatches(
            ElevationField authoritative,
            VisualizerCamera.VisibleRange visible,
            int stride,
            long token) {
        return pendingToken == token
                && pendingDelegate == authoritative
                && visible.equals(pendingVisible)
                && pendingStride == stride;
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
        if (pendingFuture != null) pendingFuture.cancel(false);
        clearPendingState();
    }

    private static void clearPendingState() {
        pendingDelegate = null;
        pendingVisible = null;
        pendingStride = 0;
        pendingToken = 0L;
        pendingFuture = null;
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
                        xAxis.regularCount(),
                        yAxis.regularCount())];
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

            if (xAxis.hasBoundarySample() && yAxis.regularCount() > 0) {
                int boundaryX = width - 1;
                long[] boundaryColumn = new long[yAxis.regularCount()];
                elevation.fillElevationSubunits(
                        xAxis.coordinates()[boundaryX],
                        yAxis.coordinates()[0],
                        1,
                        yAxis.regularCount(),
                        stride,
                        boundaryColumn);
                for (int y = 0; y < yAxis.regularCount(); y++) {
                    values[y * width + boundaryX] = boundaryColumn[y];
                }
            }
            if (yAxis.hasBoundarySample() && xAxis.regularCount() > 0) {
                int boundaryY = height - 1;
                long[] boundaryRow = new long[xAxis.regularCount()];
                elevation.fillElevationSubunits(
                        xAxis.coordinates()[0],
                        yAxis.coordinates()[boundaryY],
                        xAxis.regularCount(),
                        1,
                        stride,
                        boundaryRow);
                System.arraycopy(boundaryRow, 0, values, boundaryY * width, xAxis.regularCount());
            }
            if (xAxis.hasBoundarySample() && yAxis.hasBoundarySample()) {
                long[] corner = new long[1];
                elevation.fillElevationSubunits(
                        xAxis.coordinates()[width - 1],
                        yAxis.coordinates()[height - 1],
                        1,
                        1,
                        stride,
                        corner);
                values[(height - 1) * width + width - 1] = corner[0];
            }

            if (xAxis.regularCount() == 0 || yAxis.regularCount() == 0) {
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        long[] sample = new long[1];
                        elevation.fillElevationSubunits(
                                xAxis.coordinates()[x],
                                yAxis.coordinates()[y],
                                1,
                                1,
                                stride,
                                sample);
                        values[y * width + x] = sample[0];
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

        Long interpolate(int x, int y) {
            if (xCoordinates.length == 0 || yCoordinates.length == 0) return null;
            if (x < xCoordinates[0]
                    || x > xCoordinates[xCoordinates.length - 1]
                    || y < yCoordinates[0]
                    || y > yCoordinates[yCoordinates.length - 1]) {
                return null;
            }
            Bracket bx = bracket(xCoordinates, x);
            Bracket by = bracket(yCoordinates, y);
            long v00 = values[by.lower() * xCoordinates.length + bx.lower()];
            if (bx.lower() == bx.upper() && by.lower() == by.upper()) return v00;
            long v10 = values[by.lower() * xCoordinates.length + bx.upper()];
            long v01 = values[by.upper() * xCoordinates.length + bx.lower()];
            long v11 = values[by.upper() * xCoordinates.length + bx.upper()];
            double tx = fraction(x, xCoordinates[bx.lower()], xCoordinates[bx.upper()]);
            double ty = fraction(y, yCoordinates[by.lower()], yCoordinates[by.upper()]);
            double top = v00 + (v10 - (double) v00) * tx;
            double bottom = v01 + (v11 - (double) v01) * tx;
            return Math.round(top + (bottom - top) * ty);
        }

        private static Bracket bracket(int[] coordinates, int value) {
            int index = Arrays.binarySearch(coordinates, value);
            if (index >= 0) return new Bracket(index, index);
            int insertion = -index - 1;
            if (insertion <= 0) return new Bracket(0, 0);
            if (insertion >= coordinates.length) {
                int last = coordinates.length - 1;
                return new Bracket(last, last);
            }
            return new Bracket(insertion - 1, insertion);
        }

        private static double fraction(int value, int lower, int upper) {
            if (lower == upper) return 0d;
            return (value - (double) lower) / (upper - (double) lower);
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
        private record Bracket(int lower, int upper) {}
    }
}
