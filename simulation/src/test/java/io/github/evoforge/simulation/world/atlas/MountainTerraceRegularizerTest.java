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

        ElevationField corrected = MountainTerraceRegularizer.widenNarrowLevels(base, generated);

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
        ElevationField corrected = MountainTerraceRegularizer.widenNarrowLevels(base, raw);

        assertEquals(maximum(raw), maximum(corrected), "accepted mountain summit morphology must remain unchanged");

        long changed = 0L;
        long mountainCells = 0L;
        long maximumInteriorStep = 0L;
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

                if (mountain && x < V13_BOUNDS.maxX()
                        && raw.elevationSubunitsAt(x + 1, y) > base.elevationSubunitsAt(x + 1, y)) {
                    maximumInteriorStep = Math.max(
                            maximumInteriorStep,
                            Math.abs(corrected.elevationSubunitsAt(x + 1, y) - correctedHeight));
                }
                if (mountain && y < V13_BOUNDS.maxY()
                        && raw.elevationSubunitsAt(x, y + 1) > base.elevationSubunitsAt(x, y + 1)) {
                    maximumInteriorStep = Math.max(
                            maximumInteriorStep,
                            Math.abs(corrected.elevationSubunitsAt(x, y + 1) - correctedHeight));
                }
            }
        }

        assertTrue(mountainCells > 0L, "representative world must contain dedicated mountain terrain");
        assertTrue(changed > 0L, "representative world must exercise the narrow-level correction");
        assertTrue(changed < mountainCells, "correction must remain selective rather than replacing mountain morphology");
        assertTrue(
                maximumInteriorStep <= MountainTerraceRegularizer.MAXIMUM_COMPOSED_CARDINAL_RISE,
                "the final composed mountain interior must reserve more than two horizontal cells per Z level");
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
