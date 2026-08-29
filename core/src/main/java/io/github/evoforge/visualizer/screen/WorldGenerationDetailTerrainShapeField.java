package io.github.evoforge.visualizer.screen;

import io.github.evoforge.simulation.world.geometry.Shape;
import io.github.evoforge.simulation.world.spatial.WorldBounds;
import io.github.evoforge.simulation.world.terrain.shape.TerrainShapeField;
import io.github.evoforge.simulation.world.terrain.shape.TerrainSurfacePatch;
import io.github.evoforge.visualizer.VisualizerCamera;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Bounded presentation snapshot for cell-detail terrain geometry.
 *
 * <p>Large Continuum worlds expose terrain shapes lazily. Computing a previously unseen shape can
 * require many authoritative elevation reads, so the 2D renderer must not invoke that lazy field on
 * the LibGDX render thread merely because the camera crossed into {@code LOD x1}. This adapter returns
 * a safe flat presentation immediately and materializes the requested world-anchored detail range on
 * one low-priority worker after exact elevation for that same range is ready.
 *
 * <p>This is a rebuildable presentation projection only. The authoritative {@link TerrainShapeField}
 * remains unchanged and camera state never changes generated terrain facts.</p>
 */
final class WorldGenerationDetailTerrainShapeField implements TerrainShapeField {
    private static final long REFINEMENT_SETTLE_MILLIS = 180L;
    private static final ScheduledThreadPoolExecutor REFINER = new ScheduledThreadPoolExecutor(
            1,
            runnable -> {
                Thread thread = new Thread(runnable, "evoforge-world-detail-shapes");
                thread.setDaemon(true);
                thread.setPriority(Math.max(Thread.MIN_PRIORITY, Thread.NORM_PRIORITY - 1));
                return thread;
            });

    static {
        REFINER.setRemoveOnCancelPolicy(true);
    }

    private static TerrainShapeField cachedDelegate;
    private static VisualizerCamera.VisibleRange cachedVisible;
    private static TerrainShapeField cachedField;

    private static TerrainShapeField refinedDelegate;
    private static VisualizerCamera.VisibleRange refinedVisible;
    private static WorldGenerationDetailTerrainShapeField refinedField;

    private static TerrainShapeField pendingDelegate;
    private static VisualizerCamera.VisibleRange pendingVisible;
    private static long pendingToken;
    private static long refinementSequence;
    private static ScheduledFuture<?> pendingFuture;
    private static boolean refinementEnabled = true;

    private final WorldBounds bounds;
    private final VisualizerCamera.VisibleRange visible;
    private final int width;
    private final TerrainSurfacePatch[] surfaces;
    private final Shape[] shapes;
    private final long overrides;

    private WorldGenerationDetailTerrainShapeField(
            WorldBounds bounds,
            VisualizerCamera.VisibleRange visible,
            TerrainSurfacePatch[] surfaces,
            Shape[] shapes,
            long overrides) {
        this.bounds = bounds;
        this.visible = visible;
        this.width = visible.maxX() - visible.minX() + 1;
        this.surfaces = surfaces;
        this.shapes = shapes;
        this.overrides = overrides;
    }

    static synchronized TerrainShapeField preload(
            TerrainShapeField authoritative,
            VisualizerCamera.VisibleRange visible,
            boolean authoritativeElevationReady) {
        if (authoritative == null || visible == null) {
            throw new IllegalArgumentException("detail shape preload requires field and visible range");
        }
        if (matches(refinedDelegate, refinedVisible, authoritative, visible) && refinedField != null) {
            cache(authoritative, visible, refinedField);
            return refinedField;
        }

        if (cachedField == null || !matches(cachedDelegate, cachedVisible, authoritative, visible)) {
            cache(authoritative, visible, TerrainShapeField.baseline(authoritative.bounds()));
        }
        if (authoritativeElevationReady) {
            scheduleRefinement(authoritative, visible);
        } else if (pendingDelegate == authoritative) {
            cancelPending();
        }
        return cachedField;
    }

    static synchronized void invalidate(TerrainShapeField terrainShapes) {
        if (terrainShapes == null) return;
        if (cachedDelegate == terrainShapes) clearCache();
        if (refinedDelegate == terrainShapes) clearRefinement();
        if (pendingDelegate == terrainShapes) cancelPending();
    }

    /** Package-private deterministic switch for tests. */
    static synchronized void refinementEnabledForTests(boolean enabled) {
        refinementEnabled = enabled;
        if (!enabled) cancelPending();
    }

    @Override
    public WorldBounds bounds() {
        return bounds;
    }

    @Override
    public TerrainSurfacePatch surfaceAt(int x, int y) {
        int index = indexOf(x, y);
        return index >= 0 ? surfaces[index] : TerrainSurfacePatch.flatTop();
    }

