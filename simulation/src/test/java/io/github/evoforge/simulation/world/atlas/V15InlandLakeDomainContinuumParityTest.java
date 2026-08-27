package io.github.evoforge.simulation.world.atlas;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
import io.github.evoforge.simulation.world.terrain.field.V15ExactInlandLakeBasePageSource;
import io.github.evoforge.simulation.world.terrain.field.V15InlandLakeDomainPlan;
import org.junit.jupiter.api.Test;

/** Cell-for-cell proof of the accepted V15 lowland-domain and Z=0 shoreline pass. */
final class V15InlandLakeDomainContinuumParityTest {

    @Test
    void exactContinuumDomainMatchesHistoricalBroadInteriorLowlandCellForCell() {
        int size = 41;
        long[] elevation = borderedTerrain(size, 8, 2, 13, 27, 14, 26);
        assertDomainParity(
                size,
                elevation,
                new InlandLakeDomainCalibration(
                        size, size, size * size, (size - 2) * (size - 2), 170,
                        6, 2, 16, 5, 3, 5L * ElevationField.SUBUNITS_PER_CELL),
                permissiveHistoricalRecipe(3));
    }

    @Test
    void exactContinuumDomainRejectsHistoricalOneCellLowlandCorridorCellForCell() {
        int size = 51;
        long[] elevation = borderedTerrain(size, 9, 9, 1, 0, 1, 0);
        long low = 2L * ElevationField.SUBUNITS_PER_CELL;
        for (int y = 16; y <= 28; y++) {
            for (int x = 8; x <= 20; x++) elevation[y * size + x] = low;
            for (int x = 30; x <= 42; x++) elevation[y * size + x] = low;
        }
        for (int x = 21; x <= 29; x++) elevation[22 * size + x] = low;

        assertDomainParity(
                size,
                elevation,
                new InlandLakeDomainCalibration(
                        size, size, size * size, (size - 2) * (size - 2), 220,
                        5, 1, 24, 7, 2, 5L * ElevationField.SUBUNITS_PER_CELL),
                permissiveHistoricalRecipe(2));
    }

    @Test
    void exactContinuumBalancedPolicyMatchesHistoricalInsufficientInradiusRejection() {
        int size = 81;
        long[] elevation = borderedTerrain(size, 8, 2, 33, 47, 33, 47);
        assertDomainParity(
                size,
                elevation,
                new InlandLakeDomainCalibration(
                        size, size, size * size, (size - 2) * (size - 2), 160,
                        8, 3, 200, 20, 2, 5L * ElevationField.SUBUNITS_PER_CELL),
                InlandLakeDomainRecipe.balanced());
    }

