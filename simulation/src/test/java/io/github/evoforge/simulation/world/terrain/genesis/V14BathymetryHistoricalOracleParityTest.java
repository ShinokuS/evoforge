package io.github.evoforge.simulation.world.terrain.genesis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.evoforge.simulation.world.atlas.BathymetryCalibration;
import io.github.evoforge.simulation.world.atlas.BathymetryCalibrator;
import io.github.evoforge.simulation.world.atlas.BathymetryMorphologyAlgorithm;
import io.github.evoforge.simulation.world.atlas.BathymetryRecipe;
import io.github.evoforge.simulation.world.atlas.DeepBathymetryStructureAlgorithm;
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

/** Proves the complete historical V14 terrain, including deep-interior bathymetry, cell-for-cell. */
final class V14BathymetryHistoricalOracleParityTest {
    private static final int MIN_Z_CELLS = -64;
    private static final int MAX_Z_CELLS = 80;

    @Test
    void exactContinuumV14MatchesHistoricalDenseGeneratorCellForCellAndExercisesDeepPass() {
        Fixture fixture = new Fixture(96, 80, 913L);
        ContinuumWorldDomain domain = new ContinuumWorldDomain(fixture.width(), fixture.height());
        V15TerrainCoordinateFrame frame = V15TerrainCoordinateFrame.centered(domain);
        WorldGenerationIntent intent = WorldGenerationIntent.balanced();
        WorldBounds finalBounds = historicalBounds(frame, domain, MIN_Z_CELLS, MAX_Z_CELLS);
        WorldGenesis finalGenesis = new WorldGenesis(
                new WorldSpec(finalBounds),
                fixture.seed(),
                GenerationRevision.V14,
                RngRevision.V1,
                intent);

        BathymetryRecipe recipe = BathymetryRecipe.balanced();
        BathymetryCalibration calibration = BathymetryCalibrator.standard().calibrate(finalGenesis, recipe);
        int baseMinZ = Math.max(MIN_Z_CELLS, -recipe.baseTerrainFloorCells());
        WorldGenesis baseGenesis = new WorldGenesis(
                new WorldSpec(historicalBounds(frame, domain, baseMinZ, MAX_Z_CELLS)),
                fixture.seed(),
                GenerationRevision.V13,
                RngRevision.V1,
                intent);
        ElevationField preBathymetry = new V13MountainTerrainGenerator(
                        V14OceanicBaseTerrainGenerator.standard(),
                        MountainCalibrator.standard(),
                        MountainRecipe.balanced())
                .generate(baseGenesis);
        ElevationField coastal = new BathymetryMorphologyAlgorithm().generate(
                finalGenesis,
                preBathymetry,
                calibration,
                recipe);
        ElevationField historical = new DeepBathymetryStructureAlgorithm().generate(
                finalGenesis,
                coastal,
                calibration,
                recipe);

        V14ContinuumBathymetryPlan continuum = V14ContinuumBathymetryPlan.prepare(
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

        int deepChangedCells = 0;
        for (int y = 0; y < fixture.height(); y++) {
            int legacyY = Math.toIntExact(frame.legacyY(y));
            for (int x = 0; x < fixture.width(); x++) {
                int legacyX = Math.toIntExact(frame.legacyX(x));
                long expected = historical.elevationSubunitsAt(legacyX, legacyY);
                long actual = Math.round(page.sample(x, y));
                assertEquals(
                        expected,
                        actual,
                        "V14 bathymetry parity failed at x=" + x
                                + " y=" + y
                                + " legacyX=" + legacyX
                                + " legacyY=" + legacyY);
                if (expected != coastal.elevationSubunitsAt(legacyX, legacyY)) deepChangedCells++;
            }
        }
        assertTrue(deepChangedCells > 0, "V14 parity fixture must exercise deep-interior structure");
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
