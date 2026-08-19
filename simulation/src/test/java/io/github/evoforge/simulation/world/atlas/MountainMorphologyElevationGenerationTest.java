package io.github.evoforge.simulation.world.atlas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.evoforge.simulation.definition.NormalizedValue;
import io.github.evoforge.simulation.world.genesis.GenerationRevision;
import io.github.evoforge.simulation.world.genesis.MountainIntent;
import io.github.evoforge.simulation.world.genesis.RngRevision;
import io.github.evoforge.simulation.world.genesis.WorldGenerationIntent;
import io.github.evoforge.simulation.world.genesis.WorldGenesis;
import io.github.evoforge.simulation.world.genesis.WorldSpec;
import io.github.evoforge.simulation.world.spatial.WorldBounds;
import org.junit.jupiter.api.Test;

final class MountainMorphologyElevationGenerationTest {
    private static final WorldBounds V13_BOUNDS = new WorldBounds(-64, 63, -64, 63, -12, 96);
    private static final WorldBounds V12_BASE_BOUNDS = new WorldBounds(-64, 63, -64, 63, -12, 12);
    private static final WorldBounds LARGE_V13_BOUNDS = new WorldBounds(-128, 127, -128, 127, -12, 96);
    private static final WorldBounds LARGE_V12_BASE_BOUNDS = new WorldBounds(-128, 127, -128, 127, -12, 12);

    @Test
    void disabledMountainsPreserveAcceptedV12BaseElevationsExactly() {
        WorldGenerationIntent intent = intent(MountainIntent.none());
        ElevationField base = base(71L, intent, V12_BASE_BOUNDS);
        ElevationField v13 = V13MountainTerrainGenerator.standard().generate(genesis(71L, intent));

        for (int y = V13_BOUNDS.minY(); y <= V13_BOUNDS.maxY(); y++) {
            for (int x = V13_BOUNDS.minX(); x <= V13_BOUNDS.maxX(); x++) {
                assertEquals(
                        base.elevationSubunitsAt(x, y),
                        v13.elevationSubunitsAt(x, y),
                        "V13 with zero mountain abundance must be the accepted V12 surface");
            }
        }
    }

    @Test
    void mountainOverlayNeverChangesV12LandOceanMembership() {
        WorldGenerationIntent intent = intent(mountains(1_000_000, 800_000, 500_000, 750_000, 700_000, true, 300_000));
        ElevationField base = base(913L, intent, V12_BASE_BOUNDS);
        ElevationField mountains = V13MountainTerrainGenerator.standard().generate(genesis(913L, intent));

        for (int y = V13_BOUNDS.minY(); y <= V13_BOUNDS.maxY(); y++) {
            for (int x = V13_BOUNDS.minX(); x <= V13_BOUNDS.maxX(); x++) {
                assertEquals(
                        base.elevationSubunitsAt(x, y) > 0L,
                        mountains.elevationSubunitsAt(x, y) > 0L,
                        "mountains may reshape land but may not create or delete coastline cells");
            }
        }
    }

    @Test
    void dedicatedMountainsCanUseVerticalHeadroomBeyondTheEntireV12BaseRange() {
        WorldGenerationIntent intent = intentWithLandCoverage(
                1_000_000,
                mountains(1_000_000, 1_000_000, 1_000_000, 650_000, 1_000_000, false, 0));
        long seed = 117L;
        ElevationField base = base(seed, intent, LARGE_V12_BASE_BOUNDS);
        ElevationField mountains = V13MountainTerrainGenerator.standard().generate(
                genesis(seed, intent, LARGE_V13_BOUNDS));

        assertTrue(
                maximumMountainUplift(base, mountains) > 12L * ElevationField.SUBUNITS_PER_CELL,
                "a sufficiently large authored structure must be able to add more vertical range than V12 owns in total");
    }

