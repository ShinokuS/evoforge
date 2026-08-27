package io.github.evoforge.simulation.world.terrain.genesis;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.evoforge.simulation.world.atlas.BathymetryCalibration;
import io.github.evoforge.simulation.world.atlas.BathymetryCalibrator;
import io.github.evoforge.simulation.world.atlas.BathymetryMorphologyAlgorithm;
import io.github.evoforge.simulation.world.atlas.BathymetryRecipe;
import io.github.evoforge.simulation.world.atlas.ElevationField;
import io.github.evoforge.simulation.world.atlas.MountainCalibrator;
import io.github.evoforge.simulation.world.atlas.MountainRecipe;
import io.github.evoforge.simulation.world.atlas.V13MountainTerrainGenerator;
import io.github.evoforge.simulation.world.atlas.V14OceanicBaseTerrainGenerator;
import io.github.evoforge.simulation.world.continuum.field.ContinuumSampleWindow;
import io.github.evoforge.simulation.world.continuum.field.ContinuumScalarPage;
import io.github.evoforge.simulation.world.continuum.model.ContinuumWorldDomain;
import io.github.evoforge.simulation.world.genesis.GenerationRevision;
import io.github.evoforge.simulation.world.genesis.RngRevision;
import io.github.evoforge.simulation.world.genesis.WorldGenerationIntent;
import io.github.evoforge.simulation.world.genesis.WorldGenesis;
import io.github.evoforge.simulation.world.genesis.WorldSpec;
import io.github.evoforge.simulation.world.spatial.WorldBounds;
import io.github.evoforge.simulation.world.terrain.definition.V13MountainDefinition;
import io.github.evoforge.simulation.world.terrain.definition.V15TerrainDefinition;
import org.junit.jupiter.api.Test;

/** Proves the exact historical V14 coastal bathymetry pass before deep-interior structure. */
final class V14CoastalBathymetryHistoricalOracleParityTest {
    private static final int MIN_Z_CELLS = -64;
    private static final int MAX_Z_CELLS = 80;

    @Test
    void exactContinuumV14CoastalBathymetryMatchesHistoricalDenseGeneratorCellForCell() {
        for (Fixture fixture : new Fixture[] {
                new Fixture(61, 53, 913L),
                new Fixture(68, 57, -4_759_010_560_822_749_572L)
        }) {
            assertFixtureParity(fixture);
        }
    }

    private static void assertFixtureParity(Fixture fixture) {
        ContinuumWorldDomain domain = new ContinuumWorldDomain(fixture.width(), fixture.height());
        V15TerrainCoordinateFrame frame = V15TerrainCoordinateFrame.centered(domain);
        WorldBounds finalBounds = historicalBounds(frame, domain, MIN_Z_CELLS, MAX_Z_CELLS);
        WorldGenerationIntent intent = WorldGenerationIntent.balanced();
        WorldGenesis finalGenesis = new WorldGenesis(
                new WorldSpec(finalBounds),
                fixture.seed(),
                GenerationRevision.V14,
                RngRevision.V1,
                intent);

        BathymetryRecipe historicalRecipe = BathymetryRecipe.balanced();
        BathymetryCalibration historicalCalibration = BathymetryCalibrator.standard()
                .calibrate(finalGenesis, historicalRecipe);
        int baseMinZ = Math.max(MIN_Z_CELLS, -historicalRecipe.baseTerrainFloorCells());
        WorldBounds baseBounds = historicalBounds(frame, domain, baseMinZ, MAX_Z_CELLS);
        WorldGenesis baseGenesis = new WorldGenesis(
                new WorldSpec(baseBounds),
                fixture.seed(),
                GenerationRevision.V13,
                RngRevision.V1,
                intent);
        ElevationField preBathymetry = new V13MountainTerrainGenerator(
                        V14OceanicBaseTerrainGenerator.standard(),
                        MountainCalibrator.standard(),
                        MountainRecipe.balanced())
                .generate(baseGenesis);
        ElevationField historical = new BathymetryMorphologyAlgorithm().generate(
                finalGenesis,
                preBathymetry,
                historicalCalibration,
                historicalRecipe);

        V14ContinuumCoastalBathymetryPlan continuum = V14ContinuumCoastalBathymetryPlan.prepare(
                domain,
                fixture.seed(),
                V15TerrainDefinition.balanced(),
                V13MountainDefinition.balanced(),
                MIN_Z_CELLS,
                MAX_Z_CELLS);
        ContinuumScalarPage page = continuum.elevationPages().materialize(new ContinuumSampleWindow(
                0L,
                0L,
                fixture.width(),
                fixture.height(),
                1L));

        for (int y = 0; y < fixture.height(); y++) {
            int legacyY = Math.toIntExact(frame.legacyY(y));
            for (int x = 0; x < fixture.width(); x++) {
                int legacyX = Math.toIntExact(frame.legacyX(x));
                assertEquals(
                        historical.elevationSubunitsAt(legacyX, legacyY),
                        Math.round(page.sample(x, y)),
                        "V14 coastal bathymetry parity failed at seed=" + fixture.seed()
                                + " x=" + x
                                + " y=" + y
                                + " legacyX=" + legacyX
                                + " legacyY=" + legacyY);
            }
        }
    }

    private static WorldBounds historicalBounds(
            V15TerrainCoordinateFrame frame,
            ContinuumWorldDomain domain,
            int minZ,
            int maxZ) {
        int minX = Math.toIntExact(frame.legacyMinX());
        int minY = Math.toIntExact(frame.legacyMinY());
        int maxX = Math.toIntExact(Math.addExact(frame.legacyMinX(), domain.width() - 1L));
        int maxY = Math.toIntExact(Math.addExact(frame.legacyMinY(), domain.height() - 1L));
        return new WorldBounds(minX, maxX, minY, maxY, minZ, maxZ);
    }

    private record Fixture(int width, int height, long seed) {}
}
