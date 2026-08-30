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
 * Stable read-through elevation projection for the 2D world-generation inspector.
 *
 * <p>Overview LODs use a world-anchored sampled lattice and may refine that same lattice from the
 * authoritative V15 field after camera motion settles. Refinement never replaces a visible overview
 * in one frame: once the authoritative lattice is resident, elevation geomorphs from the already
 * visible fallback to the authoritative values over a short presentation-only interval. This keeps
 * coastlines and relief from popping while preserving immutable source terrain.</p>
 *
 * <p>Cell-detail ({@code stride == 1}) is different: it never uses overview interpolation as final
 * terrain. Exact x1 elevation and V15 shapes are published atomically by
 * {@link WorldGenerationExactDetailTiles}. Until that immutable tile frame is ready, x1 reads a
 * stable world-anchored x2 parent so the renderer cannot invent one-cell terraces from an
 * interpolated fallback.</p>
 *
 * <p>When x2 approaches the configured detail budget, the exact world tiles are prewarmed in the
 * background. Raising Detailed range can therefore expose hundreds of cells per axis without making
 * the render thread perform the corresponding unit-resolution terrain work at the LOD transition.</p>
 *
 * <p>All projections, blends and caches are presentation-only and rebuildable. Camera state never
 * changes simulation or Genesis facts.</p>
 */
final class WorldGenerationOverviewElevationField implements ElevationField {
    private static final long REFINEMENT_SETTLE_MILLIS = 180L;
    private static final long REFINEMENT_BLEND_NANOS = TimeUnit.MILLISECONDS.toNanos(140L);
    private static final int CELL_DETAIL_PARENT_STRIDE = 2;
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

    private static ElevationField transitionDelegate;
    private static VisualizerCamera.VisibleRange transitionVisible;
    private static int transitionStride;
    private static WorldGenerationOverviewElevationField transitionFrom;
    private static WorldGenerationOverviewElevationField transitionTo;
    private static long transitionStartedNanos;

    private static ElevationField pendingDelegate;
    private static VisualizerCamera.VisibleRange pendingVisible;
    private static int pendingStride;
    private static long pendingToken;
    private static long refinementSequence;
    private static ScheduledFuture<?> pendingFuture;
    private static boolean refinementEnabled = true;

