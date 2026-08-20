package io.github.evoforge.simulation.world.atlas;

import io.github.evoforge.simulation.definition.NormalizedValue;
import io.github.evoforge.simulation.world.genesis.WorldGenerationIntent;
import io.github.evoforge.simulation.world.genesis.WorldGenesis;

/**
 * V15 pre-mountain composition: accepted V14 continental base plus independently selected Z=0
 * inland-water domains.
 *
 * <p>Placement reads the already accepted lowland geometry but never edits it. Once the actual lake
 * footprint is known, this coordinator compensates the continental coverage budget by that footprint
 * so authored {@code Land} continues to mean dry land as closely as normalized intent resolution
 * permits. Shore conditioning then converts only the selected domain to submerged membership and
 * caps a bounded shoreline belt using the accepted V12 coast profile. Mountains and bathymetry
 * remain downstream owners.</p>
 */
public final class V15InlandLakeBaseTerrainGenerator implements ElevationGenerator {
    private static final int PPM = NormalizedValue.SCALE;

    private final ElevationGenerator continentalBaseGenerator;
    private final InlandLakeDomainCalibrator lakeCalibrator;
    private final InlandLakeDomainRecipe lakeRecipe;
    private final InlandLakeDomainAlgorithm lakeAlgorithm;
    private final InlandLakeShoreConditioningAlgorithm shoreAlgorithm;
    private final V12LandformRecipe.CoastProfile coastProfile;

    public V15InlandLakeBaseTerrainGenerator(
            ElevationGenerator continentalBaseGenerator,
            InlandLakeDomainCalibrator lakeCalibrator,
            InlandLakeDomainRecipe lakeRecipe,
            InlandLakeDomainAlgorithm lakeAlgorithm,
            InlandLakeShoreConditioningAlgorithm shoreAlgorithm,
            V12LandformRecipe.CoastProfile coastProfile) {
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
    }

    public static V15InlandLakeBaseTerrainGenerator standard() {
        return new V15InlandLakeBaseTerrainGenerator(
                V14OceanicBaseTerrainGenerator.standard(),
                InlandLakeDomainCalibrator.standard(),
                InlandLakeDomainRecipe.balanced(),
                InlandLakeDomainAlgorithm.standard(),
                InlandLakeShoreConditioningAlgorithm.standard(),
                V12LandformRecipe.balanced().coast());
    }

    @Override
    public ElevationField generate(WorldGenesis genesis) {
        if (genesis == null) throw new IllegalArgumentException("genesis must not be null");

        ElevationField placementBase = requireBase(continentalBaseGenerator.generate(genesis));
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
        if (domain.lakeCellCount() == 0) return placementBase;

        WorldGenesis compensatedGenesis = compensatedLandGenesis(genesis, domain.lakeCellCount());
        ElevationField authoritativeBase = requireBase(
                continentalBaseGenerator.generate(compensatedGenesis));
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

    private static WorldGenesis compensatedLandGenesis(WorldGenesis genesis, int lakeCellCount) {
        int area = DenseElevationField.cellCount(genesis.spec().bounds());
        int desiredDryCells = Math.toIntExact(
                ((long) area * genesis.generationIntent().landCoverage().partsPerMillion() + PPM / 2L)
                        / PPM);
        int continentalCells = Math.min(area, Math.addExact(desiredDryCells, lakeCellCount));
        int compensatedCoveragePpm = Math.toIntExact(Math.min(
                (long) PPM,
                ((long) continentalCells * PPM + area / 2L) / area));

        WorldGenerationIntent intent = genesis.generationIntent();
        WorldGenerationIntent compensatedIntent = new WorldGenerationIntent(
                NormalizedValue.ofPartsPerMillion(compensatedCoveragePpm),
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
