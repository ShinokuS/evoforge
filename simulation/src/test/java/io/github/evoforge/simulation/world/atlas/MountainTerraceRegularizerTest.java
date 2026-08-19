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
    void correctionOnlyRaisesExistingMountainCellsAndNeverMovesTheSummit() {
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

        assertEquals(generated.elevationSubunitsAt(0, 0), corrected.elevationSubunitsAt(0, 0));
        assertEquals(generated.elevationSubunitsAt(6, 0), corrected.elevationSubunitsAt(6, 0));
        assertEquals(maximum(generated), maximum(corrected), "terrace correction must not shave or raise the summit");
        for (int x = 0; x <= 6; x++) {
            assertTrue(
                    corrected.elevationSubunitsAt(x, 0) >= generated.elevationSubunitsAt(x, 0),
                    "correction may only fill compressed lower slope cells");
        }
        for (int x = 1; x < 5; x++) {
            assertTrue(
                    Math.abs(corrected.elevationSubunitsAt(x + 1, 0)
                                    - corrected.elevationSubunitsAt(x, 0))
                            <= MountainTerraceRegularizer.MAXIMUM_COMPOSED_CARDINAL_RISE,
                    "adjacent cells already inside the mountain footprint must not retain a one-cell-scale rise");
        }
    }

    @Test
    void representativeV13KeepsOriginalMountainMorphologyExceptCompressedSlopeCells() {
        WorldGenerationIntent intent = intent(new MountainIntent(
                normalized(350_000),
                normalized(520_000),
                normalized(500_000),
                normalized(550_000),
                normalized(600_000),
                false,
                normalized(180_000)));
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
        long mountainCells = 0L;
        long rawMaximumInteriorStep = 0L;
        long maximumInteriorStep = 0L;
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

                if (!mountain) {
                    assertEquals(rawHeight, correctedHeight, "correction must not grow the mountain footprint");
                } else {
                    assertTrue(correctedHeight >= rawHeight, "correction may not reshape the mountain by lowering it");
                }
                if (correctedHeight != rawHeight) changed++;

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
                        rawMaximumInteriorStep = Math.max(rawMaximumInteriorStep, rawStep);
                        maximumInteriorStep = Math.max(maximumInteriorStep, correctedStep);
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
                        rawMaximumInteriorStep = Math.max(rawMaximumInteriorStep, rawStep);
                        maximumInteriorStep = Math.max(maximumInteriorStep, correctedStep);
                        if (rawStep > MountainTerraceRegularizer.MAXIMUM_COMPOSED_CARDINAL_RISE) rawCompressedEdges++;
                        if (correctedStep > MountainTerraceRegularizer.MAXIMUM_COMPOSED_CARDINAL_RISE) compressedEdges++;
                    }
                }
            }
        }

        long rawOneCellBands = countOneCellMountainBands(base, raw, raw);
        long correctedOneCellBands = countOneCellMountainBands(base, corrected, raw);

        assertTrue(mountainCells > 0L, "representative world must contain dedicated mountain terrain");
        assertTrue(changed > 0L, "representative world must exercise the narrow-level correction");
        assertTrue(changed < mountainCells, "correction must remain selective rather than replacing mountain morphology");
        assertTrue(
                maximumInteriorStep <= MountainTerraceRegularizer.MAXIMUM_COMPOSED_CARDINAL_RISE,
                "residual compressed mountain slope: rawMax=" + rawMaximumInteriorStep
                        + ", correctedMax=" + maximumInteriorStep
                        + ", rawCompressedEdges=" + rawCompressedEdges
                        + ", correctedCompressedEdges=" + compressedEdges
                        + ", rawOneCellBands=" + rawOneCellBands
                        + ", correctedOneCellBands=" + correctedOneCellBands
                        + ", changedCells=" + changed
                        + ", mountainCells=" + mountainCells);
        assertTrue(
                maximumUpliftStep <= calibration.maximumCardinalRiseSubunits() + 1L,
                "terrace correction must preserve the accepted mountain-uplift slope budget; max="
                        + maximumUpliftStep + ", budget=" + calibration.maximumCardinalRiseSubunits());
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
