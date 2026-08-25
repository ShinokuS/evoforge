package io.github.evoforge.visualizer.continuum;

import io.github.evoforge.simulation.world.continuum.field.ContinuumMaterializer;
import io.github.evoforge.simulation.world.continuum.field.ContinuumSampleWindow;
import io.github.evoforge.simulation.world.continuum.field.ContinuumScalarPage;
import io.github.evoforge.simulation.world.continuum.map.ContinuumMapTileService;
import io.github.evoforge.simulation.world.continuum.map.ContinuumMapViewport;
import io.github.evoforge.simulation.world.continuum.model.ContinuumWorldDomain;
import io.github.evoforge.simulation.world.geophysics.MacroGeophysicalField;
import io.github.evoforge.simulation.world.geophysics.MacroGeophysics;
import io.github.evoforge.simulation.world.geophysics.MacroGeophysicsDefinition;
import io.github.evoforge.simulation.world.geophysics.MacroGeophysicsPreset;
import io.github.evoforge.simulation.world.terrain.ContinuousTerrainSurface;
import io.github.evoforge.simulation.world.terrain.TerrainSurfaceDefinition;
import io.github.evoforge.simulation.world.terrain.TerrainSurfaceEvolution;
import io.github.evoforge.simulation.world.terrain.TerrainSurfaceMapTileGenerator;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Presentation model for the real continuous landscape introduced in Continuum Stage 6. */
public final class ContinuumMapInspectorModel implements AutoCloseable {
    public static final long LOGICAL_SIDE = 16_000_000L;
    public static final long DEFAULT_WORLD_SEED = 0x45A1_0F0E_2026L;
    /** Compatibility alias for the original fixed inspector seed. */
    public static final long WORLD_SEED = DEFAULT_WORLD_SEED;
    public static final long GEOPHYSICS_REVISION = 1L;
    public static final long SURFACE_REVISION = 4L;
    public static final int TILE_SAMPLE_SIDE = 128;
    public static final int MAX_CPU_TILES = 384;
    public static final int MAX_OUTSTANDING_JOBS = 192;
    public static final int WORKERS = 4;
    public static final int PREFETCH_RING = 1;

    private final ContinuumWorldDomain domain;
    private final ExecutorService executor;
    private final ContinuumMapViewport viewport;

    private TerrainSurfaceMapTileGenerator generator;
    private ContinuumMapTileService tiles;
    private ContinuousTerrainSurface surface;
    private ContinuumMaterializer surfaceMaterializer;
    private MacroGeophysicsDefinition definition;
    private TerrainSurfaceDefinition surfaceDefinition;
    private MacroGeophysicsPreset preset;
    private long worldSeed;
    private long mapSourceRevision;
    private ContinuumMapViewport.Frame frame;

    public static ContinuumMapInspectorModel standard(int widthPixels, int heightPixels) {
        return standard(widthPixels, heightPixels, MacroGeophysicsPreset.BALANCED, DEFAULT_WORLD_SEED);
    }

    public static ContinuumMapInspectorModel standard(
            int widthPixels,
            int heightPixels,
            MacroGeophysicsPreset preset) {
        return standard(widthPixels, heightPixels, preset, DEFAULT_WORLD_SEED);
    }

    public static ContinuumMapInspectorModel standard(
            int widthPixels,
            int heightPixels,
            MacroGeophysicsPreset preset,
            long worldSeed) {
        if (preset == null) throw new IllegalArgumentException("preset must not be null");
        ContinuumWorldDomain domain = new ContinuumWorldDomain(LOGICAL_SIDE, LOGICAL_SIDE);
        ExecutorService executor = newExecutor();
        return new ContinuumMapInspectorModel(
                domain,
                executor,
                widthPixels,
                heightPixels,
                preset.definition(),
                TerrainSurfaceDefinition.balanced(),
                preset,
                worldSeed);
    }

