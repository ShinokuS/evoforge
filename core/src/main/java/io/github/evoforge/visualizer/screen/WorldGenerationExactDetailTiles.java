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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * World-anchored immutable cell-detail tiles for the V15 inspector.
 *
 * <p>Exact detail is authored in 64x64 world tiles, never in camera-relative rectangles. Elevation
 * and terrain shapes for one tile come from the same unit-resolution elevation snapshot and become
 * ready together. Visible tiles are published atomically; a one-tile prefetch ring continues in the
 * background so small pans and micro-zooms reuse the same exact tile objects.</p>
 *
 * <p>The exact cached frame is never modified for LOD effects. When the visualizer prewarms x1 from
 * the adjacent x2 band, only the first presentation of that already-complete frame is geomorphed from
 * a world-anchored x2 parent derived from the exact snapshot itself. The short morph is therefore
 * presentation-only, deterministic, free of authoritative reads, and cannot expose partially loaded
 * terrain. Subsequent pans use exact cached frames immediately.</p>
 *
 * <p>Tile generation is presentation work only. It is bounded by the requested viewport and does not
 * change authoritative terrain or make camera state part of Genesis.</p>
 */
final class WorldGenerationExactDetailTiles {
    static final int TILE_SIDE = 64;
    static final int SHAPE_HALO_CELLS = 10;
    private static final int RENDER_NEIGHBOUR_HALO_CELLS = 1;
    private static final int PREFETCH_RING_CELLS = TILE_SIDE;
    private static final int MAX_READY_TILES = 128;
    private static final long FIRST_PUBLISH_BLEND_NANOS = TimeUnit.MILLISECONDS.toNanos(120L);

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

    private static ElevationField source;
    private static Set<TileKey> required = Set.of();
    private static Set<TileKey> desired = Set.of();
    private static Set<TileKey> cachedFrameKeys = Set.of();
    private static DetailFrame cachedFrame;
    private static boolean workerRunning;
    private static boolean enabled = true;
    private static boolean blendFirstPublish;
    private static boolean firstPublishEstablished;
    private static long firstPublishStartedNanos;

    private WorldGenerationExactDetailTiles() {
    }

    /** Returns a complete immutable frame when every currently visible tile is ready. */
    static synchronized DetailFrame request(
            ElevationField authoritative,
            VisualizerCamera.VisibleRange visible) {
        requireRequest(authoritative, visible);
        selectSource(authoritative);
        updateTargets(authoritative, visible);

        DetailFrame exact;
        if (cachedFrame != null && cachedFrameKeys.containsAll(required)) {
            startWorkerIfNeeded();
            exact = cachedFrame;
        } else {
            startWorkerIfNeeded();
            if (!allReady(required)) return null;
            exact = buildFrame(authoritative, required);
        }
        return presentationFrame(exact);
    }

    /** Starts the same exact tile work before x1 becomes the selected presentation level. */
    static synchronized void prewarm(
            ElevationField authoritative,
            VisualizerCamera.VisibleRange visible) {
        requireRequest(authoritative, visible);
        selectSource(authoritative);
        blendFirstPublish = true;
        updateTargets(authoritative, visible);
        startWorkerIfNeeded();
    }

    static synchronized boolean isReady(
            ElevationField authoritative,
            VisualizerCamera.VisibleRange visible) {
        if (authoritative == null || visible == null || source != authoritative) return false;
        Set<TileKey> visibleKeys = requiredKeys(authoritative, visible);
        return (cachedFrame != null && cachedFrameKeys.containsAll(visibleKeys))
                || allReady(visibleKeys);
    }

    static TerrainShapeField shapesFor(ElevationField detailElevation) {
        if (detailElevation instanceof DetailElevationField detail) return detail.shapes;
        if (detailElevation instanceof BlendedElevationField blend
                && blend.to instanceof DetailElevationField detail) {
            return detail.shapes;
        }
        return null;
    }

    static synchronized void invalidate(ElevationField authoritative) {
        if (authoritative == null || source != authoritative) return;
        source = null;
        required = Set.of();
        desired = Set.of();
        READY.clear();
        cachedFrame = null;
        cachedFrameKeys = Set.of();
        resetFirstPublish();
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
        required = Set.of();
        desired = Set.of();
        READY.clear();
        cachedFrame = null;
        cachedFrameKeys = Set.of();
        resetFirstPublish();
    }

    private static void resetFirstPublish() {
        blendFirstPublish = false;
        firstPublishEstablished = false;
        firstPublishStartedNanos = 0L;
    }

    private static void updateTargets(
            ElevationField authoritative,
            VisualizerCamera.VisibleRange visible) {
        required = requiredKeys(authoritative, visible);
        VisualizerCamera.VisibleRange renderSupport = WorldGeneration2DLod.expandVisibleRange(
                visible,
                authoritative.bounds(),
                RENDER_NEIGHBOUR_HALO_CELLS);
        VisualizerCamera.VisibleRange prefetchSupport = WorldGeneration2DLod.expandVisibleRange(
                renderSupport,
                authoritative.bounds(),
                PREFETCH_RING_CELLS);
        desired = tileKeys(prefetchSupport, authoritative.bounds());
    }