    @Test
    void mountainHeightIsARealSemanticControl() {
        WorldGenerationIntent lowIntent = intentWithLandCoverage(
                1_000_000,
                mountains(1_000_000, 150_000, 500_000, 600_000, 650_000, false, 0));
        WorldGenerationIntent highIntent = intentWithLandCoverage(
                1_000_000,
                mountains(1_000_000, 850_000, 500_000, 600_000, 650_000, false, 0));
        long seed = 411L;
        ElevationField base = base(seed, lowIntent, V12_BASE_BOUNDS);
        ElevationField low = V13MountainTerrainGenerator.standard().generate(genesis(seed, lowIntent));
        ElevationField high = V13MountainTerrainGenerator.standard().generate(genesis(seed, highIntent));

        long lowMaximum = maximumMountainUplift(base, low);
        long highMaximum = maximumMountainUplift(base, high);
        assertTrue(
                highMaximum > lowMaximum,
                "higher Height intent must increase actual mountain uplift; low="
                        + lowMaximum + ", high=" + highMaximum);
    }

    @Test
    void mountainLayerObeysAbstractCardinalRiseBudget() {
        WorldGenerationIntent intent = intent(mountains(
                1_000_000,
                1_000_000,
                350_000,
                650_000,
                600_000,
                false,
                0));
        long seed = 19_731L;
        WorldGenesis mountainGenesis = genesis(seed, intent);
        MountainRecipe recipe = MountainRecipe.balanced();
        MountainCalibration calibration = MountainCalibrator.standard().calibrate(mountainGenesis, recipe);
        ElevationField base = base(seed, intent, V12_BASE_BOUNDS);
        ElevationField mountains = V13MountainTerrainGenerator.standard().generate(mountainGenesis);

        long maximumUpliftStep = maximumCardinalUpliftStep(base, mountains);
        assertTrue(
                maximumUpliftStep <= calibration.maximumCardinalRiseSubunits() + 1L,
                "mountain synthesis must enforce its geometry-only rise budget independently of Shape fitting");
    }

    @Test
    void ordinaryMountainSettingsAvoidMultiCellLandWalls() {
        WorldGenerationIntent intent = intent(mountains(
                1_000_000,
                520_000,
                500_000,
                550_000,
                600_000,
                false,
                0));
        ElevationField mountains = V13MountainTerrainGenerator.standard().generate(genesis(1L, intent));

        assertTrue(
                maximumCardinalLandStep(mountains) < 2L * ElevationField.SUBUNITS_PER_CELL,
                "ordinary V13 mountain settings should remain broad enough to avoid multi-cell land walls");
    }

    @Test
    void plateauSelectionChangesMountainContributionWithoutChangingSeedOrBaseWorld() {
        WorldGenerationIntent normalIntent = intentWithLandCoverage(
                1_000_000,
                mountains(1_000_000, 650_000, 700_000, 550_000, 600_000, false, 0));
        WorldGenerationIntent plateauIntent = intentWithLandCoverage(
                1_000_000,
                mountains(1_000_000, 650_000, 700_000, 550_000, 600_000, true, 1_000_000));
        long seed = 5_111L;
        ElevationField base = base(seed, normalIntent, V12_BASE_BOUNDS);
        ElevationField normal = V13MountainTerrainGenerator.standard().generate(genesis(seed, normalIntent));
        ElevationField plateau = V13MountainTerrainGenerator.standard().generate(genesis(seed, plateauIntent));

        assertTrue(maximumMountainUplift(base, normal) > 0L);
        assertTrue(maximumMountainUplift(base, plateau) > 0L);
        assertNotEquals(mountainUpliftHash(base, normal), mountainUpliftHash(base, plateau));
    }

    @Test
    void sameGenesisReproducesTheSameMountainSurface() {
        WorldGenesis genesis = genesis(8_031L, intent(MountainIntent.balanced()));
        ElevationField first = V13MountainTerrainGenerator.standard().generate(genesis);
        ElevationField second = V13MountainTerrainGenerator.standard().generate(genesis);

        assertEquals(surfaceHash(first), surfaceHash(second));
    }

    private static ElevationField base(long seed, WorldGenerationIntent intent, WorldBounds bounds) {
        return V12BaseTerrainGenerator.standard().generate(new WorldGenesis(
                new WorldSpec(bounds),
                seed,
                GenerationRevision.V12,
                RngRevision.V1,
                intent));
    }

    private static WorldGenesis genesis(long seed, WorldGenerationIntent intent) {
        return genesis(seed, intent, V13_BOUNDS);
    }

