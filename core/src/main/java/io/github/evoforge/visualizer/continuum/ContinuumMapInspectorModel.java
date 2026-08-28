package io.github.evoforge.visualizer.continuum;

import io.github.evoforge.simulation.world.continuum.field.ContinuumSampleWindow;
import io.github.evoforge.simulation.world.continuum.field.ContinuumScalarField;
import io.github.evoforge.simulation.world.continuum.field.ContinuumScalarPage;
import io.github.evoforge.simulation.world.continuum.map.ContinuumMapTileService;
import io.github.evoforge.simulation.world.continuum.map.ContinuumMapViewport;
import io.github.evoforge.simulation.world.continuum.map.ContinuumScalarMapTileGenerator;
import io.github.evoforge.simulation.world.continuum.model.ContinuumWorldDomain;
import io.github.evoforge.simulation.world.terrain.definition.V13MountainDefinition;
import io.github.evoforge.simulation.world.terrain.definition.V15TerrainDefinition;
import io.github.evoforge.simulation.world.terrain.field.TerrainElevationField;
import io.github.evoforge.simulation.world.terrain.genesis.V15ContinuumTerrainPlan;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Presentation model for the accepted exact V15 terrain running through Continuum. */
public final class ContinuumMapInspectorModel implements AutoCloseable {
    public static final long LOGICAL_SIDE = 256L;
    public static final long DEFAULT_WORLD_SEED = 0x45A1_0F0E_2026L;
    /** Compatibility alias for the original fixed inspector seed. */
    public static final long WORLD_SEED = DEFAULT_WORLD_SEED;
    public static final long TERRAIN_REVISION = 15L;
    public static final int MINIMUM_Z_CELLS = -96;
    public static final int MAXIMUM_Z_CELLS = 96;
    public static final int TILE_SAMPLE_SIDE = 128;
    public static final int MAX_CPU_TILES = 384;
    public static final int MAX_OUTSTANDING_JOBS = 192;
    public static final int WORKERS = 4;
    public static final int PREFETCH_RING = 1;

    @FunctionalInterface
    interface TerrainGeneratorFactory {
        ContinuumScalarMapTileGenerator create(V15TerrainDefinition definition, long seed);
    }

    private final ContinuumWorldDomain domain;
    private final ExecutorService executor;
    private final ContinuumMapViewport viewport;
    private final TerrainGeneratorFactory generatorFactory;

    private ContinuumScalarMapTileGenerator generator;
    private ContinuumMapTileService tiles;
    private V15TerrainDefinition definition;
    private long worldSeed;
    private long mapSourceRevision;
    private ContinuumMapViewport.Frame frame;

    public static ContinuumMapInspectorModel standard(int widthPixels, int heightPixels) {
        return standard(widthPixels, heightPixels, V15TerrainDefinition.balanced(), DEFAULT_WORLD_SEED);
    }

    public static ContinuumMapInspectorModel standard(
            int widthPixels,
            int heightPixels,
            long worldSeed) {
        return standard(widthPixels, heightPixels, V15TerrainDefinition.balanced(), worldSeed);
    }

    public static ContinuumMapInspectorModel standard(
            int widthPixels,
            int heightPixels,
            V15TerrainDefinition definition,
            long worldSeed) {
        if (definition == null) throw new IllegalArgumentException("definition must not be null");
        ContinuumWorldDomain domain = new ContinuumWorldDomain(LOGICAL_SIDE, LOGICAL_SIDE);
        ExecutorService executor = newExecutor();
        TerrainGeneratorFactory generatorFactory =
                (sourceDefinition, seed) -> generatorFor(domain, sourceDefinition, seed);
        return new ContinuumMapInspectorModel(
                domain,
                executor,
                widthPixels,
                heightPixels,
                definition,
                worldSeed,
                generatorFactory);
    }

    ContinuumMapInspectorModel(
            ContinuumWorldDomain domain,
            ExecutorService executor,
            int widthPixels,
            int heightPixels,
            V15TerrainDefinition definition,
            long worldSeed,
            TerrainGeneratorFactory generatorFactory) {
        if (domain == null || executor == null || definition == null || generatorFactory == null) {
            throw new IllegalArgumentException("inspector inputs must not be null");
        }
        this.domain = domain;
        this.executor = executor;
        this.definition = definition;
        this.worldSeed = worldSeed;
        this.generatorFactory = generatorFactory;
        this.generator = generatorFactory.create(definition, worldSeed);
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

    public String profileName() {
        return definition.equals(V15TerrainDefinition.balanced()) ? "balanced" : "custom";
    }

    public V15TerrainDefinition definition() {
        return definition;
    }

    public long seed() {
        return worldSeed;
    }

    /** Applies authored V15 terrain intent without moving or zooming the inspection camera. */
    public boolean applyDefinition(V15TerrainDefinition nextDefinition) {
        if (nextDefinition == null) throw new IllegalArgumentException("definition must not be null");
        if (definition.equals(nextDefinition)) return false;
        definition = nextDefinition;
        rebuildMapSource();
        return true;
    }

    /** Changes world identity and rebuilds only the exact V15 source, preserving the camera. */
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

    private void rebuildMapSource() {
        // Cancel queued work from the old immutable presentation source. At most the bounded worker
        // count may finish already-running old jobs; that service becomes unreachable afterwards.
        tiles.retainPendingDemand(Set.of());
        generator = generatorFactory.create(definition, worldSeed);
        tiles = tileService(generator);
        mapSourceRevision = Math.incrementExact(mapSourceRevision);
        viewport.setSourceRevision(mapSourceRevision);
        frame = viewport.requestFrame(tiles);
    }

    private static ContinuumScalarMapTileGenerator generatorFor(
            ContinuumWorldDomain domain,
            V15TerrainDefinition definition,
            long seed) {
        V15ContinuumTerrainPlan plan = V15ContinuumTerrainPlan.prepare(
                domain,
                seed,
                definition,
                V13MountainDefinition.balanced(),
                MINIMUM_Z_CELLS,
                MAXIMUM_Z_CELLS);
        int width = Math.toIntExact(domain.width());
        int height = Math.toIntExact(domain.height());
        ContinuumScalarPage terrain = plan.elevationPages().materialize(
                new ContinuumSampleWindow(0L, 0L, width, height, 1L));
        ContinuumScalarField presentationField = (x, y) -> normalizeTerrainElevation(
                Math.round(terrain.sample(Math.toIntExact(x), Math.toIntExact(y))));
        return new ContinuumScalarMapTileGenerator(domain, presentationField, TILE_SAMPLE_SIDE);
    }

    static double normalizeTerrainElevation(long elevationSubunits) {
        long minimum = Math.multiplyExact((long) MINIMUM_Z_CELLS, TerrainElevationField.SUBUNITS_PER_CELL);
        long maximum = Math.multiplyExact((long) MAXIMUM_Z_CELLS, TerrainElevationField.SUBUNITS_PER_CELL);
        if (elevationSubunits < 0L) {
            long clamped = Math.max(minimum, elevationSubunits);
            double fraction = (clamped - (double) minimum) / -(double) minimum;
            return 127d * fraction / 255d;
        }
        long clamped = Math.min(maximum, elevationSubunits);
        double fraction = clamped / (double) maximum;
        return (128d + 127d * fraction) / 255d;
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
