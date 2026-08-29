package io.github.evoforge.visualizer.screen;

import io.github.evoforge.simulation.world.atlas.ElevationField;
import io.github.evoforge.simulation.world.genesis.GenerationRevision;
import io.github.evoforge.simulation.world.geometry.Shape;
import io.github.evoforge.simulation.world.spatial.WorldBounds;
import io.github.evoforge.simulation.world.terrain.shape.TerrainShapeField;
import io.github.evoforge.simulation.world.terrain.shape.TerrainShapeGenerationStage;
import io.github.evoforge.simulation.world.terrain.shape.TerrainSurfacePatch;
import io.github.evoforge.visualizer.VisualizerCamera;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * World-anchored immutable cell-detail tiles for the legacy V15 inspector.
 *
 * <p>Entering {@code LOD x1} must never make the render thread issue thousands of tiny Continuum
 * requests, and camera movement must never change the authored result at a coordinate. Detail is
 * therefore prepared as immutable 64x64 world tiles. A requested tile group is sampled from the
 * authoritative elevation field in one bounded unit-resolution bulk request; each tile then fits
 * V15 terrain shapes from the same local elevation snapshot with the full sparse-transition halo.
 * Elevation and shape data become visible together only after every tile needed by the viewport is
 * ready.
 *
 * <p>The cache is presentation-only. Tiles are rebuildable snapshots keyed exclusively by world
 * coordinates and source identity; camera state never enters terrain generation semantics.</p>
 */
