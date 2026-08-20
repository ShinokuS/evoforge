package io.github.evoforge.simulation.world.atlas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.evoforge.simulation.world.genesis.GenerationRevision;
import io.github.evoforge.simulation.world.genesis.RngRevision;
import io.github.evoforge.simulation.world.genesis.WorldGenerationIntent;
import io.github.evoforge.simulation.world.genesis.WorldGenesis;
import io.github.evoforge.simulation.world.genesis.WorldSpec;
import io.github.evoforge.simulation.world.spatial.WorldBounds;
import org.junit.jupiter.api.Test;

final class InlandLakeBathymetryAlgorithmTest {

    @Test
    void deepensBroadInlandBodyWithBroadAsymmetricTerracesWhilePreservingOtherDomains() {
        WorldBounds bounds = new WorldBounds(0, 40, 0, 40, -32, 16);
        long[] elevation = broadSquareLake(bounds);
        ElevationField before = new DenseElevationField(bounds, elevation);
        WorldGenesis genesis = genesis(bounds);
        ElevationField after = InlandLakeBathymetryAlgorithm.standard().generate(
                genesis, before, InlandLakeBathymetryRecipe.balanced());

        assertTrue(deepest(after, 10, 30, 10, 30) <= -5L * ElevationField.SUBUNITS_PER_CELL);
        assertEquals(-ElevationField.SUBUNITS_PER_CELL, after.elevationSubunitsAt(10, 20),
                "first submerged ring should remain shallow");
        assertEquals(-ElevationField.SUBUNITS_PER_CELL, after.elevationSubunitsAt(11, 20),
                "one-cell depth terraces are not allowed beside the shore");

        int asymmetricPairs = 0;
        for (int offset = 1; offset <= 8; offset++) {
            long west = after.elevationSubunitsAt(20 - offset, 20);
            long east = after.elevationSubunitsAt(20 + offset, 20);
            long south = after.elevationSubunitsAt(20, 20 - offset);
            long north = after.elevationSubunitsAt(20, 20 + offset);
            if (west != east || south != north) asymmetricPairs++;
        }
        assertTrue(asymmetricPairs > 0,
                "broad lake floor should not remain a perfectly symmetric funnel around the geometric center");

        for (int y = 0; y < 41; y++) {
            for (int x = 0; x < 41; x++) {
                long original = before.elevationSubunitsAt(x, y);
                long refined = after.elevationSubunitsAt(x, y);
                assertEquals(original < 0L, refined < 0L,
                        "depth refinement must never change standing-water membership");
                if (original >= 0L || x == 0 || y == 0 || x == 40 || y == 40) {
                    assertEquals(original, refined,
                            "dry terrain and boundary-connected ocean must remain bit-identical");
                }
                if (x > 10 && x <= 30 && y >= 10 && y <= 30) {
                    long west = after.elevationSubunitsAt(x - 1, y);
                    long cardinalFall = Math.abs(refined - west);
                    assertTrue(cardinalFall <= ElevationField.SUBUNITS_PER_CELL,
                            "lake floor must never jump by more than one full Z per cardinal step");
                }
            }
        }

        ElevationField replay = InlandLakeBathymetryAlgorithm.standard().generate(
                genesis, before, InlandLakeBathymetryRecipe.balanced());
        for (int y = 0; y < 41; y++) {
            for (int x = 0; x < 41; x++) {
                assertEquals(after.elevationSubunitsAt(x, y), replay.elevationSubunitsAt(x, y),
                        "asymmetric floor bias must replay deterministically");
            }
        }
    }

    @Test
    void leavesNarrowInlandWaterUntouchedInsteadOfForcingArtificialDepth() {
        WorldBounds bounds = new WorldBounds(0, 12, 0, 12, -16, 16);
        long[] elevation = new long[13 * 13];
        for (int i = 0; i < elevation.length; i++) {
            elevation[i] = 4L * ElevationField.SUBUNITS_PER_CELL;
        }
        for (int y = 5; y <= 7; y++) {
            for (int x = 5; x <= 7; x++) {
                elevation[y * 13 + x] = -2L * ElevationField.SUBUNITS_PER_CELL;
            }
        }
        ElevationField before = new DenseElevationField(bounds, elevation);
        ElevationField after = InlandLakeBathymetryAlgorithm.standard().generate(
                genesis(bounds), before, InlandLakeBathymetryRecipe.balanced());

        for (int y = 0; y < 13; y++) {
            for (int x = 0; x < 13; x++) {
                assertEquals(before.elevationSubunitsAt(x, y), after.elevationSubunitsAt(x, y));
            }
        }
    }

    private static long[] broadSquareLake(WorldBounds bounds) {
        long[] elevation = new long[41 * 41];
        for (int y = 0; y < 41; y++) {
            for (int x = 0; x < 41; x++) {
                int cell = y * 41 + x;
                if (x == 0 || y == 0 || x == 40 || y == 40) {
                    elevation[cell] = -3L * ElevationField.SUBUNITS_PER_CELL;
                } else if (x >= 10 && x <= 30 && y >= 10 && y <= 30) {
                    elevation[cell] = -ElevationField.SUBUNITS_PER_CELL;
                } else {
                    elevation[cell] = 6L * ElevationField.SUBUNITS_PER_CELL;
                }
            }
        }
        return elevation;
    }

    private static long deepest(ElevationField field, int minX, int maxX, int minY, int maxY) {
        long deepest = 0L;
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                deepest = Math.min(deepest, field.elevationSubunitsAt(x, y));
            }
        }
        return deepest;
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
