package io.github.evoforge.simulation.world.atlas;

import io.github.evoforge.simulation.world.genesis.GenerationRevision;
import io.github.evoforge.simulation.world.genesis.WorldGenesis;
import io.github.evoforge.simulation.world.genesis.WorldSpec;
import io.github.evoforge.simulation.world.spatial.WorldBounds;

/**
 * Replaceable V13 elevation pipeline: a capped V12 base plus one calibrated mountain algorithm.
 *
 * <p>The pipeline owns composition only. Base generation, semantic calibration and mountain spatial
 * synthesis are independent dependencies, so each can be replaced without changing the others.</p>
 */
public final class V13MountainTerrainGenerator implements ElevationGenerator {
    private final ElevationGenerator baseGenerator;
    private final MountainCalibrator calibrator;
    private final MountainRecipe recipe;
    private final MountainElevationAlgorithm mountainAlgorithm;

    public V13MountainTerrainGenerator(
            ElevationGenerator baseGenerator,
            MountainCalibrator calibrator,
            MountainRecipe recipe) {
        this(baseGenerator, calibrator, recipe, new MountainMorphologyAlgorithm());
    }

    public V13MountainTerrainGenerator(
            ElevationGenerator baseGenerator,
            MountainCalibrator calibrator,
            MountainRecipe recipe,
            MountainElevationAlgorithm mountainAlgorithm) {
        if (baseGenerator == null || calibrator == null || recipe == null || mountainAlgorithm == null) {
            throw new IllegalArgumentException("V13 mountain generator dependencies must not be null");
        }
        this.baseGenerator = baseGenerator;
        this.calibrator = calibrator;
        this.recipe = recipe;
        this.mountainAlgorithm = mountainAlgorithm;
    }

    public static V13MountainTerrainGenerator standard() {
        return new V13MountainTerrainGenerator(
                V12BaseTerrainGenerator.standard(),
                MountainCalibrator.standard(),
                MountainRecipe.balanced());
    }

    @Override
    public ElevationField generate(WorldGenesis genesis) {
        if (genesis == null) throw new IllegalArgumentException("genesis must not be null");
        MountainCalibration calibration = calibrator.calibrate(genesis, recipe);
        ElevationField base = baseGenerator.generate(baseGenesis(genesis, recipe.baseTerrainCeilingCells()));
        return mountainAlgorithm.generate(genesis, base, calibration, recipe);
    }

    private static WorldGenesis baseGenesis(WorldGenesis genesis, int baseTerrainCeilingCells) {
        WorldBounds bounds = genesis.spec().bounds();
        WorldBounds baseBounds = new WorldBounds(
                bounds.minX(),
                bounds.maxX(),
                bounds.minY(),
                bounds.maxY(),
                bounds.minZ(),
                Math.min(bounds.maxZ(), baseTerrainCeilingCells));
        WorldSpec baseSpec = new WorldSpec(
                baseBounds,
                genesis.spec().climate(),
                genesis.spec().physicalSpaceScale());
        return new WorldGenesis(
                baseSpec,
                genesis.masterSeed(),
                GenerationRevision.V12,
                genesis.rngRevision(),
                genesis.generationIntent());
    }
}
