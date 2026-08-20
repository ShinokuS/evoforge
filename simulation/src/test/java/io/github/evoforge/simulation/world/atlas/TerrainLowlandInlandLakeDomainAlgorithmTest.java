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
        InlandLakeDomainCalibration calibration = calibration(41, 170, 3);
        InlandLakeDomainRecipe recipe = permissiveRecipe(3);

        InlandLakeDomain domain = InlandLakeDomainAlgorithm.standard().generate(
                genesis(bounds), base, calibration, recipe);

        assertTrue(domain.lakeCellCount() >= 70, "broad lowland should remain a visible lake domain");
        assertTrue(domain.isLakeAt(20, 20));
        assertFalse(domain.isLakeAt(6, 6));
        assertBoundaryDry(domain, 40);
    }

    @Test
    void morphologicalOpeningRejectsOneCellLowlandCorridorInsteadOfMakingLakeTendril() {
        WorldBounds bounds = new WorldBounds(0, 50, 0, 50, -1, 12);
        long[] elevation = new long[51 * 51];
        long high = 9L * ElevationField.SUBUNITS_PER_CELL;
        long low = 2L * ElevationField.SUBUNITS_PER_CELL;
        for (int y = 0; y < 51; y++) {
            for (int x = 0; x < 51; x++) {
                int cell = y * 51 + x;
                if (x == 0 || y == 0 || x == 50 || y == 50) {
                    elevation[cell] = -ElevationField.SUBUNITS_PER_CELL;
                } else {
                    elevation[cell] = high;
                }
            }
        }
        for (int y = 16; y <= 28; y++) {
            for (int x = 8; x <= 20; x++) elevation[y * 51 + x] = low;
            for (int x = 30; x <= 42; x++) elevation[y * 51 + x] = low;
        }
        for (int x = 21; x <= 29; x++) elevation[22 * 51 + x] = low;

        ElevationField base = new DenseElevationField(bounds, elevation);
        InlandLakeDomainCalibration calibration = new InlandLakeDomainCalibration(
                51, 51, 51 * 51, 49 * 49, 220, 5, 1, 24, 7, 2,
                5L * ElevationField.SUBUNITS_PER_CELL);
        InlandLakeDomain domain = InlandLakeDomainAlgorithm.standard().generate(
                genesis(bounds), base, calibration, permissiveRecipe(2));

        assertTrue(domain.isLakeAt(14, 22));
        assertTrue(domain.isLakeAt(36, 22));
        assertFalse(domain.isLakeAt(25, 22),
                "a one-cell lowland connector must not survive as a lake channel");
    }

    @Test
    void chamferReconstructionKeepsRoundedLowlandCornersOutOfTheLake() {
        WorldBounds bounds = new WorldBounds(0, 60, 0, 60, -1, 12);
        long[] elevation = new long[61 * 61];
        long high = 9L * ElevationField.SUBUNITS_PER_CELL;
        long low = 2L * ElevationField.SUBUNITS_PER_CELL;
        for (int y = 0; y < 61; y++) {
            for (int x = 0; x < 61; x++) {
                int cell = y * 61 + x;
                if (x == 0 || y == 0 || x == 60 || y == 60) {
                    elevation[cell] = -ElevationField.SUBUNITS_PER_CELL;
                    continue;
                }
                int dx = x - 30;
                int dy = y - 30;
                elevation[cell] = dx * dx + dy * dy <= 15 * 15 ? low : high;
            }
        }
        ElevationField base = new DenseElevationField(bounds, elevation);
        InlandLakeDomainCalibration calibration = new InlandLakeDomainCalibration(
                61, 61, 61 * 61, 59 * 59, 500, 6, 1, 24, 9, 1,
                5L * ElevationField.SUBUNITS_PER_CELL);
        InlandLakeDomain domain = InlandLakeDomainAlgorithm.standard().generate(
                genesis(bounds), base, calibration, permissiveRecipe(1));

        assertTrue(domain.isLakeAt(30, 30));
        assertTrue(domain.isLakeAt(30, 39));
        assertTrue(domain.isLakeAt(39, 30));
        assertFalse(domain.isLakeAt(41, 41),
                "rounded lowland reconstruction must not invent a square corner");
    }

    @Test
    void balancedPolicyRejectsLowlandWithoutEnoughInteriorRadiusForFiveZLake() {
        WorldBounds bounds = new WorldBounds(0, 80, 0, 80, -16, 16);
        long[] elevation = new long[81 * 81];
        long high = 8L * ElevationField.SUBUNITS_PER_CELL;
        long low = 2L * ElevationField.SUBUNITS_PER_CELL;
        for (int y = 0; y < 81; y++) {
            for (int x = 0; x < 81; x++) {
                int cell = y * 81 + x;
                if (x == 0 || y == 0 || x == 80 || y == 80) {
                    elevation[cell] = -ElevationField.SUBUNITS_PER_CELL;
                } else if (x >= 33 && x <= 47 && y >= 33 && y <= 47) {
                    elevation[cell] = low;
                } else {
                    elevation[cell] = high;
                }
            }
        }
        ElevationField base = new DenseElevationField(bounds, elevation);
        InlandLakeDomainCalibration calibration = new InlandLakeDomainCalibration(
                81, 81, 81 * 81, 79 * 79, 160, 8, 3, 200, 20, 2,
                5L * ElevationField.SUBUNITS_PER_CELL);

        InlandLakeDomain domain = InlandLakeDomainAlgorithm.standard().generate(
                genesis(bounds), base, calibration, InlandLakeDomainRecipe.balanced());

        assertEquals(0, domain.lakeCellCount(),
                "a lowland without enough honest inradius for the five-Z profile must not become a lake");
    }

    @Test
    void shoreConditioningChangesOnlyLakeCellsAndLeavesNaturalDryReliefBitExact() {
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
                base, domain, V12LandformRecipe.balanced().coast());

        assertEquals(-1L, conditioned.elevationSubunitsAt(10, 10));
        for (int y = 0; y <= 20; y++) {
            for (int x = 0; x <= 20; x++) {
                if (domain.isLakeAt(x, y)) continue;
                assertEquals(base.elevationSubunitsAt(x, y), conditioned.elevationSubunitsAt(x, y),
                        "lake materialization must not manufacture a one-Z dry ridge around the shore");
            }
        }
    }

    private static InlandLakeDomainCalibration calibration(int size, int target, int bodies) {
        return new InlandLakeDomainCalibration(
                size,
                size,
                size * size,
                (size - 2) * (size - 2),
                target,
                6,
                2,
                16,
                5,
                bodies,
                5L * ElevationField.SUBUNITS_PER_CELL);
    }

    private static InlandLakeDomainRecipe permissiveRecipe(int bodies) {
        return new InlandLakeDomainRecipe(
                100_000,
                900_000,
                500_000,
                6,
                50,
                2,
                120,
                18,
                5,
                120,
                bodies);
    }

    private static void assertBoundaryDry(InlandLakeDomain domain, int max) {
        for (int x = 0; x <= max; x++) {
            assertFalse(domain.isLakeAt(x, 0));
            assertFalse(domain.isLakeAt(x, max));
        }
        for (int y = 0; y <= max; y++) {
            assertFalse(domain.isLakeAt(0, y));
            assertFalse(domain.isLakeAt(max, y));
        }
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
