package io.github.evoforge.simulation.world.atlas;

import io.github.evoforge.simulation.world.genesis.GenerationRevision;
import io.github.evoforge.simulation.world.genesis.WorldGenesis;
import io.github.evoforge.simulation.world.genesis.WorldSpec;
import io.github.evoforge.simulation.world.spatial.WorldBounds;

/**
 * Replaceable V13 elevation generator: accepted V12 base morphology plus dedicated mountains.
 *
 * <p>The V12 base is deliberately generated inside a capped positive vertical range. V13 then uses
 * the remaining world height as mountain headroom. This prevents increasing world {@code maxZ}
 * from stretching every ordinary V12 hill into a mountain.</p>
 */
public final class V13MountainTerrainGenerator implements ElevationGenerator {
    private final ElevationGenerator baseGenerator;
    private final MountainCalibrator calibrator;
    private final MountainRecipe recipe;
    private final MountainMorphologyAlgorithm algorithm;

    public V13MountainTerrainGenerator(
            ElevationGenerator baseGenerator,
            MountainCalibrator calibrator,
            MountainRecipe recipe) {
        this(baseGenerator, calibrator, recipe, new MountainMorphologyAlgorithm());
    }

    V13MountainTerrainGenerator(
            ElevationGenerator baseGenerator,
            MountainCalibrator calibrator,
            MountainRecipe recipe,
            MountainMorphologyAlgorithm algorithm) {
        if (baseGenerator == null || calibrator == null || recipe == null || algorithm == null) {
            throw new IllegalArgumentException("V13 mountain generator dependencies must not be null");
        }
        this.baseGenerator = baseGenerator;
        this.calibrator = calibrator;
        this.recipe = recipe;
        this.algorithm = algorithm;
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
        WorldGenesis baseGenesis = baseGenesis(genesis, recipe.baseTerrainCeilingCells());
        ElevationField base = baseGenerator.generate(baseGenesis);
        ElevationField mountains = algorithm.generate(genesis, base, calibration, recipe);
        return MountainTerraceRegularizer.widenNarrowLevels(base, mountains);
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
