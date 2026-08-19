package io.github.evoforge.simulation.world.atlas;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.evoforge.simulation.definition.NormalizedValue;
import io.github.evoforge.simulation.world.genesis.GenerationRevision;
import io.github.evoforge.simulation.world.genesis.MountainIntent;
import io.github.evoforge.simulation.world.genesis.RngRevision;
import io.github.evoforge.simulation.world.genesis.WorldGenerationIntent;
import io.github.evoforge.simulation.world.genesis.WorldGenesis;
import io.github.evoforge.simulation.world.genesis.WorldSpec;
import io.github.evoforge.simulation.world.spatial.WorldBounds;
import io.github.evoforge.simulation.world.terrain.shape.TerrainShapeField;
import io.github.evoforge.simulation.world.terrain.shape.TerrainShapeGenerationStage;
import org.junit.jupiter.api.Test;

/** Visual-review fixture expressed as source-generation invariants rather than a repair pass. */
final class V13MountainSourceGeometryAcceptanceTest {
    private static final long CELL = ElevationField.SUBUNITS_PER_CELL;
    private static final int PATCH_SIZE = 50;

    @Test
    void reviewedPeakFixtureIsBornBroadAndKeepsRampsSparseAcrossEligibleSurface() {
        WorldBounds bounds = new WorldBounds(-200, 199, -200, 199, -12, 96);
        WorldGenerationIntent intent = screenshotIntent();
        long seed = 1L;
        WorldGenesis genesis = new WorldGenesis(
                new WorldSpec(bounds),
                seed,
                GenerationRevision.V13,
                RngRevision.V1,
                intent);
        MountainRecipe recipe = MountainRecipe.balanced();
        MountainCalibration calibration = MountainCalibrator.standard().calibrate(genesis, recipe);
        WorldBounds baseBounds = new WorldBounds(
                bounds.minX(), bounds.maxX(), bounds.minY(), bounds.maxY(), bounds.minZ(), 12);
        ElevationField base = V12BaseTerrainGenerator.standard().generate(new WorldGenesis(
                new WorldSpec(baseBounds),
                seed,
                GenerationRevision.V12,
                RngRevision.V1,
                intent));
        ElevationField mountains = V13MountainTerrainGenerator.standard().generate(genesis);
        TerrainShapeField coherentShapes = TerrainShapeGenerationStage
                .forRevision(GenerationRevision.V12)
                .generate(mountains);
        TerrainShapeField sparseShapes = TerrainShapeGenerationStage
                .forRevision(GenerationRevision.V13)
                .generate(mountains);

        int patchColumns = Math.toIntExact(((long) bounds.maxX() - bounds.minX() + 1L) / PATCH_SIZE);
        int patchRows = Math.toIntExact(((long) bounds.maxY() - bounds.minY() + 1L) / PATCH_SIZE);
        long[] patchEligible = new long[patchColumns * patchRows];
        long[] patchSparse = new long[patchEligible.length];
        long mountainCells = 0L;
        long coherentMountainOverrides = 0L;
        long sparseMountainOverrides = 0L;
        long maximumCardinalMountainStep = 0L;
        long maximumMountainHeight = Long.MIN_VALUE;

        for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
            for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                long here = mountains.elevationSubunitsAt(x, y);
                long baseHere = base.elevationSubunitsAt(x, y);
                if (here <= baseHere) continue;

                mountainCells++;
                maximumMountainHeight = Math.max(maximumMountainHeight, here);
                int patchX = (x - bounds.minX()) / PATCH_SIZE;
                int patchY = (y - bounds.minY()) / PATCH_SIZE;
                int patch = patchY * patchColumns + patchX;
                if (coherentShapes.shapeOverrideAt(x, y) != null) {
                    coherentMountainOverrides++;
                    patchEligible[patch]++;
                }
                if (sparseShapes.shapeOverrideAt(x, y) != null) {
                    sparseMountainOverrides++;
                    patchSparse[patch]++;
                }

                if (x < bounds.maxX()
                        && mountains.elevationSubunitsAt(x + 1, y) > base.elevationSubunitsAt(x + 1, y)) {
                    maximumCardinalMountainStep = Math.max(
                            maximumCardinalMountainStep,
                            Math.abs(here - mountains.elevationSubunitsAt(x + 1, y)));
                }
                if (y < bounds.maxY()
                        && mountains.elevationSubunitsAt(x, y + 1) > base.elevationSubunitsAt(x, y + 1)) {
                    maximumCardinalMountainStep = Math.max(
                            maximumCardinalMountainStep,
                            Math.abs(here - mountains.elevationSubunitsAt(x, y + 1)));
                }
            }
        }

        String diagnostics = "mountainCells=" + mountainCells
                + ", coherentMountainOverrides=" + coherentMountainOverrides
                + ", sparseMountainOverrides=" + sparseMountainOverrides
                + ", maximumMountainHeight=" + maximumMountainHeight
                + ", maximumCardinalMountainStep=" + maximumCardinalMountainStep
                + ", calibratedRise=" + calibration.maximumCardinalRiseSubunits();

        assertTrue(mountainCells > 1_000L, "fixture must contain a substantial mountain surface; " + diagnostics);
        assertTrue(
                maximumMountainHeight > 12L * CELL,
                "reviewed 400x400 fixture must actually use V13 mountain headroom; " + diagnostics);
        assertTrue(
                calibration.maximumCardinalRiseSubunits() <= 235_000L,
                "source mountain law must reserve more than four cardinal cells per vertical level");
        assertTrue(
                maximumCardinalMountainStep <= calibration.maximumCardinalRiseSubunits() + 2L,
                "generated mountain surface itself must obey the source width law; " + diagnostics);
        assertTrue(coherentMountainOverrides > 0L, "fixture must contain ramp-eligible mountain surface; " + diagnostics);
        assertTrue(sparseMountainOverrides > 0L, "ramps must remain present on the generated mountain; " + diagnostics);
        assertTrue(
                sparseMountainOverrides * 10L <= mountainCells,
                "ramps must remain a small minority of the mountain surface; " + diagnostics);
        assertTrue(
                sparseMountainOverrides * 3L <= coherentMountainOverrides,
                "V13 must discard most otherwise coherent ramp sites; " + diagnostics);
        assertTrue(
                sparseMountainOverrides * 10L >= coherentMountainOverrides,
                "V13 must not make ramps so sparse that coherent mountain faces lose them; " + diagnostics);

        int eligiblePatches = 0;
        for (int patch = 0; patch < patchEligible.length; patch++) {
            if (patchEligible[patch] < 10L) continue;
            eligiblePatches++;
            assertTrue(
                    patchSparse[patch] > 0L,
                    "every spatial patch with meaningful coherent slope must retain ramps; patch="
                            + patch + "; " + diagnostics);
            assertTrue(
                    patchSparse[patch] * 2L <= patchEligible[patch],
                    "no coherent patch may remain ramp-dominated; patch=" + patch + "; " + diagnostics);
        }
        assertTrue(
                eligiblePatches >= 3,
                "review fixture must exercise ramp distribution across several eligible surface patches; " + diagnostics);
    }

    private static WorldGenerationIntent screenshotIntent() {
        return new WorldGenerationIntent(
                normalized(350_000),
                normalized(750_000),
                normalized(250_000),
                normalized(600_000),
                normalized(450_000),
                normalized(500_000),
                normalized(350_000),
                new MountainIntent(
                        normalized(350_000),
                        normalized(520_000),
                        normalized(500_000),
                        normalized(550_000),
                        normalized(1_000_000),
                        false,
                        normalized(180_000)));
    }

    private static NormalizedValue normalized(int ppm) {
        return NormalizedValue.ofPartsPerMillion(ppm);
    }
}
