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

final class OceanicLandmassGenerationTest {

    @Test
    void balancedBoundaryScaleReservesBroadOceanMargins() {
        assertEquals(8, calibrationFor(64).minimumOceanMarginCells());
        assertEquals(18, calibrationFor(300).minimumOceanMarginCells());
        assertEquals(23, calibrationFor(500).minimumOceanMarginCells());
    }

    @Test
    void oceanicBaseReservesMarginDuringRankSelectionWithoutLosingRequestedLand() {
        WorldBounds bounds = bounds(64);
        WorldGenesis genesis = genesis(bounds, 1L);
        V12LandformRecipe terrainRecipe = V12LandformRecipe.balanced();
        V12LandformCalibration terrain = V12LandformCalibrator.standard().calibrate(genesis, terrainRecipe);
        LandmassBoundaryCalibration boundary = LandmassBoundaryCalibrator.standard()
                .calibrate(genesis, LandmassBoundaryRecipe.balanced());
        ElevationField elevation = V14OceanicBaseTerrainGenerator.standard().generate(genesis);

        int landCells = 0;
        for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
            for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                long value = elevation.elevationSubunitsAt(x, y);
                if (value > 0L) landCells++;
                if (edgeDistance(bounds, x, y) < boundary.minimumOceanMarginCells()) {
                    assertTrue(value < 0L,
                            "hard ocean margin must be excluded before land-rank selection");
                }
            }
        }

        assertEquals(terrain.landCount(), landCells,
                "normal land coverage must be redistributed into the interior, not clipped away");
        assertTrue(landCells > 0, "oceanic domain must still contain generated land");
    }

    @Test
    void unconstrainedAlgorithmPathRemainsExactlyEquivalentForAcceptedV12() {
        WorldBounds bounds = bounds(64);
        WorldGenesis genesis = genesis(bounds, 9_913L);
        V12LandformRecipe recipe = V12LandformRecipe.balanced();
        V12LandformCalibration calibration = V12LandformCalibrator.standard().calibrate(genesis, recipe);
        V12LandformElevationAlgorithm algorithm = new V12LandformElevationAlgorithm();

        ElevationField legacyPath = algorithm.generate(genesis, calibration, recipe);
        ElevationField explicitOpenPath = algorithm.generate(
                genesis,
                calibration,
                recipe,
                LandmassBoundaryCalibration.unconstrained(calibration.area()));

        for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
            for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                assertEquals(
                        legacyPath.elevationSubunitsAt(x, y),
                        explicitOpenPath.elevationSubunitsAt(x, y),
                        "accepted V12 path must remain bit-identical");
            }
        }
    }

    private static LandmassBoundaryCalibration calibrationFor(int size) {
        WorldGenesis genesis = genesis(bounds(size), 1L);
        return LandmassBoundaryCalibrator.standard()
                .calibrate(genesis, LandmassBoundaryRecipe.balanced());
    }

    private static WorldBounds bounds(int size) {
        int min = -size / 2;
        return new WorldBounds(min, min + size - 1, min, min + size - 1, -16, 96);
    }

    private static WorldGenesis genesis(WorldBounds bounds, long seed) {
        return new WorldGenesis(
                new WorldSpec(bounds),
                seed,
                GenerationRevision.V12,
                RngRevision.V1,
                WorldGenerationIntent.balanced());
    }

    private static int edgeDistance(WorldBounds bounds, int x, int y) {
        return Math.min(
                Math.min(x - bounds.minX(), bounds.maxX() - x),
                Math.min(y - bounds.minY(), bounds.maxY() - y));
    }
}
