package io.github.evoforge.simulation.world.terrain.genesis;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.evoforge.simulation.world.atlas.ElevationField;
import io.github.evoforge.simulation.world.atlas.V12BaseTerrainGenerator;
import io.github.evoforge.simulation.world.continuum.field.ContinuumSampleWindow;
import io.github.evoforge.simulation.world.continuum.field.ContinuumScalarPage;
import io.github.evoforge.simulation.world.continuum.model.ContinuumWorldDomain;
import io.github.evoforge.simulation.world.genesis.GenerationRevision;
import io.github.evoforge.simulation.world.genesis.RngRevision;
import io.github.evoforge.simulation.world.genesis.WorldGenerationIntent;
import io.github.evoforge.simulation.world.genesis.WorldGenesis;
import io.github.evoforge.simulation.world.genesis.WorldSpec;
import io.github.evoforge.simulation.world.spatial.WorldBounds;
import io.github.evoforge.simulation.world.terrain.definition.V15TerrainDefinition;
import io.github.evoforge.simulation.world.terrain.field.V12ExactSlopePageSource;
import io.github.evoforge.simulation.world.terrain.field.V12UnrelaxedLandElevationField;
import org.junit.jupiter.api.Test;

/** Proves the Continuum V12 execution against the actual accepted historical dense generator. */
final class V12HistoricalOracleParityTest {
    private static final int MIN_Z_CELLS = -12;
    private static final int MAX_Z_CELLS = 64;

    @Test
    void exactContinuumV12MatchesHistoricalDenseGeneratorCellForCell() {
        for (Fixture fixture : new Fixture[] {
                new Fixture(37, 29, 1L),
                new Fixture(48, 35, 913L),
                new Fixture(41, 46, -4_759_010_560_822_749_572L)
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
                GenerationRevision.V12,
                RngRevision.V1,
                WorldGenerationIntent.balanced());
        ElevationField historical = V12BaseTerrainGenerator.standard().generate(genesis);

        V15TerrainDefinition definition = V15TerrainDefinition.balanced();
        V12TerrainRecipe recipe = V12TerrainRecipe.balanced();
        V12TerrainCalibration calibration = V12TerrainCalibration.compile(domain, definition, recipe);
        V12LandRankPlan land = V12LandRankPlan.prepareUnconstrained(
                domain, fixture.seed(), calibration, recipe);
        V12UnrelaxedLandElevationField unrelaxed = new V12UnrelaxedLandElevationField(
                domain,
                fixture.seed(),
                land,
                calibration,
                recipe,
                MAX_Z_CELLS);
        V12ContinuumSlopeCalibration slope = V12ContinuumSlopeCalibration.compile(
                calibration, recipe, MAX_Z_CELLS);
        V12ExactSlopePageSource continuum = new V12ExactSlopePageSource(
                domain,
                unrelaxed,
                land,
                slope,
                recipe,
                MIN_Z_CELLS);

        ContinuumScalarPage page = continuum.materialize(new ContinuumSampleWindow(
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
                        "V12 parity failed at seed=" + fixture.seed()
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
        return new WorldBounds(minX, maxX, minY, maxY, MIN_Z_CELLS, MAX_Z_CELLS);
    }

    private record Fixture(int width, int height, long seed) {}
}
