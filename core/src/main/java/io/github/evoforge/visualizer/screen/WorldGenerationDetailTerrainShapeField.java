package io.github.evoforge.visualizer.screen;

import io.github.evoforge.simulation.world.atlas.ElevationField;
import io.github.evoforge.simulation.world.genesis.GenerationRevision;
import io.github.evoforge.simulation.world.geometry.Shape;
import io.github.evoforge.simulation.world.spatial.WorldBounds;
import io.github.evoforge.simulation.world.terrain.shape.TerrainShapeField;
import io.github.evoforge.simulation.world.terrain.shape.TerrainShapeGenerationStage;
import io.github.evoforge.simulation.world.terrain.shape.TerrainSurfacePatch;
import io.github.evoforge.visualizer.VisualizerCamera;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Bounded presentation snapshot for V15 cell-detail terrain geometry.
 *
 * <p>Large Continuum worlds expose terrain shapes lazily. Computing a previously unseen shape can
 * require many elevation reads, and production worlds above 2048 cells use very small point-cache
 * pages. Calling the authoritative lazy shape field from the LibGDX render thread can therefore turn
 * one {@code LOD x1} frame into thousands of tiny terrain requests.
 *
 * <p>This adapter never asks the authoritative shape field to resolve individual cells. Once the
 * corresponding exact elevation detail range has been bulk-refined, one low-priority worker fits the
 * normal V15 shape law against that already materialized in-memory elevation projection. The renderer
 * supplies a conservative influence halo around the visible cells, so the displayed interior observes
 * the same local V15 geometry law without reopening point-wise Continuum work. Until the fit is ready,
 * a flat safe presentation is returned immediately.
 *
 * <p>This is a rebuildable presentation projection only. Camera state never changes generated terrain
 * facts.</p>
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
    private static ElevationField pendingElevation;
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
            ElevationField exactPresentationElevation,
            VisualizerCamera.VisibleRange visible,
            boolean authoritativeElevationReady) {
        if (authoritative == null || exactPresentationElevation == null || visible == null) {
            throw new IllegalArgumentException(
                    "detail shape preload requires shape/elevation fields and visible range");
        }
        if (!sameBounds(authoritative.bounds(), exactPresentationElevation.bounds())) {
            throw new IllegalArgumentException("detail shape/elevation fields must share world bounds");
        }
        if (matches(refinedDelegate, refinedVisible, authoritative, visible) && refinedField != null) {
            cache(authoritative, visible, refinedField);
            return refinedField;
        }

        if (cachedField == null || !matches(cachedDelegate, cachedVisible, authoritative, visible)) {
            cache(authoritative, visible, TerrainShapeField.baseline(authoritative.bounds()));
        }
        if (authoritativeElevationReady) {
            scheduleRefinement(authoritative, exactPresentationElevation, visible);
        } else if (pendingDelegate == authoritative) {
            cancelPending();
        }
        return cachedField;
    }

    static synchronized void suspend(TerrainShapeField terrainShapes) {
        if (terrainShapes != null && pendingDelegate == terrainShapes) cancelPending();
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
            ElevationField exactPresentationElevation,
            VisualizerCamera.VisibleRange visible) {
        if (!refinementEnabled) return;
        if (matches(refinedDelegate, refinedVisible, authoritative, visible)) return;
        if (matches(pendingDelegate, pendingVisible, authoritative, visible)
                && pendingElevation == exactPresentationElevation) {
            return;
        }

        long token = ++refinementSequence;
        if (pendingFuture != null) pendingFuture.cancel(false);
        pendingDelegate = authoritative;
        pendingElevation = exactPresentationElevation;
        pendingVisible = visible;
        pendingToken = token;
        pendingFuture = REFINER.schedule(
                () -> refine(authoritative, exactPresentationElevation, visible, token),
                REFINEMENT_SETTLE_MILLIS,
                TimeUnit.MILLISECONDS);
    }

    private static void refine(
            TerrainShapeField authoritative,
            ElevationField exactPresentationElevation,
            VisualizerCamera.VisibleRange visible,
            long token) {
        synchronized (WorldGenerationDetailTerrainShapeField.class) {
            if (!pendingMatches(authoritative, exactPresentationElevation, visible, token)) return;
        }

        WorldGenerationDetailTerrainShapeField prepared;
        try {
            prepared = materialize(
                    authoritative,
                    exactPresentationElevation,
                    visible,
                    token);
            if (prepared == null) return;
        } catch (RuntimeException ignored) {
            synchronized (WorldGenerationDetailTerrainShapeField.class) {
                if (pendingToken == token) cancelPending();
            }
            return;
        }

        synchronized (WorldGenerationDetailTerrainShapeField.class) {
            if (!pendingMatches(authoritative, exactPresentationElevation, visible, token)) return;
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
            ElevationField exactPresentationElevation,
            VisualizerCamera.VisibleRange visible,
            long token) {
        WorldBounds worldBounds = authoritative.bounds();
        WorldBounds localBounds = new WorldBounds(
                visible.minX(),
                visible.maxX(),
                visible.minY(),
                visible.maxY(),
                worldBounds.minZ(),
                worldBounds.maxZ());
        ElevationField localElevation = localElevationView(
                exactPresentationElevation,
                localBounds);
        TerrainShapeField locallyFitted = TerrainShapeGenerationStage
                .forRevision(GenerationRevision.V15)
                .generate(localElevation);

        int width = visible.maxX() - visible.minX() + 1;
        int height = visible.maxY() - visible.minY() + 1;
        int cells = Math.multiplyExact(width, height);
        TerrainSurfacePatch[] surfaces = new TerrainSurfacePatch[cells];
        Shape[] shapes = new Shape[cells];
        long overrides = 0L;
        int cursor = 0;
        for (int y = visible.minY(); y <= visible.maxY(); y++) {
            synchronized (WorldGenerationDetailTerrainShapeField.class) {
                if (!pendingMatches(authoritative, exactPresentationElevation, visible, token)) return null;
            }
            for (int x = visible.minX(); x <= visible.maxX(); x++, cursor++) {
                Shape shape = locallyFitted.shapeOverrideAt(x, y);
                shapes[cursor] = shape;
                surfaces[cursor] = locallyFitted.surfaceAt(x, y);
                if (shape != null) overrides++;
            }
        }
        return new WorldGenerationDetailTerrainShapeField(
                worldBounds,
                visible,
                surfaces,
                shapes,
                overrides);
    }

    private static ElevationField localElevationView(
            ElevationField exactPresentationElevation,
            WorldBounds localBounds) {
        return new ElevationField() {
            @Override
            public WorldBounds bounds() {
                return localBounds;
            }

            @Override
            public int elevationAt(int x, int y) {
                return Math.toIntExact(Math.floorDiv(elevationSubunitsAt(x, y), SUBUNITS_PER_CELL));
            }

            @Override
            public long elevationSubunitsAt(int x, int y) {
                if (!contains(x, y)) {
                    throw new IllegalArgumentException(
                            "detail elevation coordinate outside prepared range: (" + x + ", " + y + ")");
                }
                return exactPresentationElevation.elevationSubunitsAt(x, y);
            }
        };
    }

    private static boolean pendingMatches(
            TerrainShapeField authoritative,
            ElevationField exactPresentationElevation,
            VisualizerCamera.VisibleRange visible,
            long token) {
        return pendingToken == token
                && pendingDelegate == authoritative
                && pendingElevation == exactPresentationElevation
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
        pendingElevation = null;
        pendingVisible = null;
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
}
