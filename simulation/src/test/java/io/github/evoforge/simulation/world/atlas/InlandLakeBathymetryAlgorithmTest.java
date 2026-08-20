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
    void deepensBroadInlandBodyWhilePreservingDryTerrainOceanAndMembershipExactly() {
        WorldBounds bounds = new WorldBounds(0, 24, 0, 24, -32, 16);
        long[] elevation = new long[25 * 25];
        for (int y = 0; y < 25; y++) {
            for (int x = 0; x < 25; x++) {
                int cell = y * 25 + x;
                if (x == 0 || y == 0 || x == 24 || y == 24) {
                    elevation[cell] = -3L * ElevationField.SUBUNITS_PER_CELL;
                } else if (x >= 7 && x <= 17 && y >= 7 && y <= 17) {
                    elevation[cell] = -ElevationField.SUBUNITS_PER_CELL;
                } else {
                    elevation[cell] = 6L * ElevationField.SUBUNITS_PER_CELL;
                }
            }
        }
        ElevationField before = new DenseElevationField(bounds, elevation);
        ElevationField after = InlandLakeBathymetryAlgorithm.standard().generate(
                genesis(bounds), before, InlandLakeBathymetryRecipe.balanced());

        assertTrue(after.elevationSubunitsAt(12, 12) <= -5L * ElevationField.SUBUNITS_PER_CELL);
        assertEquals(-ElevationField.SUBUNITS_PER_CELL, after.elevationSubunitsAt(7, 12),
                "first submerged ring should remain shallow");

        for (int y = 0; y < 25; y++) {
            for (int x = 0; x < 25; x++) {
                long original = before.elevationSubunitsAt(x, y);
                long refined = after.elevationSubunitsAt(x, y);
                assertEquals(original < 0L, refined < 0L,
                        "depth refinement must never change standing-water membership");
                if (original >= 0L || x == 0 || y == 0 || x == 24 || y == 24) {
                    assertEquals(original, refined,
                            "dry terrain and boundary-connected ocean must remain bit-identical");
                }
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

    private static WorldGenesis genesis(WorldBounds bounds) {
        return new WorldGenesis(
                new WorldSpec(bounds),
                71_337L,
                GenerationRevision.V15,
                RngRevision.V1,
                WorldGenerationIntent.balanced());
    }
}
