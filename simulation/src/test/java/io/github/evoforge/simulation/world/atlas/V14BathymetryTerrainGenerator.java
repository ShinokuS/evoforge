package io.github.evoforge.simulation.world.atlas;

import io.github.evoforge.simulation.world.genesis.GenerationRevision;
import io.github.evoforge.simulation.world.genesis.WorldGenesis;
import io.github.evoforge.simulation.world.genesis.WorldSpec;
import io.github.evoforge.simulation.world.spatial.WorldBounds;

/**
 * Replaceable V14 elevation pipeline: accepted V12/V13 terrain character plus an ocean-bounded
 * organic land domain and standing-water bathymetry.
 *
 * <p>The standard composition authors the finite land silhouette during V12 landmass rank selection,
 * before relief or mountain synthesis. A guaranteed external-ocean margin is only a safety guard;
 * the actual continent/island boundary comes from a broad deterministic domain field blended with
 * the accepted landmass potential. V12 and ordinary V13 generation remain on their accepted
 * unconstrained landmass path.</p>
 *
 * <p>The pipeline owns composition only. Base generation, mountain synthesis, bathymetry calibration
 * and bathymetry synthesis remain independent concerns. Water, river carving, materials and concrete
 * runtime Shapes remain later concerns.</p>
 */
public final class V14BathymetryTerrainGenerator implements ElevationGenerator {
    private final ElevationGenerator baseGenerator;
    private final BathymetryCalibrator calibrator;
    private final BathymetryRecipe recipe;
    private final BathymetryElevationAlgorithm bathymetryAlgorithm;

    public V14BathymetryTerrainGenerator(
            ElevationGenerator baseGenerator,
            BathymetryCalibrator calibrator,
            BathymetryRecipe recipe) {
        this(baseGenerator, calibrator, recipe, StructuredBathymetryAlgorithm.standard());
    }

    public V14BathymetryTerrainGenerator(
            ElevationGenerator baseGenerator,
            BathymetryCalibrator calibrator,
            BathymetryRecipe recipe,
            BathymetryElevationAlgorithm bathymetryAlgorithm) {
        if (baseGenerator == null || calibrator == null || recipe == null || bathymetryAlgorithm == null) {
            throw new IllegalArgumentException("V14 bathymetry generator dependencies must not be null");
        }
        this.baseGenerator = baseGenerator;
        this.calibrator = calibrator;
        this.recipe = recipe;
        this.bathymetryAlgorithm = bathymetryAlgorithm;
    }

    public static V14BathymetryTerrainGenerator standard() {
        ElevationGenerator oceanicMountains = new V13MountainTerrainGenerator(
                V14OceanicBaseTerrainGenerator.standard(),
                MountainCalibrator.standard(),
                MountainRecipe.balanced());
        return new V14BathymetryTerrainGenerator(
                oceanicMountains,
                BathymetryCalibrator.standard(),
                BathymetryRecipe.balanced());
    }

    @Override
    public ElevationField generate(WorldGenesis genesis) {
        if (genesis == null) throw new IllegalArgumentException("genesis must not be null");
        BathymetryCalibration calibration = calibrator.calibrate(genesis, recipe);
        if (calibration == null) {
            throw new IllegalStateException("bathymetry calibrator returned null");
        }
        ElevationField base = baseGenerator.generate(baseGenesis(genesis, recipe.baseTerrainFloorCells()));
        if (base == null) throw new IllegalStateException("V13 base generator returned null");
        return bathymetryAlgorithm.generate(genesis, base, calibration, recipe);
    }

    /**
     * The V13-compatible footprint base is generated with only a minimal negative-Z floor.
     * V12/V13 use sign for membership; their legacy negative amplitude is not allowed to become
     * bathymetric policy. V14 then owns the full final negative-Z headroom itself.
     */
    private static WorldGenesis baseGenesis(WorldGenesis genesis, int baseTerrainFloorCells) {
        WorldBounds bounds = genesis.spec().bounds();
        int baseMinZ = Math.max(bounds.minZ(), -baseTerrainFloorCells);
        if (baseMinZ >= 0) {
            throw new IllegalArgumentException("V14 bathymetry requires V13 base bounds below sea level");
        }
        WorldBounds baseBounds = new WorldBounds(
                bounds.minX(),
                bounds.maxX(),
                bounds.minY(),
                bounds.maxY(),
                baseMinZ,
                bounds.maxZ());
        WorldSpec baseSpec = new WorldSpec(baseBounds);
        return new WorldGenesis(
                baseSpec,
                genesis.masterSeed(),
                GenerationRevision.V13,
                genesis.rngRevision(),
                genesis.generationIntent());
    }
}
