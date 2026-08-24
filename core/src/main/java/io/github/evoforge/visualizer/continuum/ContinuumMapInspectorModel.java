package io.github.evoforge.visualizer.continuum;

import io.github.evoforge.simulation.world.continuum.field.ContinuumScalarField;
import io.github.evoforge.simulation.world.continuum.map.ContinuumMapTileService;
import io.github.evoforge.simulation.world.continuum.map.ContinuumMapViewport;
import io.github.evoforge.simulation.world.continuum.map.ContinuumScalarMapTileGenerator;
import io.github.evoforge.simulation.world.continuum.model.ContinuumWorldDomain;
import io.github.evoforge.simulation.world.geophysics.MacroGeophysicalContinuumField;
import io.github.evoforge.simulation.world.geophysics.MacroGeophysics;
import io.github.evoforge.simulation.world.geophysics.MacroGeophysicsPreset;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Presentation model for the real macro-geography introduced in Continuum Stage 5. */
public final class ContinuumMapInspectorModel implements AutoCloseable {
    public static final long LOGICAL_SIDE = 16_000_000L;
    public static final long WORLD_SEED = 0x45A1_0F0E_2026L;
    public static final long GEOPHYSICS_REVISION = 1L;
    public static final int TILE_SAMPLE_SIDE = 128;
    public static final int MAX_CPU_TILES = 384;
    public static final int MAX_OUTSTANDING_JOBS = 192;
    public static final int WORKERS = 4;
    public static final int PREFETCH_RING = 1;

    private final ExecutorService executor;
    private final ContinuumScalarMapTileGenerator generator;
    private final ContinuumMapTileService tiles;
    private final ContinuumMapViewport viewport;
    private final MacroGeophysicsPreset preset;
    private ContinuumMapViewport.Frame frame;

    public static ContinuumMapInspectorModel standard(int widthPixels, int heightPixels) {
        return standard(widthPixels, heightPixels, MacroGeophysicsPreset.BALANCED);
    }

    public static ContinuumMapInspectorModel standard(
            int widthPixels,
            int heightPixels,
            MacroGeophysicsPreset preset) {
        if (preset == null) throw new IllegalArgumentException("preset must not be null");
        ContinuumWorldDomain domain = new ContinuumWorldDomain(LOGICAL_SIDE, LOGICAL_SIDE);
        ContinuumScalarField field = new MacroGeophysicalContinuumField(MacroGeophysics.create(
                WORLD_SEED,
                GEOPHYSICS_REVISION,
                preset.definition()));
        ContinuumScalarMapTileGenerator generator = new ContinuumScalarMapTileGenerator(domain, field, TILE_SAMPLE_SIDE);
        ExecutorService executor = Executors.newFixedThreadPool(WORKERS, runnable -> {
            Thread thread = new Thread(runnable, "continuum-map-tile");
            thread.setDaemon(true);
            thread.setPriority(Math.max(Thread.MIN_PRIORITY, Thread.NORM_PRIORITY - 1));
            return thread;
        });
        return new ContinuumMapInspectorModel(domain, generator, executor, widthPixels, heightPixels, preset);
    }

    ContinuumMapInspectorModel(
            ContinuumWorldDomain domain,
            ContinuumScalarMapTileGenerator generator,
            ExecutorService executor,
            int widthPixels,
            int heightPixels) {
        this(domain, generator, executor, widthPixels, heightPixels, MacroGeophysicsPreset.BALANCED);
    }

    ContinuumMapInspectorModel(
            ContinuumWorldDomain domain,
            ContinuumScalarMapTileGenerator generator,
            ExecutorService executor,
            int widthPixels,
            int heightPixels,
            MacroGeophysicsPreset preset) {
        if (domain == null || generator == null || executor == null || preset == null) {
            throw new IllegalArgumentException("domain/generator/executor/preset must not be null");
        }
        this.executor = executor;
        this.generator = generator;
        this.preset = preset;
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

    public MacroGeophysicsPreset preset() {
        return preset;
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
}
