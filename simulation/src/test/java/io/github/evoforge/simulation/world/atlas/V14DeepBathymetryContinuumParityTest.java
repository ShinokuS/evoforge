package io.github.evoforge.simulation.world.atlas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.evoforge.simulation.world.continuum.field.ContinuumSampleWindow;
import io.github.evoforge.simulation.world.continuum.field.ContinuumScalarPage;
import io.github.evoforge.simulation.world.continuum.field.ContinuumScalarPageSource;
import io.github.evoforge.simulation.world.continuum.model.ContinuumWorldDomain;
import io.github.evoforge.simulation.world.genesis.GenerationRevision;
import io.github.evoforge.simulation.world.genesis.RngRevision;
import io.github.evoforge.simulation.world.genesis.WorldGenerationIntent;
import io.github.evoforge.simulation.world.genesis.WorldGenesis;
import io.github.evoforge.simulation.world.genesis.WorldSpec;
import io.github.evoforge.simulation.world.spatial.WorldBounds;
import io.github.evoforge.simulation.world.terrain.field.V14ExactDeepBathymetryPageSource;
import io.github.evoforge.simulation.world.terrain.genesis.V14BathymetryCalibration;
import io.github.evoforge.simulation.world.terrain.genesis.V14BathymetryRecipe;
import org.junit.jupiter.api.Test;

/** Isolates the historical deep-interior V14 pass on a fixture that is guaranteed to activate it. */
final class V14DeepBathymetryContinuumParityTest {
    private static final int SIDE = 121;
    private static final int MIN_Z_CELLS = -96;
    private static final int MAX_Z_CELLS = 96;

    @Test
    void exactContinuumDeepPassMatchesHistoricalDenseAlgorithmCellForCellAndIsNotNoOp() {
        WorldBounds bounds = new WorldBounds(0, SIDE - 1, 0, SIDE - 1, MIN_Z_CELLS, MAX_Z_CELLS);
        long[] acceptedValues = acceptedSquareBowl(20);
        ElevationField accepted = new DenseElevationField(bounds, acceptedValues);
        WorldGenesis genesis = new WorldGenesis(
                new WorldSpec(bounds),
                41L,
                GenerationRevision.V14,
                RngRevision.V1,
                WorldGenerationIntent.balanced());

        BathymetryRecipe historicalRecipe = BathymetryRecipe.balanced();
        BathymetryCalibration historicalCalibration = BathymetryCalibrator.standard()
                .calibrate(genesis, historicalRecipe);
        ElevationField historical = new DeepBathymetryStructureAlgorithm().generate(
                genesis,
                accepted,
                historicalCalibration,
                historicalRecipe);

        ContinuumWorldDomain domain = new ContinuumWorldDomain(SIDE, SIDE);
        V14BathymetryRecipe continuumRecipe = V14BathymetryRecipe.balanced();
        V14BathymetryCalibration continuumCalibration = V14BathymetryCalibration.compile(
                domain,
                MIN_Z_CELLS,
                continuumRecipe);
        ContinuumScalarPageSource base = new ArrayPageSource(domain, acceptedValues);
        ContinuumScalarPage page = new V14ExactDeepBathymetryPageSource(
                        domain,
                        base,
                        continuumCalibration,
                        continuumRecipe)
                .materialize(new ContinuumSampleWindow(0L, 0L, SIDE, SIDE, 1L));

        int changedCells = 0;
        for (int y = 0; y < SIDE; y++) {
            for (int x = 0; x < SIDE; x++) {
                int cell = y * SIDE + x;
                long expected = historical.elevationSubunitsAt(x, y);
                long actual = Math.round(page.sample(x, y));
                assertEquals(expected, actual, "V14 deep parity failed at x=" + x + " y=" + y);
                if (expected != acceptedValues[cell]) changedCells++;
            }
        }
        assertTrue(changedCells > 0, "deep-interior parity fixture must exercise basin/high composition");
    }

    private static long[] acceptedSquareBowl(int maximumDepthCells) {
        int maximumClearance = SIDE / 2;
        long[] values = new long[SIDE * SIDE];
        int index = 0;
        for (int y = 0; y < SIDE; y++) {
            for (int x = 0; x < SIDE; x++) {
                int clearance = Math.min(Math.min(x, SIDE - 1 - x), Math.min(y, SIDE - 1 - y));
                if (clearance == 0) {
                    values[index++] = ElevationField.SUBUNITS_PER_CELL;
                    continue;
                }
                long depth = ElevationField.SUBUNITS_PER_CELL
                        + (maximumDepthCells - 1L)
                                * ElevationField.SUBUNITS_PER_CELL
                                * clearance
                                / maximumClearance;
                values[index++] = -depth;
            }
        }
        return values;
    }

    private static final class ArrayPageSource implements ContinuumScalarPageSource {
        private final ContinuumWorldDomain domain;
        private final long[] values;

        private ArrayPageSource(ContinuumWorldDomain domain, long[] values) {
            this.domain = domain;
            this.values = values;
        }

        @Override
        public ContinuumWorldDomain domain() {
            return domain;
        }

        @Override
        public ContinuumScalarPage materialize(ContinuumSampleWindow window) {
            double[] samples = new double[Math.multiplyExact(window.width(), window.height())];
            int cursor = 0;
            for (int sampleY = 0; sampleY < window.height(); sampleY++) {
                int y = Math.toIntExact(window.yAt(sampleY));
                for (int sampleX = 0; sampleX < window.width(); sampleX++) {
                    int x = Math.toIntExact(window.xAt(sampleX));
                    samples[cursor++] = values[y * SIDE + x];
                }
            }
            return new ContinuumScalarPage(window, samples);
        }
    }
}
