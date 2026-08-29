package io.github.evoforge.simulation.world.terrain.genesis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.evoforge.simulation.definition.NormalizedValue;
import io.github.evoforge.simulation.world.atlas.ElevationField;
import io.github.evoforge.simulation.world.atlas.V15InlandLakeTerrainGenerator;
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

/** Full-world cell-for-cell proof against the accepted historical V15 dense generator. */
final class V15HistoricalOracleParityTest {
    private static final int MIN_Z_CELLS = -96;
    private static final int MAX_Z_CELLS = 96;
    private static final long SEED = 4_859_186_304_997_574_751L;

    @Test
    void exactContinuumV15MatchesHistoricalDenseGeneratorAtReferenceSizeWithActiveInlandLake() {
        assertExactParity(300, true);
    }

    @Test
    void exactContinuumV15StillMatchesHistoricalDenseGeneratorPastFormerPlanningCutoff() {
        // 320 is deliberately above the removed 300-cell surrogate-planning threshold.
        // A future reintroduction of "generate small then scale" must fail cell-for-cell here.
        assertExactParity(320, false);
    }

    private static void assertExactParity(int side, boolean requireActiveLake) {
        ContinuumWorldDomain domain = new ContinuumWorldDomain(side, side);
        V15TerrainCoordinateFrame frame = V15TerrainCoordinateFrame.centered(domain);
        WorldBounds bounds = historicalBounds(frame, domain);
        WorldGenerationIntent balanced = WorldGenerationIntent.balanced();
        WorldGenerationIntent intent = new WorldGenerationIntent(
                NormalizedValue.ofPartsPerMillion(830_000),
                NormalizedValue.ofPartsPerMillion(750_000),
                NormalizedValue.ofPartsPerMillion(120_000),
                NormalizedValue.ofPartsPerMillion(600_000),
                NormalizedValue.ofPartsPerMillion(450_000),
                balanced.landformScale(),
                balanced.ruggedness(),
                balanced.mountains());
        WorldGenesis genesis = new WorldGenesis(
                new WorldSpec(bounds),
                SEED,
                GenerationRevision.V15,
                RngRevision.V1,
                intent);
        ElevationField historical = V15InlandLakeTerrainGenerator.standard().generate(genesis);

        V15TerrainDefinition terrainDefinition = new V15TerrainDefinition(
                intent.landCoverage(),
                intent.landmassScale(),
                intent.fragmentation(),
                intent.relief(),
                intent.localRelief(),
                intent.landformScale(),
                intent.ruggedness());
        V15ContinuumTerrainPlan continuum = V15ContinuumTerrainPlan.prepare(
                domain,
                SEED,
                terrainDefinition,
                V13MountainDefinition.balanced(),
                MIN_Z_CELLS,
                MAX_Z_CELLS);
        if (requireActiveLake) {
            assertTrue(
                    continuum.lakeBase().lakeDomain().lakeCellCount() > 0,
                    "full V15 oracle fixture must exercise the inland-lake path");
        }
        ContinuumScalarPage page = continuum.elevationPages().materialize(
                new ContinuumSampleWindow(0L, 0L, side, side, 1L));

        for (int y = 0; y < side; y++) {
            int legacyY = Math.toIntExact(frame.legacyY(y));
            for (int x = 0; x < side; x++) {
                int legacyX = Math.toIntExact(frame.legacyX(x));
                assertEquals(
                        historical.elevationSubunitsAt(legacyX, legacyY),
                        Math.round(page.sample(x, y)),
                        "V15 parity failed at side=" + side
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
}
