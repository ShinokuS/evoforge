package io.github.evoforge.simulation.world.atlas;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

final class MountainTerraceRegularizerTest {
    private static final long CELL = ElevationField.SUBUNITS_PER_CELL;
    private static final WorldBounds V13_BOUNDS = new WorldBounds(-64, 63, -64, 63, -12, 96);
    private static final WorldBounds V12_BASE_BOUNDS = new WorldBounds(-64, 63, -64, 63, -12, 12);

    @Test
    void correctionOnlyRaisesLandAndNeverMovesTheSummit() {
        WorldBounds bounds = new WorldBounds(0, 6, 0, 0, -2, 8);
        ElevationField base = new DenseElevationField(bounds, new long[] {
                2 * CELL, 2 * CELL, 2 * CELL, 2 * CELL, 2 * CELL, 2 * CELL, 2 * CELL
        });
        ElevationField generated = new DenseElevationField(bounds, new long[] {
                2 * CELL,
                2 * CELL + CELL / 10,
                2 * CELL + CELL * 7 / 10,
                3 * CELL + CELL * 3 / 10,
                3 * CELL + CELL * 9 / 10,
                2 * CELL + CELL * 4 / 10,
                2 * CELL
        });

        ElevationField corrected = MountainTerraceRegularizer.widenNarrowLevels(
                base,
                generated,
                2 * CELL);

        assertEquals(maximum(generated), maximum(corrected), "terrace correction must not shave or raise the summit");
        for (int x = 0; x <= 6; x++) {
            assertTrue(
                    corrected.elevationSubunitsAt(x, 0) >= generated.elevationSubunitsAt(x, 0),
                    "correction may only raise compressed lower slope cells");
        }
        for (int x = 1; x < 5; x++) {
            assertTrue(
                    Math.abs(corrected.elevationSubunitsAt(x + 1, 0)
                                    - corrected.elevationSubunitsAt(x, 0))
                            <= MountainTerraceRegularizer.MAXIMUM_COMPOSED_CARDINAL_RISE,
                    "adjacent mountain cells must not retain a one-cell-scale rise when widening is feasible");
        }
    }

