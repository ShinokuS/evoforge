package io.github.evoforge.simulation.world.atlas;

import io.github.evoforge.simulation.definition.NormalizedValue;
import io.github.evoforge.simulation.world.genesis.WorldGenerationIntent;
import io.github.evoforge.simulation.world.genesis.WorldGenesis;

/**
 * V15 pre-mountain composition: accepted V14 continental base plus independently selected Z=0
 * inland-water domains.
 *
 * <p>The standard path reserves the balanced lake budget before the expensive continental synthesis.
 * When the selected footprint matches that prediction, the already generated base is authoritative
 * and no second V14-base synthesis is needed. Shape validity still has priority over quota: if the
 * actual footprint materially differs, the coordinator falls back to exact post-selection land
 * compensation rather than silently changing the meaning of {@code Land}.</p>
 */
public final class V15InlandLakeBaseTerrainGenerator implements ElevationGenerator {
    private static final int PPM = NormalizedValue.SCALE;

    private final ElevationGenerator continentalBaseGenerator;
    private final InlandLakeDomainCalibrator lakeCalibrator;
    private final InlandLakeDomainRecipe lakeRecipe;
    private final InlandLakeDomainAlgorithm lakeAlgorithm;
    private final InlandLakeShoreConditioningAlgorithm shoreAlgorithm;
    private final V12LandformRecipe.CoastProfile coastProfile;
    private final boolean predictiveLandReservation;

    public V15InlandLakeBaseTerrainGenerator(
            ElevationGenerator continentalBaseGenerator,
            InlandLakeDomainCalibrator lakeCalibrator,
            InlandLakeDomainRecipe lakeRecipe,
            InlandLakeDomainAlgorithm lakeAlgorithm,
            InlandLakeShoreConditioningAlgorithm shoreAlgorithm,
            V12LandformRecipe.CoastProfile coastProfile) {
        this(
                continentalBaseGenerator,
                lakeCalibrator,
                lakeRecipe,
                lakeAlgorithm,
                shoreAlgorithm,
                coastProfile,
                false);
    }

    V15InlandLakeBaseTerrainGenerator(
            ElevationGenerator continentalBaseGenerator,
            InlandLakeDomainCalibrator lakeCalibrator,
            InlandLakeDomainRecipe lakeRecipe,
            InlandLakeDomainAlgorithm lakeAlgorithm,
            InlandLakeShoreConditioningAlgorithm shoreAlgorithm,
            V12LandformRecipe.CoastProfile coastProfile,
            boolean predictiveLandReservation) {
        if (continentalBaseGenerator == null
                || lakeCalibrator == null
                || lakeRecipe == null
                || lakeAlgorithm == null
                || shoreAlgorithm == null
                || coastProfile == null) {
            throw new IllegalArgumentException("V15 lake-base dependencies must not be null");
        }
        this.continentalBaseGenerator = continentalBaseGenerator;
        this.lakeCalibrator = lakeCalibrator;
        this.lakeRecipe = lakeRecipe;
        this.lakeAlgorithm = lakeAlgorithm;
        this.shoreAlgorithm = shoreAlgorithm;
        this.coastProfile = coastProfile;
        this.predictiveLandReservation = predictiveLandReservation;
    }

    public static V15InlandLakeBaseTerrainGenerator standard() {
        return new V15InlandLakeBaseTerrainGenerator(
                V14OceanicBaseTerrainGenerator.standard(),
                InlandLakeDomainCalibrator.standard(),
                InlandLakeDomainRecipe.balanced(),
                InlandLakeDomainAlgorithm.standard(),
                InlandLakeShoreConditioningAlgorithm.standard(),
                V12LandformRecipe.balanced().coast(),
                true);
    }

    @Override
    public ElevationField generate(WorldGenesis genesis) {
        if (genesis == null) throw new IllegalArgumentException("genesis must not be null");

        WorldGenesis placementGenesis = predictiveLandReservation
                ? predictedLandGenesis(genesis, lakeRecipe)
                : genesis;
        ElevationField placementBase = requireBase(continentalBaseGenerator.generate(placementGenesis));
        InlandLakeDomainCalibration calibration = lakeCalibrator.calibrate(
                genesis,
                placementBase,
                lakeRecipe);
        if (calibration == null) {
            throw new IllegalStateException("V15 inland lake calibrator returned null");
        }
        InlandLakeDomain domain = lakeAlgorithm.generate(
                genesis,
                placementBase,
                calibration,
                lakeRecipe);
        if (domain == null) {
            throw new IllegalStateException("V15 inland lake domain algorithm returned null");
        }

        WorldGenesis exactGenesis = domain.lakeCellCount() == 0
                ? genesis
                : compensatedLandGenesis(genesis, domain.lakeCellCount());
        ElevationField authoritativeBase = sameLandCoverage(placementGenesis, exactGenesis)
                ? placementBase
                : requireBase(continentalBaseGenerator.generate(exactGenesis));

        if (domain.lakeCellCount() == 0) return authoritativeBase;
        verifyLakeDomainRemainsDry(authoritativeBase, domain);

        ElevationField conditioned = shoreAlgorithm.condition(authoritativeBase, domain, coastProfile);
        if (conditioned == null) {
            throw new IllegalStateException("V15 inland lake shore algorithm returned null");
        }
        return conditioned;
    }

