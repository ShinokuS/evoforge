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
import java.util.ArrayDeque;
import org.junit.jupiter.api.Test;

final class OceanicLandmassGenerationTest {
    private static final long[] REJECTED_RECTANGULAR_SEEDS = {
            1L,
            8_788_863_630_343_935_869L,
            -6_274_351_435_996_232_760L,
            -4_759_010_560_822_749_572L
    };

    @Test
    void balancedOceanClearanceScalesSublinearlyWithWorldSpan() {
        assertEquals(5, calibrationFor(64, 350_000, 250_000, 1L).minimumOceanMarginCells());
        assertEquals(10, calibrationFor(300, 350_000, 250_000, 1L).minimumOceanMarginCells());
        assertEquals(13, calibrationFor(500, 350_000, 250_000, 1L).minimumOceanMarginCells());
    }

    @Test
    void normalLandCoverageIsPreservedInsidePlateSilhouette() {
        WorldBounds bounds = bounds(64);
        WorldGenesis genesis = genesis(bounds, 1L, 350_000, 250_000);
        V12LandformRecipe terrainRecipe = V12LandformRecipe.balanced();
        V12LandformCalibration terrain = V12LandformCalibrator.standard().calibrate(genesis, terrainRecipe);
        LandmassBoundaryCalibration boundary = LandmassBoundaryCalibrator.standard()
                .calibrate(genesis, terrain, LandmassBoundaryRecipe.balanced());
        LandmassSilhouette silhouette = silhouetteFor(genesis, terrain, boundary);
        ElevationField elevation = V14OceanicBaseTerrainGenerator.standard().generate(genesis);

        assertTrue(terrain.landCount() <= silhouette.supportCellCount(),
                "balanced 35% land must fit inside realized plate-derived support");
        assertEquals(terrain.landCount(), countLand(elevation),
                "ordinary authored land coverage must remain exact when it fits support");
        assertGuaranteedOceanMargin(elevation, boundary.minimumOceanMarginCells());
        assertGeographicSilhouette(elevation, "64x64 seed=1 Land=35%");
    }

    @Test
    void maximumFragmentationActuallyCreatesMoreIndependentLandPieces() {
        WorldBounds bounds = bounds(300);
        ElevationField cohesive = V14OceanicBaseTerrainGenerator.standard().generate(
                genesis(bounds, 71_337L, 500_000, 0));
        ElevationField fragmented = V14OceanicBaseTerrainGenerator.standard().generate(
                genesis(bounds, 71_337L, 500_000, 1_000_000));

        int cohesiveComponents = countLandComponents(cohesive);
        int fragmentedComponents = countLandComponents(fragmented);
        assertTrue(fragmentedComponents >= cohesiveComponents + 2,
                "Fragmentation=100% must change topology, not merely coastline roughness: cohesive="
                        + cohesiveComponents + " fragmented=" + fragmentedComponents);
    }

    @Test
    void screenshotRegressionSeedsDoNotReturnToRectangularContinentsAt64() {
        for (long seed : REJECTED_RECTANGULAR_SEEDS) {
            assertGeographicSilhouette(
                    V14OceanicBaseTerrainGenerator.standard().generate(
                            genesis(bounds(64), seed, 350_000, 250_000)),
                    "64x64 seed=" + seed + " Land=35%");
            assertMaximumLandGeographic(64, seed);
        }
    }

    @Test
    void screenshotRegressionSeedsDoNotReturnToRectangularContinentsAt300() {
        for (long seed : REJECTED_RECTANGULAR_SEEDS) {
            assertGeographicSilhouette(
                    V14OceanicBaseTerrainGenerator.standard().generate(
                            genesis(bounds(300), seed, 350_000, 250_000)),
                    "300x300 seed=" + seed + " Land=35%");
            assertMaximumLandGeographic(300, seed);
        }
    }