    private static WorldGenesis genesis(long seed, WorldGenerationIntent intent, WorldBounds bounds) {
        return new WorldGenesis(
                new WorldSpec(bounds),
                seed,
                GenerationRevision.V13,
                RngRevision.V1,
                intent);
    }

    private static WorldGenerationIntent intent(MountainIntent mountains) {
        return intentWithLandCoverage(650_000, mountains);
    }

    private static WorldGenerationIntent intentWithLandCoverage(
            int landCoverage,
            MountainIntent mountains) {
        return new WorldGenerationIntent(
                normalized(landCoverage),
                normalized(750_000),
                normalized(250_000),
                normalized(600_000),
                normalized(450_000),
                normalized(500_000),
                normalized(350_000),
                mountains);
    }

    private static MountainIntent mountains(
            int abundance,
            int height,
            int scale,
            int chaininess,
            int sharpness,
            boolean plateaus,
            int plateauProbability) {
        return new MountainIntent(
                normalized(abundance),
                normalized(height),
                normalized(scale),
                normalized(chaininess),
                normalized(sharpness),
                plateaus,
                normalized(plateauProbability));
    }

    private static NormalizedValue normalized(int ppm) {
        return NormalizedValue.ofPartsPerMillion(ppm);
    }

    private static long maximumMountainUplift(ElevationField base, ElevationField mountains) {
        long maximum = 0L;
        WorldBounds bounds = mountains.bounds();
        for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
            for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                maximum = Math.max(
                        maximum,
                        mountains.elevationSubunitsAt(x, y) - base.elevationSubunitsAt(x, y));
            }
        }
        return maximum;
    }

    private static long maximumCardinalLandStep(ElevationField field) {
        long maximum = 0L;
        WorldBounds bounds = field.bounds();
        for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
            for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                long here = field.elevationSubunitsAt(x, y);
                if (here <= 0L) continue;
                if (x < bounds.maxX()) {
                    long right = field.elevationSubunitsAt(x + 1, y);
                    if (right > 0L) maximum = Math.max(maximum, Math.abs(here - right));
                }
                if (y < bounds.maxY()) {
                    long up = field.elevationSubunitsAt(x, y + 1);
                    if (up > 0L) maximum = Math.max(maximum, Math.abs(here - up));
                }
            }
        }
        return maximum;
    }

    private static long maximumCardinalUpliftStep(ElevationField base, ElevationField mountains) {
        long maximum = 0L;
        WorldBounds bounds = mountains.bounds();
        for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
            for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                long baseHere = base.elevationSubunitsAt(x, y);
                if (baseHere <= 0L) continue;
                long upliftHere = mountains.elevationSubunitsAt(x, y) - baseHere;
                if (x < bounds.maxX() && base.elevationSubunitsAt(x + 1, y) > 0L) {
                    long upliftRight = mountains.elevationSubunitsAt(x + 1, y)
                            - base.elevationSubunitsAt(x + 1, y);
                    maximum = Math.max(maximum, Math.abs(upliftHere - upliftRight));
                }
                if (y < bounds.maxY() && base.elevationSubunitsAt(x, y + 1) > 0L) {
                    long upliftUp = mountains.elevationSubunitsAt(x, y + 1)
                            - base.elevationSubunitsAt(x, y + 1);
                    maximum = Math.max(maximum, Math.abs(upliftHere - upliftUp));
                }
            }
        }
        return maximum;
    }

    private static long mountainUpliftHash(ElevationField base, ElevationField mountains) {
        long hash = 0xcbf29ce484222325L;
        WorldBounds bounds = mountains.bounds();
        for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
            for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                hash ^= mountains.elevationSubunitsAt(x, y) - base.elevationSubunitsAt(x, y);
                hash *= 0x100000001b3L;
            }
        }
        return hash;
    }

    private static long surfaceHash(ElevationField field) {
        long hash = 0xcbf29ce484222325L;
        WorldBounds bounds = field.bounds();
        for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
            for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                hash ^= field.elevationSubunitsAt(x, y);
                hash *= 0x100000001b3L;
            }
        }
        return hash;
    }
}
