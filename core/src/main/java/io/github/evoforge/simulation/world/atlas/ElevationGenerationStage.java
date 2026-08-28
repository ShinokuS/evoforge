package io.github.evoforge.simulation.world.atlas;

import io.github.evoforge.simulation.world.continuum.field.ContinuumScalarPage;
import io.github.evoforge.simulation.world.continuum.field.ContinuumScalarPageSource;
import io.github.evoforge.simulation.world.continuum.model.ContinuumWorldDomain;
import io.github.evoforge.simulation.world.continuum.page.ContinuumPageKey;
import io.github.evoforge.simulation.world.continuum.page.ContinuumPageLayout;
import io.github.evoforge.simulation.world.continuum.page.ContinuumScalarPageCache;
import io.github.evoforge.simulation.world.genesis.GenerationRevision;
import io.github.evoforge.simulation.world.genesis.MountainIntent;
import io.github.evoforge.simulation.world.genesis.WorldGenerationIntent;
import io.github.evoforge.simulation.world.genesis.WorldGenesis;
import io.github.evoforge.simulation.world.spatial.WorldBounds;
import io.github.evoforge.simulation.world.terrain.definition.V13MountainDefinition;
import io.github.evoforge.simulation.world.terrain.definition.V15TerrainDefinition;
import io.github.evoforge.simulation.world.terrain.genesis.V15ContinuumTerrainPlan;
import io.github.evoforge.simulation.world.terrain.genesis.V15TerrainCoordinateFrame;

/**
 * Compatibility seam for the accepted historical V15 visualizer.
 *
 * <p>The old presentation still submits authored intent through this type, but terrain generation is
 * performed exclusively by the V15 Continuum plan. The returned field is page-backed: declaring a
 * large world no longer materializes {@code width * height} elevation samples up front.</p>
 */
public final class ElevationGenerationStage {
    private static final int PAGE_SIDE = 64;
    private static final int MAX_RESIDENT_PAGES = 256;
    private static final long MAX_RESIDENT_PAGE_BYTES = 16L * 1024L * 1024L;

    public ElevationField generate(WorldGenesis genesis) {
        if (genesis == null) throw new IllegalArgumentException("genesis must not be null");
        if (!GenerationRevision.V15.equals(genesis.generationRevision())) {
            throw new IllegalArgumentException("Continuum preview compatibility supports V15 only");
        }

        WorldBounds bounds = genesis.spec().bounds();
        long width = Math.addExact(Math.subtractExact((long) bounds.maxX(), bounds.minX()), 1L);
        long length = Math.addExact(Math.subtractExact((long) bounds.maxY(), bounds.minY()), 1L);
        ContinuumWorldDomain domain = new ContinuumWorldDomain(width, length);
        V15TerrainCoordinateFrame frame = V15TerrainCoordinateFrame.centered(domain);
        requireHistoricalPreviewFrame(bounds, frame, width, length);

        WorldGenerationIntent intent = genesis.generationIntent();
        V15TerrainDefinition terrainDefinition = new V15TerrainDefinition(
                intent.landCoverage(),
                intent.landmassScale(),
                intent.fragmentation(),
                intent.relief(),
                intent.localRelief(),
                intent.landformScale(),
                intent.ruggedness());
        MountainIntent mountains = intent.mountains();
        V13MountainDefinition mountainDefinition = new V13MountainDefinition(
                mountains.abundance(),
                mountains.height(),
                mountains.scale(),
                mountains.chaininess(),
                mountains.peakSharpness(),
                mountains.plateausEnabled(),
                mountains.plateauProbability());

        V15ContinuumTerrainPlan plan = V15ContinuumTerrainPlan.prepare(
                domain,
                genesis.masterSeed(),
                terrainDefinition,
                mountainDefinition,
                bounds.minZ(),
                bounds.maxZ());
        return new ContinuumElevationField(bounds, plan.elevationPages());
    }

    private static void requireHistoricalPreviewFrame(
            WorldBounds bounds,
            V15TerrainCoordinateFrame frame,
            long width,
            long length) {
        long expectedMaxX = Math.addExact(frame.legacyMinX(), width - 1L);
        long expectedMaxY = Math.addExact(frame.legacyMinY(), length - 1L);
        if (bounds.minX() != frame.legacyMinX()
                || bounds.minY() != frame.legacyMinY()
                || bounds.maxX() != expectedMaxX
                || bounds.maxY() != expectedMaxY) {
            throw new IllegalArgumentException(
                    "V15 Continuum preview requires the historical centered coordinate frame");
        }
    }

    private static final class ContinuumElevationField implements ElevationField {
        private final WorldBounds bounds;
        private final ContinuumPageLayout layout;
        private final ContinuumScalarPageCache pages;

        private ContinuumElevationField(
                WorldBounds bounds,
                ContinuumScalarPageSource source) {
            this.bounds = bounds;
            this.layout = new ContinuumPageLayout(source.domain(), PAGE_SIDE, PAGE_SIDE);
            this.pages = new ContinuumScalarPageCache(
                    layout,
                    source,
                    MAX_RESIDENT_PAGES,
                    MAX_RESIDENT_PAGE_BYTES);
        }

        @Override
        public WorldBounds bounds() {
            return bounds;
        }

        @Override
        public int elevationAt(int x, int y) {
            return Math.toIntExact(Math.floorDiv(elevationSubunitsAt(x, y), SUBUNITS_PER_CELL));
        }

        @Override
        public long elevationSubunitsAt(int x, int y) {
            if (!contains(x, y)) {
                throw new IllegalArgumentException(
                        "position outside elevation field: (" + x + ", " + y + ")");
            }
            long localX = (long) x - bounds.minX();
            long localY = (long) y - bounds.minY();
            ContinuumPageKey key = layout.pageAt(localX, localY);
            ContinuumScalarPage page = pages.page(key);
            int sampleX = Math.toIntExact(localX - page.window().minX());
            int sampleY = Math.toIntExact(localY - page.window().minY());
            return Math.round(page.sample(sampleX, sampleY));
        }
    }
}