    @Test
    void unconstrainedSilhouettePathRemainsExactlyEquivalentForAcceptedV12() {
        WorldBounds bounds = bounds(64);
        WorldGenesis genesis = genesis(bounds, 9_913L, 600_000, 250_000);
        V12LandformRecipe recipe = V12LandformRecipe.balanced();
        V12LandformCalibration calibration = V12LandformCalibrator.standard().calibrate(genesis, recipe);
        V12LandformElevationAlgorithm algorithm = new V12LandformElevationAlgorithm();

        ElevationField ordinary = algorithm.generate(genesis, calibration, recipe);
        ElevationField explicitUnconstrained = algorithm.generate(
                genesis,
                calibration,
                recipe,
                LandmassSilhouette.unconstrained(bounds));

        for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
            for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                assertEquals(
                        ordinary.elevationSubunitsAt(x, y),
                        explicitUnconstrained.elevationSubunitsAt(x, y),
                        "accepted V12 path must remain bit-identical");
            }
        }
    }

    private static void assertMaximumLandGeographic(int size, long seed) {
        WorldBounds bounds = bounds(size);
        WorldGenesis genesis = genesis(bounds, seed, 1_000_000, 250_000);
        V12LandformRecipe terrainRecipe = V12LandformRecipe.balanced();
        V12LandformCalibration terrain = V12LandformCalibrator.standard().calibrate(genesis, terrainRecipe);
        LandmassBoundaryCalibration boundary = LandmassBoundaryCalibrator.standard()
                .calibrate(genesis, terrain, LandmassBoundaryRecipe.balanced());
        LandmassSilhouette silhouette = silhouetteFor(genesis, terrain, boundary);
        ElevationField elevation = V14OceanicBaseTerrainGenerator.standard().generate(genesis);
        String context = size + "x" + size + " seed=" + seed + " Land=100%";

        assertEquals(silhouette.supportCellCount(), countLand(elevation),
                "100% authored land must fill the realized plate-derived support: " + context);
        assertTrue(silhouette.supportCellCount() <= boundary.maximumLandCells(),
                "plate-derived support may approach but never exceed finite-world land capacity: " + context);
        assertGuaranteedOceanMargin(elevation, boundary.minimumOceanMarginCells());
        assertGeographicSilhouette(elevation, context);
    }

    private static LandmassSilhouette silhouetteFor(
            WorldGenesis genesis,
            V12LandformCalibration terrain,
            LandmassBoundaryCalibration boundary) {
        LandmassSilhouetteRecipe recipe = LandmassSilhouetteRecipe.balanced();
        LandmassSilhouetteCalibration calibration = LandmassSilhouetteCalibrator.standard()
                .calibrate(genesis, terrain, recipe);
        return LandmassSilhouetteAlgorithm.standard().generate(
                genesis,
                boundary,
                calibration,
                recipe);
    }

    private static LandmassBoundaryCalibration calibrationFor(
            int size,
            int landPpm,
            int fragmentationPpm,
            long seed) {
        WorldGenesis genesis = genesis(bounds(size), seed, landPpm, fragmentationPpm);
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
                            "scale-aware external-ocean clearance must remain submerged");
                }
            }
        }
    }

    private static void assertGeographicSilhouette(ElevationField elevation, String context) {
        WorldBounds bounds = elevation.bounds();
        int minX = bounds.maxX();
        int maxX = bounds.minX();
        int minY = bounds.maxY();
        int maxY = bounds.minY();
        int landCells = 0;
        for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
            for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                if (!isLand(elevation, x, y)) continue;
                landCells++;
                minX = Math.min(minX, x);
                maxX = Math.max(maxX, x);
                minY = Math.min(minY, y);
                maxY = Math.max(maxY, y);
            }
        }
        assertTrue(landCells > 0, "representative silhouette must contain land: " + context);

        int boxWidth = maxX - minX + 1;
        int boxHeight = maxY - minY + 1;
        int boundingArea = Math.multiplyExact(boxWidth, boxHeight);
        assertTrue((long) landCells * 100L < (long) boundingArea * 88L,
                "landmass still reads as a filled axis-aligned bounding rectangle: " + context
                        + " fill=" + landCells + "/" + boundingArea);

        assertNoDominantAxisAlignedCoastline(elevation, 30, context);
    }

    private static void assertNoDominantAxisAlignedCoastline(
            ElevationField elevation,
            int maximumRunPercent,
            String context) {
        WorldBounds bounds = elevation.bounds();
        int width = bounds.maxX() - bounds.minX() + 1;
        int height = bounds.maxY() - bounds.minY() + 1;
        int maximumAllowed = Math.max(4, Math.min(width, height) * maximumRunPercent / 100);
        int maximumObserved = 0;

        for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
            int run = 0;
            for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                boolean coast = isLand(elevation, x, y)
                        && (isWaterOrOutside(elevation, x, y - 1)
                        || isWaterOrOutside(elevation, x, y + 1));
                run = coast ? run + 1 : 0;
                maximumObserved = Math.max(maximumObserved, run);
            }
        }
        for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
            int run = 0;
            for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
                boolean coast = isLand(elevation, x, y)
                        && (isWaterOrOutside(elevation, x - 1, y)
                        || isWaterOrOutside(elevation, x + 1, y));
                run = coast ? run + 1 : 0;
                maximumObserved = Math.max(maximumObserved, run);
            }
        }

        assertTrue(maximumObserved <= maximumAllowed,
                "coastline contains an implausibly dominant axis-aligned run: " + context
                        + " observed=" + maximumObserved + " allowed=" + maximumAllowed);
    }

    private static int countLandComponents(ElevationField elevation) {
        WorldBounds bounds = elevation.bounds();
        int width = bounds.maxX() - bounds.minX() + 1;
        int height = bounds.maxY() - bounds.minY() + 1;
        boolean[] visited = new boolean[Math.multiplyExact(width, height)];
        int components = 0;
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        for (int localY = 0; localY < height; localY++) {
            for (int localX = 0; localX < width; localX++) {
                int start = localY * width + localX;
                if (visited[start] || elevation.elevationSubunitsAt(
                        bounds.minX() + localX,
                        bounds.minY() + localY) <= 0L) continue;
                components++;
                visited[start] = true;
                queue.add(start);
                while (!queue.isEmpty()) {
                    int cell = queue.removeFirst();
                    int x = cell % width;
                    int y = cell / width;
                    if (x > 0) visitLand(elevation, bounds, width, x - 1, y, visited, queue);
                    if (x + 1 < width) visitLand(elevation, bounds, width, x + 1, y, visited, queue);
                    if (y > 0) visitLand(elevation, bounds, width, x, y - 1, visited, queue);
                    if (y + 1 < height) visitLand(elevation, bounds, width, x, y + 1, visited, queue);
                }
            }
        }
        return components;
    }

    private static void visitLand(
            ElevationField elevation,
            WorldBounds bounds,
            int width,
            int x,
            int y,
            boolean[] visited,
            ArrayDeque<Integer> queue) {
        int index = y * width + x;
        if (visited[index]) return;
        visited[index] = true;
        if (elevation.elevationSubunitsAt(bounds.minX() + x, bounds.minY() + y) > 0L) {
            queue.add(index);
        }
    }

    private static boolean isLand(ElevationField elevation, int x, int y) {
        return elevation.elevationSubunitsAt(x, y) > 0L;
    }

    private static boolean isWaterOrOutside(ElevationField elevation, int x, int y) {
        WorldBounds bounds = elevation.bounds();
        if (x < bounds.minX() || x > bounds.maxX() || y < bounds.minY() || y > bounds.maxY()) {
            return true;
        }
        return elevation.elevationSubunitsAt(x, y) < 0L;
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

    private static WorldGenesis genesis(
            WorldBounds bounds,
            long seed,
            int landPpm,
            int fragmentationPpm) {
        WorldGenerationIntent balanced = WorldGenerationIntent.balanced();
        WorldGenerationIntent intent = new WorldGenerationIntent(
                NormalizedValue.ofPartsPerMillion(landPpm),
                balanced.landmassScale(),
                NormalizedValue.ofPartsPerMillion(fragmentationPpm),
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
