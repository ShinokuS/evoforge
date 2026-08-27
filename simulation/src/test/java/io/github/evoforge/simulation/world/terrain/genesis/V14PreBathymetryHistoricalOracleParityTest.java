package io.github.evoforge.simulation.world.terrain.genesis;

import static org.junit.jupiter.api.Assertions.assertEquals;

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

/** Proves the exact V14 oceanic footprint plus V13 mountains before bathymetry. */
final class V14PreBathymetryHistoricalOracleParityTest {
    private static final int BASE_MIN_Z_CELLS = -1;
    private static final int MAX_Z_CELLS = 80;

    @Test
    void exactContinuumV14PreBathymetryMatchesHistoricalDenseGeneratorCellForCell() {
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
        WorldBounds bounds = historicalBounds(frame, domain);
        WorldGenesis genesis = new WorldGenesis(
                new WorldSpec(bounds),
                fixture.seed(),
                GenerationRevision.V13,
                RngRevision.V1,
                WorldGenerationIntent.balanced());
        ElevationField historical = new V13MountainTerrainGenerator(
                        V14OceanicBaseTerrainGenerator.standard(),
                        MountainCalibrator.standard(),
                        MountainRecipe.balanced())
                .generate(genesis);

        V14ContinuumPreBathymetryPlan continuum = V14ContinuumPreBathymetryPlan.prepare(
                domain,
                fixture.seed(),
                V15TerrainDefinition.balanced(),
                V13MountainDefinition.balanced(),
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
                long expected = historical.elevationSubunitsAt(legacyX, legacyY);
                long actual = Math.round(page.sample(x, y));
                assertEquals(
                        expected,
                        actual,
                        "V14 pre-bathymetry parity failed at seed=" + fixture.seed()
                                + " x=" + x
                                + " y=" + y
                                + " legacyX=" + legacyX
                                + " legacyY=" + legacyY);
            }
        }
    }

    private static WorldBounds historicalBounds(
            V15TerrainCoordinateFrame frame,
            ContinuumWorldDomain domain) {
        int minX = Math.toIntExact(frame.legacyMinX());
        int minY = Math.toIntExact(frame.legacyMinY());
        int maxX = Math.toIntExact(Math.addExact(frame.legacyMinX(), domain.width() - 1L));
        int maxY = Math.toIntExact(Math.addExact(frame.legacyMinY(), domain.height() - 1L));
        return new WorldBounds(minX, maxX, minY, maxY, BASE_MIN_Z_CELLS, MAX_Z_CELLS);
    }

    private record Fixture(int width, int height, long seed) {}
}