    @Override
    public Shape shapeOverrideAt(int x, int y) {
        int index = indexOf(x, y);
        return index >= 0 ? shapes[index] : null;
    }

    @Override
    public long overrideCount() {
        return overrides;
    }

    @Override
    public boolean overrideCountIsExact() {
        return false;
    }

    private int indexOf(int x, int y) {
        if (x < visible.minX() || x > visible.maxX()
                || y < visible.minY() || y > visible.maxY()) {
            return -1;
        }
        return (y - visible.minY()) * width + (x - visible.minX());
    }

    private static synchronized void scheduleRefinement(
            TerrainShapeField authoritative,
            VisualizerCamera.VisibleRange visible) {
        if (!refinementEnabled) return;
        if (matches(refinedDelegate, refinedVisible, authoritative, visible)) return;
        if (matches(pendingDelegate, pendingVisible, authoritative, visible)) return;

        long token = ++refinementSequence;
        if (pendingFuture != null) pendingFuture.cancel(false);
        pendingDelegate = authoritative;
        pendingVisible = visible;
        pendingToken = token;
        pendingFuture = REFINER.schedule(
                () -> refine(authoritative, visible, token),
                REFINEMENT_SETTLE_MILLIS,
                TimeUnit.MILLISECONDS);
    }

    private static void refine(
            TerrainShapeField authoritative,
            VisualizerCamera.VisibleRange visible,
            long token) {
        synchronized (WorldGenerationDetailTerrainShapeField.class) {
            if (!pendingMatches(authoritative, visible, token)) return;
        }

        WorldGenerationDetailTerrainShapeField prepared;
        try {
            prepared = materialize(authoritative, visible, token);
            if (prepared == null) return;
        } catch (RuntimeException ignored) {
            synchronized (WorldGenerationDetailTerrainShapeField.class) {
                if (pendingToken == token) cancelPending();
            }
            return;
        }

        synchronized (WorldGenerationDetailTerrainShapeField.class) {
            if (!pendingMatches(authoritative, visible, token)) return;
            refinedDelegate = authoritative;
            refinedVisible = visible;
            refinedField = prepared;
            if (matches(cachedDelegate, cachedVisible, authoritative, visible)) {
                cachedField = prepared;
            }
            clearPendingState();
        }
    }

    private static WorldGenerationDetailTerrainShapeField materialize(
            TerrainShapeField authoritative,
            VisualizerCamera.VisibleRange visible,
            long token) {
        int width = visible.maxX() - visible.minX() + 1;
        int height = visible.maxY() - visible.minY() + 1;
        int cells = Math.multiplyExact(width, height);
        TerrainSurfacePatch[] surfaces = new TerrainSurfacePatch[cells];
        Shape[] shapes = new Shape[cells];
        long overrides = 0L;
        int cursor = 0;
        for (int y = visible.minY(); y <= visible.maxY(); y++) {
            synchronized (WorldGenerationDetailTerrainShapeField.class) {
                if (!pendingMatches(authoritative, visible, token)) return null;
            }
            for (int x = visible.minX(); x <= visible.maxX(); x++, cursor++) {
                Shape shape = authoritative.shapeOverrideAt(x, y);
                shapes[cursor] = shape;
                surfaces[cursor] = authoritative.surfaceAt(x, y);
                if (shape != null) overrides++;
            }
        }
        return new WorldGenerationDetailTerrainShapeField(
                authoritative.bounds(),
                visible,
                surfaces,
                shapes,
                overrides);
    }

    private static boolean pendingMatches(
            TerrainShapeField authoritative,
            VisualizerCamera.VisibleRange visible,
            long token) {
        return pendingToken == token
                && pendingDelegate == authoritative
                && visible.equals(pendingVisible);
    }

    private static boolean matches(
            TerrainShapeField storedDelegate,
            VisualizerCamera.VisibleRange storedVisible,
            TerrainShapeField requestedDelegate,
            VisualizerCamera.VisibleRange requestedVisible) {
        return storedDelegate == requestedDelegate
                && requestedVisible.equals(storedVisible);
    }

    private static void cache(
            TerrainShapeField authoritative,
            VisualizerCamera.VisibleRange visible,
            TerrainShapeField field) {
        cachedDelegate = authoritative;
        cachedVisible = visible;
        cachedField = field;
    }

    private static void clearCache() {
        cachedDelegate = null;
        cachedVisible = null;
        cachedField = null;
    }

    private static void clearRefinement() {
        refinedDelegate = null;
        refinedVisible = null;
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
        pendingToken = 0L;
        pendingFuture = null;
    }
}