    @Test
    void representativeV13RemovesNarrowLevelsWithoutReplacingAcceptedMountainMorphology() {
        WorldGenerationIntent intent = screenshotIntent();
        long seed = 1L;
        WorldGenesis genesis = new WorldGenesis(
                new WorldSpec(V13_BOUNDS),
                seed,
                GenerationRevision.V13,
                RngRevision.V1,
                intent);
        MountainRecipe recipe = MountainRecipe.balanced();
        MountainCalibration calibration = MountainCalibrator.standard().calibrate(genesis, recipe);
        ElevationField base = V12BaseTerrainGenerator.standard().generate(new WorldGenesis(
                new WorldSpec(V12_BASE_BOUNDS),
                seed,
                GenerationRevision.V12,
                RngRevision.V1,
                intent));
        ElevationField raw = new MountainMorphologyAlgorithm().generate(genesis, base, calibration, recipe);
        ElevationField corrected = MountainTerraceRegularizer.widenNarrowLevels(
                base,
                raw,
                calibration.maximumCardinalRiseSubunits());

        assertEquals(maximum(raw), maximum(corrected), "accepted mountain summit morphology must remain unchanged");

        long changed = 0L;
        long apronChangedCells = 0L;
        long mountainCells = 0L;
        long finalMountainCells = 0L;
        long rawCompressedEdges = 0L;
        long compressedEdges = 0L;
        long maximumUpliftStep = 0L;
        for (int y = V13_BOUNDS.minY(); y <= V13_BOUNDS.maxY(); y++) {
            for (int x = V13_BOUNDS.minX(); x <= V13_BOUNDS.maxX(); x++) {
                long baseHeight = base.elevationSubunitsAt(x, y);
                long rawHeight = raw.elevationSubunitsAt(x, y);
                long correctedHeight = corrected.elevationSubunitsAt(x, y);
                boolean mountain = rawHeight > baseHeight;
                if (mountain) mountainCells++;
                if (correctedHeight > baseHeight) finalMountainCells++;

                assertTrue(correctedHeight >= rawHeight, "terrace correction may never lower accepted terrain");
                if (correctedHeight != rawHeight) {
                    changed++;
                    if (!mountain) {
                        apronChangedCells++;
                        assertTrue(baseHeight > 0L, "bounded widening apron may use dry land only");
                    }
                }

                if (baseHeight > 0L && x < V13_BOUNDS.maxX()
                        && base.elevationSubunitsAt(x + 1, y) > 0L) {
                    long rightBase = base.elevationSubunitsAt(x + 1, y);
                    long rightRaw = raw.elevationSubunitsAt(x + 1, y);
                    long rightCorrected = corrected.elevationSubunitsAt(x + 1, y);
                    maximumUpliftStep = Math.max(
                            maximumUpliftStep,
                            Math.abs((correctedHeight - baseHeight) - (rightCorrected - rightBase)));
                    if (mountain && rightRaw > rightBase) {
                        long rawStep = Math.abs(rightRaw - rawHeight);
                        long correctedStep = Math.abs(rightCorrected - correctedHeight);
                        if (rawStep > MountainTerraceRegularizer.MAXIMUM_COMPOSED_CARDINAL_RISE) rawCompressedEdges++;
                        if (correctedStep > MountainTerraceRegularizer.MAXIMUM_COMPOSED_CARDINAL_RISE) compressedEdges++;
                    }
                }
                if (baseHeight > 0L && y < V13_BOUNDS.maxY()
                        && base.elevationSubunitsAt(x, y + 1) > 0L) {
                    long upperBase = base.elevationSubunitsAt(x, y + 1);
                    long upperRaw = raw.elevationSubunitsAt(x, y + 1);
                    long upperCorrected = corrected.elevationSubunitsAt(x, y + 1);
                    maximumUpliftStep = Math.max(
                            maximumUpliftStep,
                            Math.abs((correctedHeight - baseHeight) - (upperCorrected - upperBase)));
                    if (mountain && upperRaw > upperBase) {
                        long rawStep = Math.abs(upperRaw - rawHeight);
                        long correctedStep = Math.abs(upperCorrected - correctedHeight);
                        if (rawStep > MountainTerraceRegularizer.MAXIMUM_COMPOSED_CARDINAL_RISE) rawCompressedEdges++;
                        if (correctedStep > MountainTerraceRegularizer.MAXIMUM_COMPOSED_CARDINAL_RISE) compressedEdges++;
                    }
                }
            }
        }

        long rawOneCellBands = countOneCellMountainBands(base, raw, raw);
        long correctedOneCellBands = countOneCellMountainBands(base, corrected, raw);
        String diagnostics = "rawCompressedEdges=" + rawCompressedEdges
                + ", correctedCompressedEdges=" + compressedEdges
                + ", rawOneCellBands=" + rawOneCellBands
                + ", correctedOneCellBands=" + correctedOneCellBands
                + ", changedCells=" + changed
                + ", apronChangedCells=" + apronChangedCells
                + ", rawMountainCells=" + mountainCells
                + ", finalMountainCells=" + finalMountainCells;

        assertTrue(mountainCells > 0L, "representative world must contain dedicated mountain terrain");
        assertTrue(rawOneCellBands > 0L, "fixture must reproduce the original narrow-level defect");
        assertTrue(changed > 0L && changed < mountainCells,
                "correction must remain selective rather than replace the macro mountain; " + diagnostics);
        assertTrue(correctedOneCellBands * 5L <= rawOneCellBands,
                "literal one-cell Z bands must fall by at least 80%; " + diagnostics);
        assertTrue(compressedEdges * 10L <= rawCompressedEdges,
                "strongly compressed interior slope edges must fall by at least 90%; " + diagnostics);
        assertTrue(apronChangedCells * 20L <= mountainCells,
                "bounded land apron must stay below 5% of the accepted mountain area; " + diagnostics);
        assertEquals(mountainCells + apronChangedCells, finalMountainCells,
                "the regularizer may only add the explicitly measured dry-land apron");
        assertTrue(
                maximumUpliftStep <= calibration.maximumCardinalRiseSubunits() + 1L,
                "terrace correction must preserve the accepted mountain-uplift slope budget; max="
                        + maximumUpliftStep + ", budget=" + calibration.maximumCardinalRiseSubunits());
    }

