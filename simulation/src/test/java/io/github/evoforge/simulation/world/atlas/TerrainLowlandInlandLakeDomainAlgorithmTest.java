package io.github.evoforge.simulation.world.atlas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.evoforge.simulation.world.genesis.GenerationRevision;
import io.github.evoforge.simulation.world.genesis.RngRevision;
import io.github.evoforge.simulation.world.genesis.WorldGenerationIntent;
import io.github.evoforge.simulation.world.genesis.WorldGenesis;
import io.github.evoforge.simulation.world.genesis.WorldSpec;
import io.github.evoforge.simulation.world.spatial.WorldBounds;
import org.junit.jupiter.api.Test;

final class TerrainLowlandInlandLakeDomainAlgorithmTest {

    @Test
    void selectsBroadInteriorLowlandWithoutTouchingExistingWaterOrHighGround() {
        WorldBounds bounds = new WorldBounds(0, 40, 0, 40, -1, 12);
        long[] elevation = new long[41 * 41];
        long high = 8L * ElevationField.SUBUNITS_PER_CELL;
        long low = 2L * ElevationField.SUBUNITS_PER_CELL;
        for (int y = 0; y < 41; y++) {
            for (int x = 0; x < 41; x++) {
                int cell = y * 41 + x;
                if (x == 0 || y == 0 || x == 40 || y == 40) {
                    elevation[cell] = -ElevationField.SUBUNITS_PER_CELL;
                } else if (x >= 13 && x <= 27 && y >= 14 && y <= 26) {
                    elevation[cell] = low;
                } else {
                    elevation[cell] = high;
                }
            }
        }
        ElevationField base = new DenseElevationField(bounds, elevation);
        InlandLakeDomainCalibration calibration = new InlandLakeDomainCalibration(
                41,
                41,
                41 * 41,
                39 * 39,
                170,
                6,
                2,
                16,
                3,
                3,
                5L * ElevationField.SUBUNITS_PER_CELL);
        InlandLakeDomainRecipe recipe = new InlandLakeDomainRecipe(
                100_000,
                900_000,
                500_000,
                6,
                50,
                2,
                120,
                18,
                3,
                120,
                3);

        InlandLakeDomain domain = InlandLakeDomainAlgorithm.standard().generate(
                genesis(bounds),
                base,
                calibration,
                recipe);

        assertTrue(domain.lakeCellCount() >= 100, "broad lowland should become a visible lake domain");
        assertTrue(domain.isLakeAt(20, 20));
        assertFalse(domain.isLakeAt(6, 6));
        for (int x = 0; x <= 40; x++) {
            assertFalse(domain.isLakeAt(x, 0));
            assertFalse(domain.isLakeAt(x, 40));
        }
        for (int y = 0; y <= 40; y++) {
            assertFalse(domain.isLakeAt(0, y));
            assertFalse(domain.isLakeAt(40, y));
        }
    }

    @Test
    void shoreConditioningCreatesOnlyZ0WaterAndLeavesFarTerrainExact() {
        WorldBounds bounds = new WorldBounds(0, 20, 0, 20, -1, 12);
        long[] elevation = new long[21 * 21];
        long ordinary = 7L * ElevationField.SUBUNITS_PER_CELL;
        for (int i = 0; i < elevation.length; i++) elevation[i] = ordinary;
        for (int x = 0; x <= 20; x++) {
            elevation[x] = -ElevationField.SUBUNITS_PER_CELL;
            elevation[20 * 21 + x] = -ElevationField.SUBUNITS_PER_CELL;
        }
        for (int y = 0; y <= 20; y++) {
            elevation[y * 21] = -ElevationField.SUBUNITS_PER_CELL;
            elevation[y * 21 + 20] = -ElevationField.SUBUNITS_PER_CELL;
        }
        ElevationField base = new DenseElevationField(bounds, elevation);
        boolean[] lake = new boolean[elevation.length];
        for (int y = 9; y <= 11; y++) {
            for (int x = 9; x <= 11; x++) lake[y * 21 + x] = true;
        }
        InlandLakeDomain domain = new InlandLakeDomain(bounds, lake, 9);

        ElevationField conditioned = InlandLakeShoreConditioningAlgorithm.standard().condition(
                base,
                domain,
                V12LandformRecipe.balanced().coast());

        assertEquals(-1L, conditioned.elevationSubunitsAt(10, 10));
        assertEquals(base.elevationSubunitsAt(0, 10), conditioned.elevationSubunitsAt(0, 10));
        assertEquals(base.elevationSubunitsAt(2, 2), conditioned.elevationSubunitsAt(2, 2));
        assertTrue(conditioned.elevationSubunitsAt(8, 10) > 0L);
        assertTrue(conditioned.elevationSubunitsAt(8, 10) < ordinary,
                "dry shore must approach Z=0 without becoming water");
    }

    private static WorldGenesis genesis(WorldBounds bounds) {
        return new WorldGenesis(
                new WorldSpec(bounds),
                71_337L,
                GenerationRevision.V15,
                RngRevision.V1,
                WorldGenerationIntent.balanced());
    }
}
