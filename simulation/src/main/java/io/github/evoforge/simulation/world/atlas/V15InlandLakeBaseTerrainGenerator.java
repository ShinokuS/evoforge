package io.github.evoforge.simulation.world.atlas;

import io.github.evoforge.simulation.world.genesis.WorldGenesis;

/**
 * V15 pre-mountain composition: accepted V14 continental base plus independently selected Z=0
 * inland-water domains.
 *
 * <p>Placement reads the already accepted lowland geometry but never edits it. Shore conditioning
 * then converts only the selected domain to submerged membership and caps a bounded shoreline belt
 * using the accepted V12 coast profile. Mountains and bathymetry remain downstream owners.</p>
 */
public final class V15InlandLakeBaseTerrainGenerator implements ElevationGenerator {
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
        ElevationField continentalBase = continentalBaseGenerator.generate(genesis);
        if (continentalBase == null) {
            throw new IllegalStateException("V15 continental base generator returned null");
        }
        InlandLakeDomainCalibration calibration = lakeCalibrator.calibrate(
                genesis,
                continentalBase,
                lakeRecipe);
        if (calibration == null) {
            throw new IllegalStateException("V15 inland lake calibrator returned null");
        }
        InlandLakeDomain domain = lakeAlgorithm.generate(
                genesis,
                continentalBase,
                calibration,
                lakeRecipe);
        if (domain == null) {
            throw new IllegalStateException("V15 inland lake domain algorithm returned null");
        }
        ElevationField conditioned = shoreAlgorithm.condition(continentalBase, domain, coastProfile);
        if (conditioned == null) {
            throw new IllegalStateException("V15 inland lake shore algorithm returned null");
        }
        return conditioned;
    }
}
