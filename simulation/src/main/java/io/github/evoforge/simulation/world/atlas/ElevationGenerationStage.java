package io.github.evoforge.simulation.world.atlas;

import io.github.evoforge.simulation.world.continuum.field.ContinuumSampleWindow;
import io.github.evoforge.simulation.world.continuum.field.ContinuumScalarPage;
import io.github.evoforge.simulation.world.continuum.model.ContinuumWorldDomain;
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
 * Temporary compatibility seam for the accepted historical visualizer.
 *
 * <p>The old visualizer still submits its V15 authored intent through this type, but generation is
 * performed exclusively by the Continuum V15 plan. No historical dense generator is reachable from
 * production code.</p>
 */
public final class ElevationGenerationStage {

    public ElevationField generate(WorldGenesis genesis) {
        if (genesis == null) throw new IllegalArgumentException("genesis must not be null");
        if (!GenerationRevision.V15.equals(genesis.generationRevision())) {
            throw new IllegalArgumentException("Continuum preview compatibility supports V15 only");
        }

        WorldBounds bounds = genesis.spec().bounds();
        long widthLong = Math.addExact(Math.subtractExact((long) bounds.maxX(), bounds.minX()), 1L);
        long lengthLong = Math.addExact(Math.subtractExact((long) bounds.maxY(), bounds.minY()), 1L);
        int width = Math.toIntExact(widthLong);
        int length = Math.toIntExact(lengthLong);
        ContinuumWorldDomain domain = new ContinuumWorldDomain(widthLong, lengthLong);
        V15TerrainCoordinateFrame frame = V15TerrainCoordinateFrame.centered(domain);
        requireHistoricalPreviewFrame(bounds, frame, widthLong, lengthLong);

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
        ContinuumScalarPage page = plan.elevationPages().materialize(
                new ContinuumSampleWindow(0L, 0L, width, length, 1L));
        return new ContinuumElevationField(bounds, width, page);
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
        private final int width;
        private final ContinuumScalarPage page;

        private ContinuumElevationField(WorldBounds bounds, int width, ContinuumScalarPage page) {
            this.bounds = bounds;
            this.width = width;
            this.page = page;
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
            int localX = x - bounds.minX();
            int localY = y - bounds.minY();
            if (localX < 0 || localX >= width) {
                throw new IllegalStateException("preview X coordinate mapping is inconsistent");
            }
            return Math.round(page.sample(localX, localY));
        }
    }
}
