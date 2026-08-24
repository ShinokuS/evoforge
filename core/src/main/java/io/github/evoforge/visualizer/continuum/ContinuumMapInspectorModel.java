package io.github.evoforge.visualizer.continuum;

import io.github.evoforge.simulation.world.continuum.field.ContinuumScalarField;
import io.github.evoforge.simulation.world.continuum.map.ContinuumMapTileService;
import io.github.evoforge.simulation.world.continuum.map.ContinuumMapViewport;
import io.github.evoforge.simulation.world.continuum.map.ContinuumScalarMapTileGenerator;
import io.github.evoforge.simulation.world.continuum.model.ContinuumWorldDomain;
import io.github.evoforge.simulation.world.geophysics.MacroGeophysicalContinuumField;
import io.github.evoforge.simulation.world.geophysics.MacroGeophysics;
import io.github.evoforge.simulation.world.geophysics.MacroGeophysicsDefinition;
import io.github.evoforge.simulation.world.geophysics.MacroGeophysicsPreset;
import java.util.Set;
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

    private final ContinuumWorldDomain domain;
    private final ExecutorService executor;
    private final ContinuumMapViewport viewport;

    private ContinuumScalarMapTileGenerator generator;
    private ContinuumMapTileService tiles;
    private MacroGeophysicsDefinition definition;
    private MacroGeophysicsPreset preset;
    private long mapSourceRevision;
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
        ExecutorService executor = newExecutor();
        return new ContinuumMapInspectorModel(
                domain,
                executor,
                widthPixels,
                heightPixels,
                preset.definition(),
                preset);
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
        this.domain = domain;
        this.executor = executor;
        this.generator = generator;
        this.definition = MacroGeophysicsPreset.BALANCED.definition();
        this.preset = MacroGeophysicsPreset.BALANCED;
        this.tiles = tileService(generator);
        this.viewport = new ContinuumMapViewport(
                domain.width(),
                domain.height(),
                generator.sampleSide(),
                generator.maxLevel(),
                PREFETCH_RING,
                Math.max(1, widthPixels),
                Math.max(1, heightPixels));
        viewport.setSourceRevision(mapSourceRevision);
        update(widthPixels, heightPixels);
    }

    private ContinuumMapInspectorModel(
            ContinuumWorldDomain domain,
            ExecutorService executor,
            int widthPixels,
            int heightPixels,
            MacroGeophysicsDefinition definition,
            MacroGeophysicsPreset preset) {
        if (domain == null || executor == null || definition == null) {
            throw new IllegalArgumentException("domain/executor/definition must not be null");
        }
        this.domain = domain;
        this.executor = executor;
        this.definition = definition;
        this.preset = preset;
        this.generator = generatorFor(definition);
        this.tiles = tileService(generator);
        this.viewport = new ContinuumMapViewport(
                domain.width(),
                domain.height(),
                generator.sampleSide(),
                generator.maxLevel(),
                PREFETCH_RING,
                Math.max(1, widthPixels),
                Math.max(1, heightPixels));
        viewport.setSourceRevision(mapSourceRevision);
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

    public String profileName() {
        return preset == null ? "custom" : preset.displayName();
    }

    public MacroGeophysicsDefinition definition() {
        return definition;
    }

    /** Applies a named convenience profile without moving or zooming the inspection camera. */
    public boolean applyPreset(MacroGeophysicsPreset nextPreset) {
        if (nextPreset == null) throw new IllegalArgumentException("preset must not be null");
        return applyDefinition(nextPreset.definition(), nextPreset);
    }

    /** Applies arbitrary authored macro-geophysics intent without changing the inspection camera. */
    public boolean applyDefinition(MacroGeophysicsDefinition nextDefinition) {
        return applyDefinition(nextDefinition, null);
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
        tiles.retainPendingDemand(Set.of());
        executor.shutdownNow();
    }

    private boolean applyDefinition(
            MacroGeophysicsDefinition nextDefinition,
            MacroGeophysicsPreset nextPreset) {
        if (nextDefinition == null) throw new IllegalArgumentException("definition must not be null");

        boolean changed = !definition.equals(nextDefinition);
        definition = nextDefinition;
        preset = nextPreset;
        if (!changed) return false;

        // Cancel queued work from the old derived source. At most the bounded worker count may be
        // finishing already-running old jobs; their service becomes unreachable and cannot publish
        // into the new map source.
        tiles.retainPendingDemand(Set.of());
        generator = generatorFor(nextDefinition);
        tiles = tileService(generator);
        mapSourceRevision = Math.incrementExact(mapSourceRevision);
        viewport.setSourceRevision(mapSourceRevision);
        frame = viewport.requestFrame(tiles);
        return true;
    }

    private ContinuumScalarMapTileGenerator generatorFor(MacroGeophysicsDefinition sourceDefinition) {
        ContinuumScalarField field = new MacroGeophysicalContinuumField(MacroGeophysics.create(
                WORLD_SEED,
                GEOPHYSICS_REVISION,
                sourceDefinition));
        return new ContinuumScalarMapTileGenerator(domain, field, TILE_SAMPLE_SIDE);
    }

    private ContinuumMapTileService tileService(ContinuumScalarMapTileGenerator sourceGenerator) {
        return new ContinuumMapTileService(
                sourceGenerator,
                executor,
                sourceGenerator.maxLevel(),
                MAX_CPU_TILES,
                MAX_OUTSTANDING_JOBS,
                WORKERS);
    }

    private static ExecutorService newExecutor() {
        return Executors.newFixedThreadPool(WORKERS, runnable -> {
            Thread thread = new Thread(runnable, "continuum-map-tile");
            thread.setDaemon(true);
            thread.setPriority(Math.max(Thread.MIN_PRIORITY, Thread.NORM_PRIORITY - 1));
            return thread;
        });
    }
}
