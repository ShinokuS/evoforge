package io.github.evoforge.simulation.world.atlas;

import io.github.evoforge.simulation.world.genesis.GenerationRevision;
import io.github.evoforge.simulation.world.genesis.WorldGenesis;
import io.github.evoforge.simulation.world.genesis.WorldSpec;
import io.github.evoforge.simulation.world.spatial.WorldBounds;

/**
 * Replaceable V14 elevation pipeline: accepted V13 dry terrain plus standing-water bathymetry.
 *
 * <p>The pipeline owns composition only. V13 base generation, bathymetry calibration and spatial
 * synthesis remain independent dependencies. V14 deliberately changes only already-submerged
 * elevation; Water, river carving, materials and concrete runtime Shapes remain later concerns.</p>
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
        return new V14BathymetryTerrainGenerator(
                V13MountainTerrainGenerator.standard(),
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
     * The accepted V13 land/ocean footprint is generated with only a minimal negative-Z floor.
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
        WorldSpec baseSpec = new WorldSpec(
                baseBounds,
                genesis.spec().climate(),
                genesis.spec().physicalSpaceScale());
        return new WorldGenesis(
                baseSpec,
                genesis.masterSeed(),
                GenerationRevision.V13,
                genesis.rngRevision(),
                genesis.generationIntent());
    }
}