    private static ElevationField detailFallbackDelegate;
    private static VisualizerCamera.VisibleRange detailFallbackVisible;
    private static ElevationField detailFallbackField;

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
        if (transitionDelegate == authoritative) clearTransition();
        if (pendingDelegate == authoritative) cancelPending();
        if (detailFallbackDelegate == authoritative) clearDetailFallback();
    }

    static synchronized boolean hasFallback(ElevationField elevation) {
        return elevation != null && FALLBACKS.containsKey(elevation);
    }

    static synchronized boolean isRefined(
            ElevationField elevation,
            VisualizerCamera.VisibleRange visible,
            int stride) {
        if (elevation == null || visible == null || stride < 1) return false;
        if (stride == 1) {
            return WorldGenerationExactDetailTiles.isReady(elevation, visible);
        }
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
        if (presentationStride == 1) {
            if (!FALLBACKS.containsKey(elevation)) {
                return elevation;
            }
            WorldGenerationExactDetailTiles.DetailFrame exact =
                    WorldGenerationExactDetailTiles.request(elevation, visible);
            if (exact != null) return exact.elevation();
            return stableCellDetailParent(elevation, visible);
        }

        if (presentationStride == 2 && FALLBACKS.containsKey(elevation)) {
            int width = visible.maxX() - visible.minX() + 1;
            int height = visible.maxY() - visible.minY() + 1;
            if (WorldGeneration2DLod.detailWarmupUseful(width, height)) {
                WorldGenerationExactDetailTiles.prewarm(elevation, visible);
            }
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
            return refinedPresentation(elevation, stableVisible, presentationStride);
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

    static synchronized void prewarmCellDetail(
            ElevationField elevation,
            VisualizerCamera.VisibleRange visible) {
        if (elevation == null || visible == null || !FALLBACKS.containsKey(elevation)) return;
        WorldGenerationExactDetailTiles.prewarm(elevation, visible);
    }

    static synchronized void invalidate(ElevationField elevation) {
        if (elevation == null) return;
        FALLBACKS.remove(elevation);
        WorldGenerationExactDetailTiles.invalidate(elevation);
        if (cachedDelegate == elevation) clearCache();
        if (refinedDelegate == elevation) clearRefinement();
        if (transitionDelegate == elevation) clearTransition();
        if (pendingDelegate == elevation) cancelPending();
        if (detailFallbackDelegate == elevation) clearDetailFallback();
    }

    /** Package-private deterministic switch for tests which assert synchronous fallback reads. */
    static synchronized void refinementEnabledForTests(boolean enabled) {
        refinementEnabled = enabled;
        WorldGenerationExactDetailTiles.enabledForTests(enabled);
        if (!enabled) {
            cancelPending();
            clearTransition();
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

    private static ElevationField stableCellDetailParent(
            ElevationField elevation,
            VisualizerCamera.VisibleRange visible) {
        VisualizerCamera.VisibleRange stable = WorldGeneration2DLod.alignVisibleRange(
                visible,
                elevation.bounds(),
                CELL_DETAIL_PARENT_STRIDE);
        if (detailFallbackField != null
                && detailFallbackDelegate == elevation
                && stable.equals(detailFallbackVisible)) {
            return detailFallbackField;
        }
        ElevationField fallback = FALLBACKS.getOrDefault(elevation, elevation);
        detailFallbackDelegate = elevation;
        detailFallbackVisible = stable;
        detailFallbackField = new BlockParentField(
                fallback,
                elevation.bounds(),
                CELL_DETAIL_PARENT_STRIDE);
        return detailFallbackField;
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
        if (!refinementEnabled || stride <= 1) return;
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
            WorldGenerationOverviewElevationField visibleBeforeRefinement =
                    matches(cachedDelegate, cachedVisible, cachedStride, authoritative, visible, stride)
                            ? cachedField
                            : null;
            refinedDelegate = authoritative;
            refinedVisible = visible;
            refinedStride = stride;
            refinedField = prepared;
            if (visibleBeforeRefinement != null && visibleBeforeRefinement != prepared) {
                beginTransition(
                        authoritative,
                        visible,
                        stride,
                        visibleBeforeRefinement,
                        prepared);
            } else {
                clearTransition();
            }
            clearPendingState();
        }
    }

    private static ElevationField refinedPresentation(
            ElevationField authoritative,
            VisualizerCamera.VisibleRange visible,
            int stride) {
        if (transitionFrom != null
                && transitionTo != null
                && matches(
                        transitionDelegate,
                        transitionVisible,
                        transitionStride,
                        authoritative,
                        visible,
                        stride)) {
            long elapsed = Math.max(0L, System.nanoTime() - transitionStartedNanos);
            if (elapsed < REFINEMENT_BLEND_NANOS) {
                double linear = elapsed / (double) REFINEMENT_BLEND_NANOS;
                double smooth = linear * linear * (3d - 2d * linear);
                return new BlendedElevationField(transitionFrom, transitionTo, smooth);
            }
            clearTransition();
        }
        cache(authoritative, visible, stride, refinedField);
        return refinedField;
    }

    private static void beginTransition(
            ElevationField authoritative,
            VisualizerCamera.VisibleRange visible,
            int stride,
            WorldGenerationOverviewElevationField from,
            WorldGenerationOverviewElevationField to) {
        transitionDelegate = authoritative;
        transitionVisible = visible;
        transitionStride = stride;
        transitionFrom = from;
        transitionTo = to;
        transitionStartedNanos = System.nanoTime();
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

    private static void clearTransition() {
        transitionDelegate = null;
        transitionVisible = null;
        transitionStride = 0;
        transitionFrom = null;
        transitionTo = null;
        transitionStartedNanos = 0L;
    }

    private static void clearDetailFallback() {
        detailFallbackDelegate = null;
        detailFallbackVisible = null;
        detailFallbackField = null;
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

    /** Stable x2 parent used only while an atomic x1 tile frame is still loading. */
    private static final class BlockParentField implements ElevationField {
        private final ElevationField source;
        private final WorldBounds bounds;
        private final int stride;

        private BlockParentField(ElevationField source, WorldBounds bounds, int stride) {
            this.source = source;
            this.bounds = bounds;
            this.stride = stride;
        }

        @Override public WorldBounds bounds() { return bounds; }

        @Override
        public int elevationAt(int x, int y) {
            return Math.toIntExact(Math.floorDiv(elevationSubunitsAt(x, y), SUBUNITS_PER_CELL));
        }

        @Override
        public long elevationSubunitsAt(int x, int y) {
            if (!contains(x, y)) throw new IllegalArgumentException("detail fallback read outside bounds");
            int startX = alignedBlockStart(bounds.minX(), x, stride);
            int startY = alignedBlockStart(bounds.minY(), y, stride);
            int endX = Math.min(bounds.maxX(), startX + stride - 1);
            int endY = Math.min(bounds.maxY(), startY + stride - 1);
            int sampleX = startX + (endX - startX) / 2;
            int sampleY = startY + (endY - startY) / 2;
            return source.elevationSubunitsAt(sampleX, sampleY);
        }

        private static int alignedBlockStart(int worldMinimum, int coordinate, int stride) {
            long block = Math.floorDiv((long) coordinate - worldMinimum, stride);
            return Math.toIntExact((long) worldMinimum + block * stride);
        }
    }

    /** Immutable presentation-only morph between two already materialized views of the same world. */
    private static final class BlendedElevationField implements ElevationField {
        private final ElevationField from;
        private final ElevationField to;
        private final double alpha;

        private BlendedElevationField(ElevationField from, ElevationField to, double alpha) {
            if (from == null || to == null || !sameBounds(from.bounds(), to.bounds())) {
                throw new IllegalArgumentException("LOD blend fields must share world bounds");
            }
            this.from = from;
            this.to = to;
            this.alpha = Math.max(0d, Math.min(1d, alpha));
        }

        @Override public WorldBounds bounds() { return to.bounds(); }

        @Override
        public int elevationAt(int x, int y) {
            return Math.toIntExact(Math.floorDiv(elevationSubunitsAt(x, y), SUBUNITS_PER_CELL));
        }

        @Override
        public long elevationSubunitsAt(int x, int y) {
            long a = from.elevationSubunitsAt(x, y);
            long b = to.elevationSubunitsAt(x, y);
            return Math.round(a + (b - (double) a) * alpha);
        }

        @Override
        public void fillElevationSubunits(
                int minX,
                int minY,
                int sampleWidth,
                int sampleHeight,
                long step,
                long[] target) {
            int samples = Math.multiplyExact(sampleWidth, sampleHeight);
            if (target == null || target.length < samples) {
                throw new IllegalArgumentException("LOD blend target is too small");
            }
            long[] fromValues = new long[samples];
            long[] toValues = new long[samples];
            from.fillElevationSubunits(minX, minY, sampleWidth, sampleHeight, step, fromValues);
            to.fillElevationSubunits(minX, minY, sampleWidth, sampleHeight, step, toValues);
            for (int sample = 0; sample < samples; sample++) {
                long a = fromValues[sample];
                long b = toValues[sample];
                target[sample] = Math.round(a + (b - (double) a) * alpha);
            }
        }
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
