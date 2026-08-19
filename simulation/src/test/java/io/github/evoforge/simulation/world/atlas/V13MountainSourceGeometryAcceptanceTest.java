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

    @Test
    void reviewedPeakFixtureIsBornBroadAndKeepsRampsSparseAcrossTheMountain() {
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
        TerrainShapeField shapes = TerrainShapeGenerationStage
                .forRevision(GenerationRevision.V13)
                .generate(mountains);

        long mountainCells = 0L;
        long mountainOverrides = 0L;
        long maximumCardinalMountainStep = 0L;
        long[] quadrantMountainCells = new long[4];
        long[] quadrantOverrides = new long[4];

        for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
            for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                long here = mountains.elevationSubunitsAt(x, y);
                long baseHere = base.elevationSubunitsAt(x, y);
                if (here <= baseHere) continue;

                mountainCells++;
                int quadrant = (x >= 0 ? 1 : 0) + (y >= 0 ? 2 : 0);
                quadrantMountainCells[quadrant]++;
                if (shapes.shapeOverrideAt(x, y) != null) {
                    mountainOverrides++;
                    quadrantOverrides[quadrant]++;
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
                + ", mountainOverrides=" + mountainOverrides
                + ", maximumCardinalMountainStep=" + maximumCardinalMountainStep
                + ", calibratedRise=" + calibration.maximumCardinalRiseSubunits();

        assertTrue(mountainCells > 1_000L, "fixture must contain a substantial mountain surface; " + diagnostics);
        assertTrue(
                calibration.maximumCardinalRiseSubunits() <= 235_000L,
                "source mountain law must reserve more than four cardinal cells per vertical level");
        assertTrue(
                maximumCardinalMountainStep <= calibration.maximumCardinalRiseSubunits() + 2L,
                "generated mountain surface itself must obey the source width law; " + diagnostics);
        assertTrue(mountainOverrides > 0L, "ramps must remain present on the generated mountain; " + diagnostics);
        assertTrue(
                mountainOverrides * 10L <= mountainCells,
                "ramps must remain a small minority of the mountain surface; " + diagnostics);

        int substantialQuadrants = 0;
        for (int quadrant = 0; quadrant < quadrantMountainCells.length; quadrant++) {
            long cells = quadrantMountainCells[quadrant];
            if (cells < 500L) continue;
            substantialQuadrants++;
            assertTrue(
                    quadrantOverrides[quadrant] > 0L,
                    "every substantial mountain region must retain some ramps; quadrant=" + quadrant + "; " + diagnostics);
            assertTrue(
                    quadrantOverrides[quadrant] * 5L <= cells,
                    "no substantial mountain region may become ramp-dominated; quadrant=" + quadrant + "; " + diagnostics);
        }
        assertTrue(
                substantialQuadrants >= 3,
                "review fixture must exercise ramp distribution across several mountain regions; " + diagnostics);
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