    @Test
    void exactContinuumShoreConditioningMatchesHistoricalZ0ContractCellForCell() {
        int size = 41;
        long[] elevation = borderedTerrain(size, 8, 2, 13, 27, 14, 26);
        WorldBounds bounds = bounds(size);
        WorldGenesis genesis = genesis(bounds);
        ElevationField historicalBase = new DenseElevationField(bounds, elevation);
        InlandLakeDomainCalibration historicalCalibration = new InlandLakeDomainCalibration(
                size, size, size * size, (size - 2) * (size - 2), 170,
                6, 2, 16, 5, 3, 5L * ElevationField.SUBUNITS_PER_CELL);
        InlandLakeDomainRecipe historicalRecipe = permissiveHistoricalRecipe(3);
        InlandLakeDomain historicalDomain = InlandLakeDomainAlgorithm.standard().generate(
                genesis, historicalBase, historicalCalibration, historicalRecipe);
        ElevationField historicalConditioned = InlandLakeShoreConditioningAlgorithm.standard()
                .condition(historicalBase, historicalDomain);

        ContinuumWorldDomain continuumDomain = new ContinuumWorldDomain(size, size);
        ArrayPageSource base = new ArrayPageSource(continuumDomain, elevation, size);
        V15InlandLakeDomainPlan lakePlan = V15InlandLakeDomainPlan.prepare(
                continuumDomain,
                base,
                continuumCalibration(historicalCalibration),
                continuumRecipe(historicalRecipe));
        ContinuumScalarPage page = new V15ExactInlandLakeBasePageSource(
                        continuumDomain, base, lakePlan)
                .materialize(new ContinuumSampleWindow(0L, 0L, size, size, 1L));

        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                assertEquals(
                        historicalConditioned.elevationSubunitsAt(x, y),
                        Math.round(page.sample(x, y)),
                        "V15 shoreline parity failed at x=" + x + " y=" + y);
            }
        }
    }

    private static void assertDomainParity(
            int size,
            long[] elevation,
            InlandLakeDomainCalibration historicalCalibration,
            InlandLakeDomainRecipe historicalRecipe) {
        WorldBounds bounds = bounds(size);
        ElevationField historicalBase = new DenseElevationField(bounds, elevation);
        InlandLakeDomain historical = InlandLakeDomainAlgorithm.standard().generate(
                genesis(bounds), historicalBase, historicalCalibration, historicalRecipe);

        ContinuumWorldDomain domain = new ContinuumWorldDomain(size, size);
        V15InlandLakeDomainPlan continuum = V15InlandLakeDomainPlan.prepare(
                domain,
                new ArrayPageSource(domain, elevation, size),
                continuumCalibration(historicalCalibration),
                continuumRecipe(historicalRecipe));

        assertEquals(historical.lakeCellCount(), continuum.lakeCellCount());
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                assertEquals(
                        historical.isLakeAt(x, y),
                        continuum.isLake(x, y),
                        "V15 lake-domain parity failed at x=" + x + " y=" + y);
            }
        }
    }

    private static io.github.evoforge.simulation.world.terrain.genesis.V15InlandLakeDomainCalibration
            continuumCalibration(InlandLakeDomainCalibration calibration) {
        return new io.github.evoforge.simulation.world.terrain.genesis.V15InlandLakeDomainCalibration(
                calibration.width(),
                calibration.height(),
                calibration.area(),
                calibration.dryLandCells(),
                calibration.targetLakeCells(),
                calibration.minimumInteriorClearanceCells(),
                calibration.smoothingRadiusCells(),
                calibration.minimumComponentCells(),
                calibration.minimumComponentSpanCells(),
                calibration.maximumLakeBodies(),
                calibration.maximumSourceElevationSubunits());
    }

    private static io.github.evoforge.simulation.world.terrain.genesis.V15InlandLakeDomainRecipe
            continuumRecipe(InlandLakeDomainRecipe recipe) {
        return new io.github.evoforge.simulation.world.terrain.genesis.V15InlandLakeDomainRecipe(
                recipe.targetDryLandCoveragePpm(),
                recipe.maximumInteriorOccupancyPpm(),
                recipe.maximumSourceElevationPpm(),
                recipe.minimumInteriorClearanceCells(),
                recipe.interiorClearanceWorldDivisor(),
                recipe.minimumSmoothingRadiusCells(),
                recipe.smoothingWorldDivisor(),
                recipe.maximumSmoothingRadiusCells(),
                recipe.minimumComponentSpanCells(),
                recipe.componentSpanWorldDivisor(),
                recipe.maximumLakeBodies());
    }

    private static InlandLakeDomainRecipe permissiveHistoricalRecipe(int bodies) {
        return new InlandLakeDomainRecipe(
                100_000,
                900_000,
                500_000,
                6,
                50,
                2,
                120,
                18,
                5,
                120,
                bodies);
    }

    private static long[] borderedTerrain(
            int size,
            int highCells,
            int lowCells,
            int minLowX,
            int maxLowX,
            int minLowY,
            int maxLowY) {
        long[] elevation = new long[size * size];
        long high = (long) highCells * ElevationField.SUBUNITS_PER_CELL;
        long low = (long) lowCells * ElevationField.SUBUNITS_PER_CELL;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int cell = y * size + x;
                if (x == 0 || y == 0 || x == size - 1 || y == size - 1) {
                    elevation[cell] = -ElevationField.SUBUNITS_PER_CELL;
                } else if (x >= minLowX && x <= maxLowX && y >= minLowY && y <= maxLowY) {
                    elevation[cell] = low;
                } else {
                    elevation[cell] = high;
                }
            }
        }
        return elevation;
    }

    private static WorldBounds bounds(int size) {
        return new WorldBounds(0, size - 1, 0, size - 1, -16, 16);
    }

    private static WorldGenesis genesis(WorldBounds bounds) {
        return new WorldGenesis(
                new WorldSpec(bounds),
                71_337L,
                GenerationRevision.V15,
                RngRevision.V1,
                WorldGenerationIntent.balanced());
    }

    private static final class ArrayPageSource implements ContinuumScalarPageSource {
        private final ContinuumWorldDomain domain;
        private final long[] values;
        private final int width;

        private ArrayPageSource(ContinuumWorldDomain domain, long[] values, int width) {
            this.domain = domain;
            this.values = values;
            this.width = width;
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
                    samples[cursor++] = values[y * width + x];
                }
            }
            return new ContinuumScalarPage(window, samples);
        }
    }
}
