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

    @Test
    void disabledMountainsPreserveAcceptedV12BaseElevationsExactly() {
        WorldGenerationIntent intent = intent(MountainIntent.none());
        ElevationField base = V12BaseTerrainGenerator.standard().generate(new WorldGenesis(
                new WorldSpec(V12_BASE_BOUNDS),
                71L,
                GenerationRevision.V12,
                RngRevision.V1,
                intent));
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
        ElevationField base = V12BaseTerrainGenerator.standard().generate(new WorldGenesis(
                new WorldSpec(V12_BASE_BOUNDS),
                913L,
                GenerationRevision.V12,
                RngRevision.V1,
                intent));
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
    void dedicatedMountainsUseVerticalHeadroomAboveV12BaseRange() {
        WorldGenerationIntent intent = intent(mountains(1_000_000, 750_000, 450_000, 650_000, 650_000, false, 0));
        ElevationField mountains = V13MountainTerrainGenerator.standard().generate(genesis(117L, intent));

        assertTrue(
                maximumLandHeight(mountains) > 12L * ElevationField.SUBUNITS_PER_CELL,
                "V13 must create actual mountain elevation above the accepted V12 base ceiling");
    }

    @Test
    void mountainHeightIsARealSemanticControl() {
        WorldGenerationIntent lowIntent = intent(mountains(1_000_000, 150_000, 500_000, 600_000, 650_000, false, 0));
        WorldGenerationIntent highIntent = intent(mountains(1_000_000, 850_000, 500_000, 600_000, 650_000, false, 0));

        long lowMaximum = maximumLandHeight(
                V13MountainTerrainGenerator.standard().generate(genesis(411L, lowIntent)));
        long highMaximum = maximumLandHeight(
                V13MountainTerrainGenerator.standard().generate(genesis(411L, highIntent)));

        assertTrue(highMaximum > lowMaximum, "higher mountain height intent must increase reachable summit elevation");
    }

    @Test
    void composedMountainSurfaceObeysAbstractCardinalRiseBudgetAwayFromCoast() {
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
        ElevationField base = V12BaseTerrainGenerator.standard().generate(new WorldGenesis(
                new WorldSpec(V12_BASE_BOUNDS),
                seed,
                GenerationRevision.V12,
                RngRevision.V1,
                intent));
        ElevationField mountains = V13MountainTerrainGenerator.standard().generate(mountainGenesis);

        long maximumFinalStep = maximumInteriorAffectedFinalStep(base, mountains);
        long budget = calibration.maximumCardinalRiseSubunits() + 1L;
        assertTrue(
                maximumFinalStep <= budget,
                "V13 must constrain the composed V12 + mountain surface, not only the uplift delta; "
                        + "maxStep=" + maximumFinalStep + ", budget=" + budget);
    }

    @Test
    void ordinaryMountainSettingsKeepFinalLandStepsBelowHalfACell() {
        WorldGenerationIntent intent = intent(mountains(
                1_000_000,
                520_000,
                500_000,
                550_000,
                600_000,
                false,
                0));
        long seed = 1L;
        ElevationField base = V12BaseTerrainGenerator.standard().generate(new WorldGenesis(
                new WorldSpec(V12_BASE_BOUNDS),
                seed,
                GenerationRevision.V12,
                RngRevision.V1,
                intent));
        ElevationField mountains = V13MountainTerrainGenerator.standard().generate(genesis(seed, intent));

        long maximumFinalStep = maximumInteriorAffectedFinalStep(base, mountains);
        long limit = ElevationField.SUBUNITS_PER_CELL / 2L;
        assertTrue(
                maximumFinalStep < limit,
                "ordinary mountain terrain should expose broad voxel-transition bands to generic Shape fitting; "
                        + "maxStep=" + maximumFinalStep + ", limit=" + limit);
    }

    @Test
    void plateauSelectionChangesMountainProfileWithoutChangingSeedOrBaseWorld() {
        WorldGenerationIntent normalIntent = intent(mountains(1_000_000, 650_000, 500_000, 550_000, 600_000, false, 0));
        WorldGenerationIntent plateauIntent = intent(mountains(1_000_000, 650_000, 500_000, 550_000, 600_000, true, 1_000_000));

        ElevationField normal = V13MountainTerrainGenerator.standard().generate(genesis(5_111L, normalIntent));
        ElevationField plateau = V13MountainTerrainGenerator.standard().generate(genesis(5_111L, plateauIntent));

        assertNotEquals(surfaceHash(normal), surfaceHash(plateau));
    }

    @Test
    void sameGenesisReproducesTheSameMountainSurface() {
        WorldGenesis genesis = genesis(8_031L, intent(MountainIntent.balanced()));
        ElevationField first = V13MountainTerrainGenerator.standard().generate(genesis);
        ElevationField second = V13MountainTerrainGenerator.standard().generate(genesis);

        assertEquals(surfaceHash(first), surfaceHash(second));
    }

    private static WorldGenesis genesis(long seed, WorldGenerationIntent intent) {
        return new WorldGenesis(
                new WorldSpec(V13_BOUNDS),
                seed,
                GenerationRevision.V13,
                RngRevision.V1,
                intent);
    }

    private static WorldGenerationIntent intent(MountainIntent mountains) {
        return new WorldGenerationIntent(
                normalized(650_000),
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

    private static long maximumLandHeight(ElevationField field) {
        long maximum = Long.MIN_VALUE;
        WorldBounds bounds = field.bounds();
        for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
            for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                maximum = Math.max(maximum, field.elevationSubunitsAt(x, y));
            }
        }
        return maximum;
    }

    private static long maximumInteriorAffectedFinalStep(ElevationField base, ElevationField mountains) {
        long maximum = 0L;
        WorldBounds bounds = mountains.bounds();
        for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
            for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                if (isCoastalLand(base, x, y)) continue;
                long baseHere = base.elevationSubunitsAt(x, y);
                if (baseHere <= 0L) continue;
                long here = mountains.elevationSubunitsAt(x, y);
                boolean affectedHere = here != baseHere;

                if (x < bounds.maxX()
                        && !isCoastalLand(base, x + 1, y)
                        && base.elevationSubunitsAt(x + 1, y) > 0L) {
                    long right = mountains.elevationSubunitsAt(x + 1, y);
                    boolean affectedRight = right != base.elevationSubunitsAt(x + 1, y);
                    if (affectedHere || affectedRight) {
                        maximum = Math.max(maximum, Math.abs(here - right));
                    }
                }
                if (y < bounds.maxY()
                        && !isCoastalLand(base, x, y + 1)
                        && base.elevationSubunitsAt(x, y + 1) > 0L) {
                    long up = mountains.elevationSubunitsAt(x, y + 1);
                    boolean affectedUp = up != base.elevationSubunitsAt(x, y + 1);
                    if (affectedHere || affectedUp) {
                        maximum = Math.max(maximum, Math.abs(here - up));
                    }
                }
            }
        }
        return maximum;
    }

    private static boolean isCoastalLand(ElevationField base, int x, int y) {
        if (!base.contains(x, y) || base.elevationSubunitsAt(x, y) <= 0L) return false;
        WorldBounds bounds = base.bounds();
        if (x == bounds.minX() || x == bounds.maxX() || y == bounds.minY() || y == bounds.maxY()) {
            return true;
        }
        return base.elevationSubunitsAt(x - 1, y) <= 0L
                || base.elevationSubunitsAt(x + 1, y) <= 0L
                || base.elevationSubunitsAt(x, y - 1) <= 0L
                || base.elevationSubunitsAt(x, y + 1) <= 0L;
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