    @Test
    void screenshotScaleWorldsAlsoRemoveMostLiteralOneCellMountainBands() {
        assertScreenshotScale(new WorldBounds(-50, 49, -50, 49, -12, 96), "100x100");
        assertScreenshotScale(new WorldBounds(-250, 249, -250, 249, -12, 96), "500x500");
    }

    private static void assertScreenshotScale(WorldBounds bounds, String label) {
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
        ElevationField raw = new MountainMorphologyAlgorithm().generate(genesis, base, calibration, recipe);
        ElevationField corrected = MountainTerraceRegularizer.widenNarrowLevels(
                base,
                raw,
                calibration.maximumCardinalRiseSubunits());

        long rawBands = countOneCellMountainBands(base, raw, raw);
        long correctedBands = countOneCellMountainBands(base, corrected, raw);
        assertTrue(rawBands > 0L, label + " fixture must reproduce literal one-cell mountain bands");
        assertTrue(correctedBands * 4L <= rawBands,
                label + " must remove at least 75% of literal one-cell mountain bands; raw="
                        + rawBands + ", corrected=" + correctedBands);
        assertEquals(maximum(raw), maximum(corrected), label + " mountain summit must remain unchanged");
    }

    private static long countOneCellMountainBands(
            ElevationField base,
            ElevationField surface,
            ElevationField rawMountainFootprint) {
        WorldBounds bounds = surface.bounds();
        long count = 0L;
        for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
            for (int x = bounds.minX() + 1; x < bounds.maxX(); x++) {
                if (isMountain(base, rawMountainFootprint, x - 1, y)
                        && isMountain(base, rawMountainFootprint, x, y)
                        && isMountain(base, rawMountainFootprint, x + 1, y)
                        && isStrictIntermediateLevel(
                                surface.elevationSubunitsAt(x - 1, y),
                                surface.elevationSubunitsAt(x, y),
                                surface.elevationSubunitsAt(x + 1, y))) {
                    count++;
                }
            }
        }
        for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
            for (int y = bounds.minY() + 1; y < bounds.maxY(); y++) {
                if (isMountain(base, rawMountainFootprint, x, y - 1)
                        && isMountain(base, rawMountainFootprint, x, y)
                        && isMountain(base, rawMountainFootprint, x, y + 1)
                        && isStrictIntermediateLevel(
                                surface.elevationSubunitsAt(x, y - 1),
                                surface.elevationSubunitsAt(x, y),
                                surface.elevationSubunitsAt(x, y + 1))) {
                    count++;
                }
            }
        }
        return count;
    }

    private static boolean isMountain(
            ElevationField base,
            ElevationField rawMountainFootprint,
            int x,
            int y) {
        return rawMountainFootprint.elevationSubunitsAt(x, y) > base.elevationSubunitsAt(x, y);
    }

    private static boolean isStrictIntermediateLevel(long firstHeight, long middleHeight, long lastHeight) {
        long first = Math.floorDiv(firstHeight, CELL);
        long middle = Math.floorDiv(middleHeight, CELL);
        long last = Math.floorDiv(lastHeight, CELL);
        return (first < middle && middle < last) || (first > middle && middle > last);
    }

    private static WorldGenerationIntent screenshotIntent() {
        return intent(new MountainIntent(
                normalized(350_000),
                normalized(520_000),
                normalized(500_000),
                normalized(550_000),
                normalized(600_000),
                false,
                normalized(180_000)));
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

    private static NormalizedValue normalized(int ppm) {
        return NormalizedValue.ofPartsPerMillion(ppm);
    }

    private static long maximum(ElevationField field) {
        long maximum = Long.MIN_VALUE;
        WorldBounds bounds = field.bounds();
        for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
            for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                maximum = Math.max(maximum, field.elevationSubunitsAt(x, y));
            }
        }
        return maximum;
    }
}