    private ContinuumMapInspectorModel(
            ContinuumWorldDomain domain,
            ExecutorService executor,
            int widthPixels,
            int heightPixels,
            MacroGeophysicsDefinition definition,
            TerrainSurfaceDefinition surfaceDefinition,
            MacroGeophysicsPreset preset,
            long worldSeed) {
        if (domain == null || executor == null || definition == null || surfaceDefinition == null) {
            throw new IllegalArgumentException("domain/executor/definitions must not be null");
        }
        this.domain = domain;
        this.executor = executor;
        this.definition = definition;
        this.surfaceDefinition = surfaceDefinition;
        this.preset = preset;
        this.worldSeed = worldSeed;
        this.generator = generatorFor(definition, surfaceDefinition);
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

    /** Stage 5 authored macro-geophysics definition retained by the Stage 6 source. */
    public MacroGeophysicsDefinition definition() {
        return definition;
    }

    public TerrainSurfaceDefinition surfaceDefinition() {
        return surfaceDefinition;
    }

    public long seed() {
        return worldSeed;
    }

    public long sourceRevision() {
        return mapSourceRevision;
    }

    public long worldWidth() {
        return domain.width();
    }

    public long worldHeight() {
        return domain.height();
    }

    /** Bounded raw-Z materialization used by the real 3D terrain inspection mode. */
    public ContinuumScalarPage materializeSurface(ContinuumSampleWindow window) {
        return surfaceMaterializer.materialize(window);
    }

    /** Applies a named Stage 5 convenience profile without changing Stage 6 authored settings. */
    public boolean applyPreset(MacroGeophysicsPreset nextPreset) {
        if (nextPreset == null) throw new IllegalArgumentException("preset must not be null");
        return applyDefinition(nextPreset.definition(), nextPreset);
    }

    /** Applies arbitrary authored Stage 5 intent without changing Stage 6 settings or camera. */
    public boolean applyDefinition(MacroGeophysicsDefinition nextDefinition) {
        return applyDefinition(nextDefinition, null);
    }

    /** Applies Stage 6 surface intent without changing Stage 5 settings, seed or camera. */
    public boolean applySurfaceDefinition(TerrainSurfaceDefinition nextDefinition) {
        if (nextDefinition == null) throw new IllegalArgumentException("surface definition must not be null");
        if (surfaceDefinition.equals(nextDefinition)) return false;
        surfaceDefinition = nextDefinition;
        rebuildMapSource();
        return true;
    }

    /** Applies both authored layers with one source rebuild; Stage 5 preset identity is preserved when unchanged. */
    public boolean applyDefinitions(
            MacroGeophysicsDefinition nextMacroDefinition,
            TerrainSurfaceDefinition nextSurfaceDefinition) {
        if (nextMacroDefinition == null || nextSurfaceDefinition == null) {
            throw new IllegalArgumentException("definitions must not be null");
        }
        boolean macroChanged = !definition.equals(nextMacroDefinition);
        boolean surfaceChanged = !surfaceDefinition.equals(nextSurfaceDefinition);
        if (!macroChanged && !surfaceChanged) return false;

        definition = nextMacroDefinition;
        surfaceDefinition = nextSurfaceDefinition;
        if (macroChanged) preset = null;
        rebuildMapSource();
        return true;
    }

    /** Changes world identity while preserving both Stage 5 and Stage 6 authored settings. */
    public boolean applySeed(long nextSeed) {
        if (worldSeed == nextSeed) return false;
        worldSeed = nextSeed;
        rebuildMapSource();
        return true;
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

        rebuildMapSource();
        return true;
    }

    private void rebuildMapSource() {
        tiles.retainPendingDemand(Set.of());
        generator = generatorFor(definition, surfaceDefinition);
        tiles = tileService(generator);
        mapSourceRevision = Math.incrementExact(mapSourceRevision);
        viewport.setSourceRevision(mapSourceRevision);
        frame = viewport.requestFrame(tiles);
    }

    private TerrainSurfaceMapTileGenerator generatorFor(
            MacroGeophysicsDefinition macroDefinition,
            TerrainSurfaceDefinition terrainDefinition) {
        MacroGeophysicalField geophysics = MacroGeophysics.create(
                worldSeed,
                GEOPHYSICS_REVISION,
                macroDefinition);
        surface = TerrainSurfaceEvolution.create(
                worldSeed,
                SURFACE_REVISION,
                geophysics,
                terrainDefinition);
        surfaceMaterializer = new ContinuumMaterializer(domain, surface::surfaceZAt);
        return new TerrainSurfaceMapTileGenerator(domain, surface, TILE_SAMPLE_SIDE);
    }

    private ContinuumMapTileService tileService(TerrainSurfaceMapTileGenerator sourceGenerator) {
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