    private static Set<TileKey> requiredKeys(
            ElevationField authoritative,
            VisualizerCamera.VisibleRange visible) {
        VisualizerCamera.VisibleRange support = WorldGeneration2DLod.expandVisibleRange(
                visible,
                authoritative.bounds(),
                RENDER_NEIGHBOUR_HALO_CELLS);
        return tileKeys(support, authoritative.bounds());
    }

    private static DetailFrame buildFrame(
            ElevationField authoritative,
            Set<TileKey> keys) {
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
        cachedFrameKeys = orderedCopy(keys);
        return cachedFrame;
    }

    /**
     * The first prewarmed x1 frame starts at its own exact x2 parent and converges to exact x1. The
     * source samples are already resident, so this does not run Continuum generation on the render
     * thread. Only this first publish morphs; new frames created while panning are exact immediately.
     */
    private static DetailFrame presentationFrame(DetailFrame exact) {
        if (!blendFirstPublish || firstPublishEstablished) return exact;
        if (firstPublishStartedNanos == 0L) firstPublishStartedNanos = System.nanoTime();
        long elapsed = Math.max(0L, System.nanoTime() - firstPublishStartedNanos);
        if (elapsed >= FIRST_PUBLISH_BLEND_NANOS) {
            firstPublishEstablished = true;
            return exact;
        }
        double linear = elapsed / (double) FIRST_PUBLISH_BLEND_NANOS;
        double smooth = linear * linear * (3d - 2d * linear);
        ElevationField parent = new BlockParentElevationField(exact.elevation(), exact.elevation().bounds(), 2);
        return new DetailFrame(
                new BlendedElevationField(parent, exact.elevation(), smooth),
                exact.shapes());
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
        if (nextMissingBatch().isEmpty()) return;
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
                if (!enabled || capturedSource == null || batch.isEmpty()) {
                    workerRunning = false;
                    return;
                }
            }

            Map<TileKey, Tile> built;
            try {
                built = buildBatch(capturedSource, batch);
            } catch (RuntimeException ignored) {
                synchronized (WorldGenerationExactDetailTiles.class) {
                    workerRunning = false;
                }
                return;
            }

            synchronized (WorldGenerationExactDetailTiles.class) {
                if (source != capturedSource) continue;
                READY.putAll(built);
            }
        }
    }

    /** Visible tiles are always one bulk batch; the prefetch ring is a later, lower-priority batch. */
    private static List<TileKey> nextMissingBatch() {
        List<TileKey> visibleMissing = new ArrayList<>();
        for (TileKey key : required) {
            if (!READY.containsKey(key)) visibleMissing.add(key);
        }
        if (!visibleMissing.isEmpty()) return visibleMissing;

        List<TileKey> prefetchMissing = new ArrayList<>();
        for (TileKey key : desired) {
            if (!READY.containsKey(key)) prefetchMissing.add(key);
        }
        return prefetchMissing;
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
            built.put(key, new Tile(tileElevation, shapes, overrides));
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
        LinkedHashSet<TileKey> keys = new LinkedHashSet<>();
        for (long y = firstY; y <= lastY; y += TILE_SIDE) {
            for (long x = firstX; x <= lastX; x += TILE_SIDE) {
                keys.add(new TileKey(Math.toIntExact(x), Math.toIntExact(y)));
            }
        }
        return Collections.unmodifiableSet(keys);
    }

    private static Set<TileKey> orderedCopy(Set<TileKey> keys) {
        return Collections.unmodifiableSet(new LinkedHashSet<>(keys));
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

    record DetailFrame(ElevationField elevation, TerrainShapeField shapes) {
        DetailFrame {
            if (elevation == null || shapes == null) {
                throw new IllegalArgumentException("detail frame fields must not be null");
            }
        }
    }

    private record TileKey(int minX, int minY) {}

    private record Tile(
            SnapshotElevationField elevation,
            TerrainShapeField shapes,
            long overrides) {}

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

    /** World-anchored x2 parent synthesized from the already resident exact x1 frame. */
    private static final class BlockParentElevationField implements ElevationField {
        private final ElevationField source;
        private final WorldBounds bounds;
        private final int stride;

        private BlockParentElevationField(ElevationField source, WorldBounds bounds, int stride) {
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
            if (!contains(x, y)) throw new IllegalArgumentException("x1 parent read outside bounds");
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

    private static final class BlendedElevationField implements ElevationField {
        private final ElevationField from;
        private final ElevationField to;
        private final double alpha;

        private BlendedElevationField(ElevationField from, ElevationField to, double alpha) {
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
                throw new IllegalArgumentException("x1 blend target is too small");
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
            return tileAt(x, y).shapes().surfaceAt(x, y);
        }

        @Override
        public Shape shapeOverrideAt(int x, int y) {
            return tileAt(x, y).shapes().shapeOverrideAt(x, y);
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
            return new SnapshotElevationField(
                    new WorldBounds(
                            visible.minX(), visible.maxX(),
                            visible.minY(), visible.maxY(),
                            sourceBounds.minZ(), sourceBounds.maxZ()),
                    width,
                    values);
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

        @Override
        public boolean contains(int x, int y) {
            return x >= bounds.minX() && x <= bounds.maxX()
                    && y >= bounds.minY() && y <= bounds.maxY();
        }
    }
}