    private ElevationField requireBase(ElevationField base) {
        if (base == null) throw new IllegalStateException("V15 continental base generator returned null");
        return base;
    }

    private static WorldGenesis predictedLandGenesis(
            WorldGenesis genesis,
            InlandLakeDomainRecipe recipe) {
        int lakeCoveragePpm = recipe.targetDryLandCoveragePpm();
        if (lakeCoveragePpm <= 0) return genesis;
        int area = DenseElevationField.cellCount(genesis.spec().bounds());
        int desiredDryCells = desiredDryCells(genesis, area);
        long denominator = PPM - (long) lakeCoveragePpm;
        if (denominator <= 0L) return withLandCoverage(genesis, PPM);

        int predictedLakeCells = Math.toIntExact(
                ((long) desiredDryCells * lakeCoveragePpm + denominator / 2L) / denominator);
        int width = Math.toIntExact(
                (long) genesis.spec().bounds().maxX() - genesis.spec().bounds().minX() + 1L);
        int height = Math.toIntExact(
                (long) genesis.spec().bounds().maxY() - genesis.spec().bounds().minY() + 1L);
        int limitingSpan = Math.min(width, height);
        int minimumSpan = Math.max(
                recipe.minimumComponentSpanCells(),
                limitingSpan / recipe.componentSpanWorldDivisor());
        int minimumLakeCells = Math.max(4, minimumSpan * minimumSpan / 2);
        if (desiredDryCells > 0) predictedLakeCells = Math.max(predictedLakeCells, minimumLakeCells);

        int predictedContinentalCells = Math.min(
                area,
                Math.addExact(desiredDryCells, Math.min(predictedLakeCells, area - desiredDryCells)));
        return withContinentalCells(genesis, predictedContinentalCells, area);
    }

    private static WorldGenesis compensatedLandGenesis(WorldGenesis genesis, int lakeCellCount) {
        int area = DenseElevationField.cellCount(genesis.spec().bounds());
        int desiredDryCells = desiredDryCells(genesis, area);
        int continentalCells = Math.min(area, Math.addExact(desiredDryCells, lakeCellCount));
        return withContinentalCells(genesis, continentalCells, area);
    }

    private static int desiredDryCells(WorldGenesis genesis, int area) {
        return Math.toIntExact(
                ((long) area * genesis.generationIntent().landCoverage().partsPerMillion() + PPM / 2L)
                        / PPM);
    }

    private static WorldGenesis withContinentalCells(
            WorldGenesis genesis,
            int continentalCells,
            int area) {
        int coveragePpm = Math.toIntExact(Math.min(
                (long) PPM,
                ((long) continentalCells * PPM + area / 2L) / area));
        return withLandCoverage(genesis, coveragePpm);
    }

    private static WorldGenesis withLandCoverage(WorldGenesis genesis, int coveragePpm) {
        WorldGenerationIntent intent = genesis.generationIntent();
        WorldGenerationIntent compensatedIntent = new WorldGenerationIntent(
                NormalizedValue.ofPartsPerMillion(coveragePpm),
                intent.landmassScale(),
                intent.fragmentation(),
                intent.relief(),
                intent.localRelief(),
                intent.landformScale(),
                intent.ruggedness(),
                intent.mountains());
        return new WorldGenesis(
                genesis.spec(),
                genesis.masterSeed(),
                genesis.generationRevision(),
                genesis.rngRevision(),
                compensatedIntent);
    }

    private static boolean sameLandCoverage(WorldGenesis first, WorldGenesis second) {
        return first.generationIntent().landCoverage().partsPerMillion()
                == second.generationIntent().landCoverage().partsPerMillion();
    }

    private static void verifyLakeDomainRemainsDry(
            ElevationField authoritativeBase,
            InlandLakeDomain domain) {
        int width = Math.toIntExact(
                (long) authoritativeBase.bounds().maxX() - authoritativeBase.bounds().minX() + 1L);
        int index = 0;
        for (int y = authoritativeBase.bounds().minY(); y <= authoritativeBase.bounds().maxY(); y++) {
            for (int x = authoritativeBase.bounds().minX(); x <= authoritativeBase.bounds().maxX(); x++) {
                if (domain.isLakeIndex(index)
                        && authoritativeBase.elevationSubunitsAt(x, y)
                                <= ElevationGenerationStage.SEA_LEVEL_SUBUNITS) {
                    throw new IllegalStateException(
                            "land-budget compensation failed to preserve lake-domain dry support at "
                                    + x + "," + y);
                }
                index++;
            }
        }
        if (index != Math.multiplyExact(
                width,
                Math.toIntExact((long) authoritativeBase.bounds().maxY()
                        - authoritativeBase.bounds().minY() + 1L))) {
            throw new IllegalStateException("unexpected lake-domain verification area");
        }
    }
}
