package io.github.evoforge.visualizer.continuum;

import io.github.evoforge.simulation.world.continuum.field.ContinuumScalarField;
import io.github.evoforge.simulation.world.continuum.map.ContinuumMapTileService;
import io.github.evoforge.simulation.world.continuum.map.ContinuumMapViewport;
import io.github.evoforge.simulation.world.continuum.map.ContinuumScalarMapTileGenerator;
import io.github.evoforge.simulation.world.continuum.model.ContinuumWorldDomain;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Presentation model for the Stage 4 pan/zoom proof. */
public final class ContinuumMapInspectorModel implements AutoCloseable {
    public static final long LOGICAL_SIDE = 1_000_000L;
    public static final int TILE_SAMPLE_SIDE = 128;
    public static final int MAX_CPU_TILES = 256;
    public static final int MAX_OUTSTANDING_JOBS = 96;
    public static final int WORKERS = 2;
    public static final int PREFETCH_RING = 1;

    private final ExecutorService executor;
    private final ContinuumScalarMapTileGenerator generator;
    private final ContinuumMapTileService tiles;
    private final ContinuumMapViewport viewport;
    private ContinuumMapViewport.Frame frame;

    public static ContinuumMapInspectorModel standard(int widthPixels, int heightPixels) {
        ContinuumWorldDomain domain = new ContinuumWorldDomain(LOGICAL_SIDE, LOGICAL_SIDE);
        ContinuumScalarField field = ContinuumMapInspectorModel::syntheticField;
        ContinuumScalarMapTileGenerator generator = new ContinuumScalarMapTileGenerator(domain, field, TILE_SAMPLE_SIDE);
        ExecutorService executor = Executors.newFixedThreadPool(WORKERS, runnable -> {
            Thread thread = new Thread(runnable, "continuum-map-tile");
            thread.setDaemon(true);
            return thread;
        });
        return new ContinuumMapInspectorModel(domain, generator, executor, widthPixels, heightPixels);
    }

    ContinuumMapInspectorModel(
            ContinuumWorldDomain domain,
            ContinuumScalarMapTileGenerator generator,
            ExecutorService executor,
            int widthPixels,
            int heightPixels) {
        if (domain == null || generator == null || executor == null) {
            throw new IllegalArgumentException("domain/generator/executor must not be null");
        }
        this.executor = executor;
        this.generator = generator;
        this.tiles = new ContinuumMapTileService(
                generator,
                executor,
                generator.maxLevel(),
                MAX_CPU_TILES,
                MAX_OUTSTANDING_JOBS,
                WORKERS);
        this.viewport = new ContinuumMapViewport(
                domain.width(),
                domain.height(),
                generator.sampleSide(),
                generator.maxLevel(),
                PREFETCH_RING,
                Math.max(1, widthPixels),
                Math.max(1, heightPixels));
        update(widthPixels, heightPixels);
    }

    public void update(int widthPixels, int heightPixels) {
        viewport.resize(Math.max(1, widthPixels), Math.max(1, heightPixels));
        frame = viewport.requestFrame(tiles);
    }

    public ContinuumMapViewport.Frame frame() {
        return frame;
    }

    public ContinuumMapTileService.Metrics metrics() {
        return tiles.metrics();
    }

    public void panPixels(double deltaX, double deltaY) {
        viewport.panPixels(deltaX, deltaY);
    }

    public void zoomAt(double factor, double screenX, double screenY) {
        viewport.zoomAt(factor, screenX, screenY);
    }

    public void fitWholeWorld() {
        viewport.fitWholeWorld();
    }

    public double screenXForWorld(double worldX) {
        return viewport.screenXForWorld(worldX);
    }

    public double screenYForWorld(double worldY) {
        return viewport.screenYForWorld(worldY);
    }

    public long tileWorldSpan(int level) {
        return viewport.tileWorldSpan(level);
    }

    public double centerX() {
        return viewport.centerX();
    }

    public double centerY() {
        return viewport.centerY();
    }

    public double pixelsPerWorldUnit() {
        return viewport.pixelsPerWorldUnit();
    }

    public int maxLevel() {
        return generator.maxLevel();
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }

    private static double syntheticField(long x, long y) {
        double broad = Math.sin(x / 91_000d) * Math.cos(y / 73_000d);
        double medium = Math.sin((x + y) / 31_000d) * 0.55d;
        double fine = Math.cos(x / 9_000d - y / 12_000d) * 0.28d;
        double diagonal = Math.sin((x * 0.65d - y * 0.35d) / 17_000d) * 0.22d;
        return Math.max(0d, Math.min(1d, 0.5d + broad * 0.23d + medium * 0.18d + fine * 0.12d + diagonal * 0.08d));
    }
}
