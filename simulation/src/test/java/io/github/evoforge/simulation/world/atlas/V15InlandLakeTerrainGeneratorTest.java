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
import org.junit.jupiter.api.Test;

final class V15InlandLakeTerrainGeneratorTest {

    @Test
    void representativeV15AddsMeaningfulDeepInteriorStandingWaterWithoutRewritingWorldEdge() {
        WorldGenesis genesis = genesis(300, 4_859_186_304_997_574_751L, GenerationRevision.V15, 830_000);
        ElevationField v14 = V14BathymetryTerrainGenerator.standard().generate(genesis);
        ElevationField v15 = V15InlandLakeTerrainGenerator.standard().generate(genesis);

        int landToWater = 0;
        long deepestNewWater = 0L;
        WorldBounds bounds = v15.bounds();
        for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
            for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                long before = v14.elevationSubunitsAt(x, y);
                long after = v15.elevationSubunitsAt(x, y);
                if (before >= 0L && after < 0L) {
                    landToWater++;
                    deepestNewWater = Math.min(deepestNewWater, after);
                    assertTrue(x > bounds.minX() && x < bounds.maxX()
                                    && y > bounds.minY() && y < bounds.maxY(),
                            "inland lake must not be a boundary rewrite");
                }
            }
        }
        assertTrue(landToWater >= 100,
                "representative high-land V15 world should contain a visually meaningful inland-water footprint");
        assertTrue(deepestNewWater <= -5L * ElevationField.SUBUNITS_PER_CELL,
                "a significant inland lake must have a bathymetric core deeper than the old 3-4 cell puddles");

        for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
            assertTrue(v15.elevationSubunitsAt(x, bounds.minY()) < 0L);
            assertTrue(v15.elevationSubunitsAt(x, bounds.maxY()) < 0L);
        }
        for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
            assertTrue(v15.elevationSubunitsAt(bounds.minX(), y) < 0L);
            assertTrue(v15.elevationSubunitsAt(bounds.maxX(), y) < 0L);
        }
    }

    @Test
    void coordinatorReservesRealLakeAreaSoLandContinuesToMeanDryLandWhenSupportFits() {
        WorldBounds bounds = new WorldBounds(0, 9, 0, 9, -4, 4);
        WorldGenesis genesis = genesis(bounds, 91_337L, GenerationRevision.V15, 500_000);
        ElevationGenerator coverageBase = current -> {
            int area = DenseElevationField.cellCount(current.spec().bounds());
            int landCells = Math.toIntExact(
                    ((long) area * current.generationIntent().landCoverage().partsPerMillion()
                                    + NormalizedValue.SCALE / 2L)
                            / NormalizedValue.SCALE);
            long[] elevation = new long[area];
            for (int cell = 0; cell < area; cell++) {
                elevation[cell] = cell < landCells
                        ? ElevationField.SUBUNITS_PER_CELL
                        : -ElevationField.SUBUNITS_PER_CELL;
            }
            return new DenseElevationField(current.spec().bounds(), elevation);
        };
        InlandLakeDomainCalibrator calibrator = (current, base, recipe) ->
                new InlandLakeDomainCalibration(
                        10,
                        10,
                        100,
                        50,
                        5,
                        1,
                        1,
                        1,
                        1,
                        1,
                        4L * ElevationField.SUBUNITS_PER_CELL);
        InlandLakeDomainAlgorithm lakeAlgorithm = (current, base, calibration, recipe) -> {
            boolean[] lake = new boolean[100];
            for (int cell = 40; cell < 45; cell++) lake[cell] = true;
            return new InlandLakeDomain(bounds, lake, 5);
        };
        V15InlandLakeBaseTerrainGenerator generator = new V15InlandLakeBaseTerrainGenerator(
                coverageBase,
                calibrator,
                InlandLakeDomainRecipe.balanced(),
                lakeAlgorithm,
                InlandLakeShoreConditioningAlgorithm.standard(),
                V12LandformRecipe.balanced().coast());

        ElevationField result = generator.generate(genesis);
        int dryLand = 0;
        for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
            for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                if (result.elevationSubunitsAt(x, y) >= 0L) dryLand++;
            }
        }

        assertEquals(50, dryLand,
                "five lake cells must be reserved in the continental budget rather than silently reducing Land");
        for (int cell = 40; cell < 45; cell++) {
            int x = cell % 10;
            int y = cell / 10;
            assertTrue(result.elevationSubunitsAt(x, y) < 0L);
        }
    }

    @Test
    void sameGenesisReplaysZ0LakeTerrainExactly() {
        WorldGenesis genesis = genesis(180, 71_337L, GenerationRevision.V15, 830_000);
        assertFieldsEqual(
                V15InlandLakeTerrainGenerator.standard().generate(genesis),
                V15InlandLakeTerrainGenerator.standard().generate(genesis));
    }

    @Test
    void elevationStageRoutesV15ToLakeDomainPipelineWhileV14RemainsAccepted() {
        WorldGenesis v15 = genesis(160, 991_337L, GenerationRevision.V15, 830_000);
        assertFieldsEqual(
                V15InlandLakeTerrainGenerator.standard().generate(v15),
                new ElevationGenerationStage().generate(v15));

        WorldGenesis v14 = genesis(160, 991_337L, GenerationRevision.V14, 830_000);
        assertFieldsEqual(
                V14BathymetryTerrainGenerator.standard().generate(v14),
                new ElevationGenerationStage().generate(v14));
    }

    private static void assertFieldsEqual(ElevationField expected, ElevationField actual) {
        assertEquals(expected.bounds(), actual.bounds());
        WorldBounds bounds = expected.bounds();
        for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
            for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                assertEquals(
                        expected.elevationSubunitsAt(x, y),
                        actual.elevationSubunitsAt(x, y),
                        "elevation mismatch at " + x + "," + y);
            }
        }
    }

    private static WorldGenesis genesis(
            int size,
            long seed,
            GenerationRevision revision,
            int landCoveragePpm) {
        int min = -size / 2;
        return genesis(
                new WorldBounds(min, min + size - 1, min, min + size - 1, -96, 96),
                seed,
                revision,
                landCoveragePpm);
    }

    private static WorldGenesis genesis(
            WorldBounds bounds,
            long seed,
            GenerationRevision revision,
            int landCoveragePpm) {
        WorldGenerationIntent balanced = WorldGenerationIntent.balanced();
        WorldGenerationIntent intent = new WorldGenerationIntent(
                NormalizedValue.ofPartsPerMillion(landCoveragePpm),
                NormalizedValue.ofPartsPerMillion(750_000),
                NormalizedValue.ofPartsPerMillion(120_000),
                NormalizedValue.ofPartsPerMillion(600_000),
                NormalizedValue.ofPartsPerMillion(450_000),
                balanced.landformScale(),
                balanced.ruggedness(),
                balanced.mountains());
        return new WorldGenesis(
                new WorldSpec(bounds),
                seed,
                revision,
                RngRevision.V1,
                intent);
    }
}