final class WorldGenerationExactDetailTiles {
    static final int TILE_SIDE = 64;
    static final int SHAPE_HALO_CELLS = 10;
    private static final int RENDER_NEIGHBOUR_HALO_CELLS = 1;
    private static final int PREFETCH_RING_CELLS = TILE_SIDE;
    private static final int MAX_READY_TILES = 128;

    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "evoforge-world-x1-tiles");
        thread.setDaemon(true);
        thread.setPriority(Math.max(Thread.MIN_PRIORITY, Thread.NORM_PRIORITY - 1));
        return thread;
    });

    private static final LinkedHashMap<TileKey, Tile> READY = new LinkedHashMap<>(32, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<TileKey, Tile> eldest) {
            return size() > MAX_READY_TILES;
        }
    };
    private static final Map<ElevationField, Boolean> KNOWN_SOURCES = new IdentityHashMap<>();

    private static ElevationField source;
    private static Set<TileKey> required = Set.of();
    private static Set<TileKey> desired = Set.of();
    private static Set<TileKey> cachedFrameKeys = Set.of();
    private static DetailFrame cachedFrame;
    private static boolean workerRunning;
    private static boolean enabled = true;

    private WorldGenerationExactDetailTiles() {
    }

    /** Returns one atomic exact detail frame, or {@code null} while the requested tiles are loading. */
    static synchronized DetailFrame request(
            ElevationField authoritative,
            VisualizerCamera.VisibleRange visible) {
        requireRequest(authoritative, visible);
        selectSource(authoritative);
        VisualizerCamera.VisibleRange renderSupport = WorldGeneration2DLod.expandVisibleRange(
                visible,
                authoritative.bounds(),
                RENDER_NEIGHBOUR_HALO_CELLS);
        required = tileKeys(renderSupport, authoritative.bounds());
        VisualizerCamera.VisibleRange prefetchSupport = WorldGeneration2DLod.expandVisibleRange(
                renderSupport,
                authoritative.bounds(),
                PREFETCH_RING_CELLS);
        desired = tileKeys(prefetchSupport, authoritative.bounds());
        cachedFrame = null;
        cachedFrameKeys = Set.of();
        startWorkerIfNeeded();
        return frameIfReady(authoritative, required);
    }

    /** Starts exactly the same tile work before the renderer crosses from x2 to x1. */
    static synchronized void prewarm(
            ElevationField authoritative,
            VisualizerCamera.VisibleRange visible) {
        requireRequest(authoritative, visible);
        selectSource(authoritative);
        VisualizerCamera.VisibleRange renderSupport = WorldGeneration2DLod.expandVisibleRange(
                visible,
                authoritative.bounds(),
                RENDER_NEIGHBOUR_HALO_CELLS);
        required = tileKeys(renderSupport, authoritative.bounds());
        VisualizerCamera.VisibleRange prefetchSupport = WorldGeneration2DLod.expandVisibleRange(
                renderSupport,
                authoritative.bounds(),
                PREFETCH_RING_CELLS);
        desired = tileKeys(prefetchSupport, authoritative.bounds());
        startWorkerIfNeeded();
    }

    static synchronized boolean isReady(
            ElevationField authoritative,
            VisualizerCamera.VisibleRange visible) {
        if (authoritative == null || visible == null || source != authoritative) return false;
        VisualizerCamera.VisibleRange support = WorldGeneration2DLod.expandVisibleRange(
                visible,
                authoritative.bounds(),
                RENDER_NEIGHBOUR_HALO_CELLS);
        return allReady(tileKeys(support, authoritative.bounds()));
    }

    static TerrainShapeField shapesFor(ElevationField detailElevation) {
        if (detailElevation instanceof DetailElevationField detail) {
            return detail.shapes;
        }
        return null;
    }

    static synchronized void invalidate(ElevationField authoritative) {
        if (authoritative == null) return;
        KNOWN_SOURCES.remove(authoritative);
        if (source != authoritative) return;
        source = null;
        required = Set.of();
        desired = Set.of();
        READY.clear();
        cachedFrame = null;
        cachedFrameKeys = Set.of();
    }

    static synchronized void enabledForTests(boolean value) {
        enabled = value;
        if (!value) {
            required = Set.of();
            desired = Set.of();
        }
    }

    static void awaitIdleForTests(long timeoutMillis) throws InterruptedException {
        long deadline = System.nanoTime() + timeoutMillis * 1_000_000L;
        while (System.nanoTime() < deadline) {
            synchronized (WorldGenerationExactDetailTiles.class) {
                if (!workerRunning) return;
            }
            Thread.sleep(5L);
        }
        throw new AssertionError("exact detail tile worker did not become idle in time");
    }

    private static void requireRequest(
            ElevationField authoritative,
            VisualizerCamera.VisibleRange visible) {
        if (authoritative == null || visible == null) {
            throw new IllegalArgumentException("exact detail tiles require elevation and visible range");
        }
    }

    private static void selectSource(ElevationField authoritative) {
        if (source == authoritative) return;
        source = authoritative;
        KNOWN_SOURCES.put(authoritative, Boolean.TRUE);
        required = Set.of();
        desired = Set.of();
        READY.clear();
        cachedFrame = null;
        cachedFrameKeys = Set.of();
    }

    private static DetailFrame frameIfReady(
            ElevationField authoritative,
            Set<TileKey> keys) {
        if (!allReady(keys)) return null;
        if (cachedFrame != null && cachedFrameKeys.equals(keys)) return cachedFrame;

        Map<TileKey, Tile> tiles = new LinkedHashMap<>();
        for (TileKey key : keys) {
            Tile tile = READY.get(key);
            if (tile == null) return null;
            tiles.put(key, tile);
        }
        Map<TileKey, Tile> immutable = Collections.unmodifiableMap(tiles);
        DetailShapeField shapes = new DetailShapeField(authoritative.bounds(), immutable);
        DetailElevationField elevation = new DetailElevationField(authoritative.bounds(), immutable, shapes);
        cachedFrame = new DetailFrame(elevation, shapes);
        cachedFrameKeys = Set.copyOf(keys);
        return cachedFrame;
    }

    private static boolean allReady(Set<TileKey> keys) {
        if (keys.isEmpty()) return false;
        for (TileKey key : keys) {
            if (!READY.containsKey(key)) return false;
        }
        return true;
    }

    private static void startWorkerIfNeeded() {
        if (!enabled || workerRunning || source == null) return;
        if (nextMissingBatch() == null) return;
        workerRunning = true;
        WORKER.execute(WorldGenerationExactDetailTiles::drain);
    }

    private static void drain() {
        while (true) {
            ElevationField capturedSource;
            List<TileKey> batch;
            synchronized (WorldGenerationExactDetailTiles.class) {
                capturedSource = source;
                batch = nextMissingBatch();
                if (!enabled || capturedSource == null || batch == null) {
                    workerRunning = false;
                    return;
                }
            }

            Map<TileKey, Tile> built;
            try {
                built = buildBatch(capturedSource, batch);
            } catch (RuntimeException ignored) {
                synchronized (WorldGenerationExactDetailTiles.class) {
                    if (source == capturedSource) {
                        for (TileKey key : batch) {
                            desired = without(desired, key);
                            required = without(required, key);
                        }
                    }
                }
                continue;
            }

            synchronized (WorldGenerationExactDetailTiles.class) {
                if (source != capturedSource) continue;
                READY.putAll(built);
                cachedFrame = null;
                cachedFrameKeys = Set.of();
            }
        }
    }

    /** Required viewport tiles are one bulk job; the prefetch ring is deliberately one tile/job. */
    private static List<TileKey> nextMissingBatch() {
        List<TileKey> missingRequired = new ArrayList<>();
        for (TileKey key : required) {
            if (!READY.containsKey(key)) missingRequired.add(key);
        }
        if (!missingRequired.isEmpty()) return missingRequired;
        for (TileKey key : desired) {
            if (!READY.containsKey(key)) return List.of(key);
        }
        return null;
    }

    private static Map<TileKey, Tile> buildBatch(
            ElevationField authoritative,
            List<TileKey> keys) {
        WorldBounds world = authoritative.bounds();
        VisualizerCamera.VisibleRange union = null;
        for (TileKey key : keys) {
            VisualizerCamera.VisibleRange support = WorldGeneration2DLod.expandVisibleRange(
                    tileRange(key, world),
                    world,
                    SHAPE_HALO_CELLS);
            union = union == null ? support : union(union, support);
        }
        if (union == null) return Map.of();

        SnapshotElevationField batchElevation = SnapshotElevationField.materialize(authoritative, union);
        Map<TileKey, Tile> built = new LinkedHashMap<>();
        for (TileKey key : keys) {
            VisualizerCamera.VisibleRange interior = tileRange(key, world);
            VisualizerCamera.VisibleRange support = WorldGeneration2DLod.expandVisibleRange(
                    interior,
                    world,
                    SHAPE_HALO_CELLS);
            SnapshotElevationField tileElevation = batchElevation.copy(support);
            TerrainShapeField shapes = TerrainShapeGenerationStage
                    .forRevision(GenerationRevision.V15)
                    .generate(tileElevation);
            long overrides = 0L;
            for (int y = interior.minY(); y <= interior.maxY(); y++) {
                for (int x = interior.minX(); x <= interior.maxX(); x++) {
                    if (shapes.shapeOverrideAt(x, y) != null) overrides++;
                }
            }
            built.put(key, new Tile(interior, tileElevation, shapes, overrides));
        }
        return built;
    }

    private static Set<TileKey> tileKeys(
            VisualizerCamera.VisibleRange visible,
            WorldBounds bounds) {
        int firstX = tileStart(bounds.minX(), visible.minX());
        int firstY = tileStart(bounds.minY(), visible.minY());
        int lastX = tileStart(bounds.minX(), visible.maxX());
        int lastY = tileStart(bounds.minY(), visible.maxY());
        Set<TileKey> keys = new LinkedHashSet<>();
        for (long y = firstY; y <= lastY; y += TILE_SIDE) {
            for (long x = firstX; x <= lastX; x += TILE_SIDE) {
                keys.add(new TileKey(Math.toIntExact(x), Math.toIntExact(y)));
            }
        }
        return Set.copyOf(keys);
    }

    private static int tileStart(int worldMinimum, int coordinate) {
        long tile = Math.floorDiv((long) coordinate - worldMinimum, TILE_SIDE);
        return Math.toIntExact((long) worldMinimum + tile * TILE_SIDE);
    }

    private static TileKey tileKeyAt(WorldBounds bounds, int x, int y) {
        return new TileKey(tileStart(bounds.minX(), x), tileStart(bounds.minY(), y));
    }

    private static VisualizerCamera.VisibleRange tileRange(TileKey key, WorldBounds bounds) {
        return new VisualizerCamera.VisibleRange(
                key.minX(),
                Math.min(bounds.maxX(), key.minX() + TILE_SIDE - 1),
                key.minY(),
                Math.min(bounds.maxY(), key.minY() + TILE_SIDE - 1));
    }

    private static VisualizerCamera.VisibleRange union(
            VisualizerCamera.VisibleRange left,
            VisualizerCamera.VisibleRange right) {
        return new VisualizerCamera.VisibleRange(
                Math.min(left.minX(), right.minX()),
                Math.max(left.maxX(), right.maxX()),
                Math.min(left.minY(), right.minY()),
                Math.max(left.maxY(), right.maxY()));
    }

    private static Set<TileKey> without(Set<TileKey> source, TileKey key) {
        if (!source.contains(key)) return source;
        Set<TileKey> copy = new LinkedHashSet<>(source);
        copy.remove(key);
        return Set.copyOf(copy);
    }

    record DetailFrame(ElevationField elevation, TerrainShapeField shapes) {
        DetailFrame {
            if (elevation == null || shapes == null) {
                throw new IllegalArgumentException("detail frame fields must not be null");
            }
        }
    }

    private record TileKey(int minX, int minY) {
    }

    private record Tile(
            VisualizerCamera.VisibleRange interior,
            SnapshotElevationField elevation,
            TerrainShapeField shapes,
            long overrides) {
    }

    private static final class DetailElevationField implements ElevationField {
        private final WorldBounds bounds;
        private final Map<TileKey, Tile> tiles;
        private final TerrainShapeField shapes;

        private DetailElevationField(
                WorldBounds bounds,
                Map<TileKey, Tile> tiles,
                TerrainShapeField shapes) {
            this.bounds = bounds;
            this.tiles = tiles;
            this.shapes = shapes;
        }

        @Override public WorldBounds bounds() { return bounds; }

        @Override
        public int elevationAt(int x, int y) {
            return Math.toIntExact(Math.floorDiv(elevationSubunitsAt(x, y), SUBUNITS_PER_CELL));
        }

        @Override
        public long elevationSubunitsAt(int x, int y) {
            Tile tile = tiles.get(tileKeyAt(bounds, x, y));
            if (tile == null) {
                throw new IllegalStateException("detail frame read escaped its immutable tile set");
            }
            return tile.elevation().elevationSubunitsAt(x, y);
        }
    }

    private static final class DetailShapeField implements TerrainShapeField {
        private final WorldBounds bounds;
        private final Map<TileKey, Tile> tiles;
        private final long overrides;

        private DetailShapeField(WorldBounds bounds, Map<TileKey, Tile> tiles) {
            this.bounds = bounds;
            this.tiles = tiles;
            long total = 0L;
            for (Tile tile : tiles.values()) total += tile.overrides();
            this.overrides = total;
        }

        @Override public WorldBounds bounds() { return bounds; }

        @Override
        public TerrainSurfacePatch surfaceAt(int x, int y) {
            Tile tile = tileAt(x, y);
            return tile.shapes().surfaceAt(x, y);
        }

        @Override
        public Shape shapeOverrideAt(int x, int y) {
            Tile tile = tileAt(x, y);
            return tile.shapes().shapeOverrideAt(x, y);
        }

        @Override public long overrideCount() { return overrides; }
        @Override public boolean overrideCountIsExact() { return false; }

        private Tile tileAt(int x, int y) {
            Tile tile = tiles.get(tileKeyAt(bounds, x, y));
            if (tile == null) {
                throw new IllegalStateException("detail shape read escaped its immutable tile set");
            }
            return tile;
        }
    }

    private static final class SnapshotElevationField implements ElevationField {
        private final WorldBounds bounds;
        private final int width;
        private final long[] values;

        private SnapshotElevationField(WorldBounds bounds, int width, long[] values) {
            this.bounds = bounds;
            this.width = width;
            this.values = values;
        }

        static SnapshotElevationField materialize(
                ElevationField source,
                VisualizerCamera.VisibleRange visible) {
            int width = visible.maxX() - visible.minX() + 1;
            int height = visible.maxY() - visible.minY() + 1;
            long[] values = new long[Math.multiplyExact(width, height)];
            source.fillElevationSubunits(
                    visible.minX(), visible.minY(), width, height, 1L, values);
            WorldBounds sourceBounds = source.bounds();
            WorldBounds localBounds = new WorldBounds(
                    visible.minX(), visible.maxX(),
                    visible.minY(), visible.maxY(),
                    sourceBounds.minZ(), sourceBounds.maxZ());
            return new SnapshotElevationField(localBounds, width, values);
        }

        SnapshotElevationField copy(VisualizerCamera.VisibleRange visible) {
            int copyWidth = visible.maxX() - visible.minX() + 1;
            int copyHeight = visible.maxY() - visible.minY() + 1;
            long[] copy = new long[Math.multiplyExact(copyWidth, copyHeight)];
            int cursor = 0;
            for (int y = visible.minY(); y <= visible.maxY(); y++) {
                int sourceOffset = (y - bounds.minY()) * width + (visible.minX() - bounds.minX());
                System.arraycopy(values, sourceOffset, copy, cursor, copyWidth);
                cursor += copyWidth;
            }
            return new SnapshotElevationField(
                    new WorldBounds(
                            visible.minX(), visible.maxX(),
                            visible.minY(), visible.maxY(),
                            bounds.minZ(), bounds.maxZ()),
                    copyWidth,
                    copy);
        }

        @Override public WorldBounds bounds() { return bounds; }

        @Override
        public int elevationAt(int x, int y) {
            return Math.toIntExact(Math.floorDiv(elevationSubunitsAt(x, y), SUBUNITS_PER_CELL));
        }

        @Override
        public long elevationSubunitsAt(int x, int y) {
            if (!contains(x, y)) {
                throw new IllegalArgumentException("coordinate outside exact detail elevation snapshot");
            }
            return values[(y - bounds.minY()) * width + (x - bounds.minX())];
        }

        @Override
        public void fillElevationSubunits(
                int minX,
                int minY,
                int sampleWidth,
                int sampleHeight,
                long step,
                long[] target) {
            if (sampleWidth <= 0 || sampleHeight <= 0 || step <= 0L || target == null
                    || target.length < Math.multiplyExact(sampleWidth, sampleHeight)) {
                throw new IllegalArgumentException("detail snapshot sample request is invalid");
            }
            int cursor = 0;
            for (int sy = 0; sy < sampleHeight; sy++) {
                int y = Math.toIntExact((long) minY + sy * step);
                for (int sx = 0; sx < sampleWidth; sx++, cursor++) {
                    int x = Math.toIntExact((long) minX + sx * step);
                    target[cursor] = elevationSubunitsAt(x, y);
                }
            }
        }
    }
}
