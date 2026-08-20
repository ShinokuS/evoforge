package io.github.evoforge.simulation.world.atlas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.evoforge.simulation.definition.NormalizedValue;
import io.github.evoforge.simulation.world.genesis.GenerationRevision;
import io.github.evoforge.simulation.world.genesis.RngRevision;
import io.github.evoforge.simulation.world.genesis.WorldGenerationIntent;
import io.github.evoforge.simulation.world.genesis.WorldGenesis;
import io.github.evoforge.simulation.world.genesis.WorldSpec;
import io.github.evoforge.simulation.world.spatial.WorldBounds;
import org.junit.jupiter.api.Test;

final class OceanicLandmassGenerationTest {

    @Test
    void balancedBoundaryScaleKeepsARealGuaranteedOceanMargin() {
        assertEquals(5, calibrationFor(64, 350_000, 1L).minimumOceanMarginCells());
        assertEquals(9, calibrationFor(300, 350_000, 1L).minimumOceanMarginCells());
        assertEquals(12, calibrationFor(500, 350_000, 1L).minimumOceanMarginCells());
    }

    @Test
    void normalLandCoverageIsPreservedInsteadOfBeingClippedByTheOceanDomain() {
        WorldBounds bounds = bounds(64);
        WorldGenesis genesis = genesis(bounds, 1L, 350_000);
        V12LandformRecipe terrainRecipe = V12LandformRecipe.balanced();
        V12LandformCalibration terrain = V12LandformCalibrator.standard().calibrate(genesis, terrainRecipe);
        LandmassBoundaryCalibration boundary = LandmassBoundaryCalibrator.standard()
                .calibrate(genesis, terrain, LandmassBoundaryRecipe.balanced());
        ElevationField elevation = V14OceanicBaseTerrainGenerator.standard().generate(genesis);

        assertTrue(terrain.landCount() < boundary.maximumLandCells(),
                "balanced 35% land should fit inside the organic oceanic domain");
        assertEquals(terrain.landCount(), countLand(elevation),
                "ordinary authored land coverage must be preserved exactly when it fits the domain");
        assertGuaranteedOceanMargin(elevation, boundary.minimumOceanMarginCells());
    }

    @Test
    void maximumLandAuthorsAnOrganicDomainInsteadOfAnInsetRectangleAt64() {
        assertOrganicMaximumDomain(64, 1L);
    }

    @Test
    void maximumLandAuthorsAnOrganicDomainInsteadOfAnInsetRectangleAt300() {
        assertOrganicMaximumDomain(300, 72_670_605_181_775_852L);
    }

    @Test
    void unconstrainedAlgorithmPathRemainsExactlyEquivalentForAcceptedV12() {
        WorldBounds bounds = bounds(64);
        WorldGenesis genesis = genesis(bounds, 9_913L, 600_000);
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

    private static void assertOrganicMaximumDomain(int size, long seed) {
        WorldBounds bounds = bounds(size);
        WorldGenesis genesis = genesis(bounds, seed, 1_000_000);
        V12LandformRecipe terrainRecipe = V12LandformRecipe.balanced();
        V12LandformCalibration terrain = V12LandformCalibrator.standard().calibrate(genesis, terrainRecipe);
        LandmassBoundaryCalibration boundary = LandmassBoundaryCalibrator.standard()
                .calibrate(genesis, terrain, LandmassBoundaryRecipe.balanced());
        ElevationField elevation = V14OceanicBaseTerrainGenerator.standard().generate(genesis);

        int landCells = countLand(elevation);
        assertEquals(boundary.maximumLandCells(), landCells,
                "100% authored land means maximum terrestrial domain, not the rectangular world area");
        assertGuaranteedOceanMargin(elevation, boundary.minimumOceanMarginCells());

        int minX = bounds.maxX();
        int maxX = bounds.minX();
        int minY = bounds.maxY();
        int maxY = bounds.minY();
        for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
            for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                if (elevation.elevationSubunitsAt(x, y) <= 0L) continue;
                minX = Math.min(minX, x);
                maxX = Math.max(maxX, x);
                minY = Math.min(minY, y);
                maxY = Math.max(maxY, y);
            }
        }
        int boundingArea = Math.multiplyExact(maxX - minX + 1, maxY - minY + 1);
        assertTrue((long) landCells * 100L < (long) boundingArea * 92L,
                "maximum land silhouette must contain substantial bays/cutouts instead of filling an axis-aligned box");
    }

    private static LandmassBoundaryCalibration calibrationFor(int size, int landPpm, long seed) {
        WorldGenesis genesis = genesis(bounds(size), seed, landPpm);
        V12LandformCalibration terrain = V12LandformCalibrator.standard()
                .calibrate(genesis, V12LandformRecipe.balanced());
        return LandmassBoundaryCalibrator.standard()
                .calibrate(genesis, terrain, LandmassBoundaryRecipe.balanced());
    }

    private static void assertGuaranteedOceanMargin(ElevationField elevation, int margin) {
        WorldBounds bounds = elevation.bounds();
        for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
            for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                if (edgeDistance(bounds, x, y) < margin) {
                    assertTrue(elevation.elevationSubunitsAt(x, y) < 0L,
                            "guaranteed external-ocean margin must remain water");
                }
            }
        }
    }

    private static int countLand(ElevationField elevation) {
        WorldBounds bounds = elevation.bounds();
        int count = 0;
        for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
            for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                if (elevation.elevationSubunitsAt(x, y) > 0L) count++;
            }
        }
        return count;
    }

    private static WorldBounds bounds(int size) {
        int min = -size / 2;
        return new WorldBounds(min, min + size - 1, min, min + size - 1, -16, 96);
    }

    private static WorldGenesis genesis(WorldBounds bounds, long seed, int landPpm) {
        WorldGenerationIntent balanced = WorldGenerationIntent.balanced();
        WorldGenerationIntent intent = new WorldGenerationIntent(
                NormalizedValue.ofPartsPerMillion(landPpm),
                balanced.landmassScale(),
                balanced.fragmentation(),
                balanced.relief(),
                balanced.localRelief(),
                balanced.landformScale(),
                balanced.ruggedness(),
                balanced.mountains());
        return new WorldGenesis(
                new WorldSpec(bounds),
                seed,
                GenerationRevision.V14,
                RngRevision.V1,
                intent);
    }

    private static int edgeDistance(WorldBounds bounds, int x, int y) {
        return Math.min(
                Math.min(x - bounds.minX(), bounds.maxX() - x),
                Math.min(y - bounds.minY(), bounds.maxY() - y));
    }
}
