package io.github.evoforge.simulation.world.atlas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.evoforge.simulation.world.genesis.GenerationRevision;
import io.github.evoforge.simulation.world.genesis.RngRevision;
import io.github.evoforge.simulation.world.genesis.WorldGenerationIntent;
import io.github.evoforge.simulation.world.genesis.WorldGenesis;
import io.github.evoforge.simulation.world.genesis.WorldSpec;
import io.github.evoforge.simulation.world.spatial.WorldBounds;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

final class V13MountainTerrainGeneratorCompositionTest {

    @Test
    void compositionAcceptsIndependentMountainAlgorithmWithoutChangingPipelineOwnership() {
        WorldBounds bounds = new WorldBounds(-4, 3, -4, 3, -12, 96);
        WorldGenesis genesis = new WorldGenesis(
                new WorldSpec(bounds),
                73L,
                GenerationRevision.V13,
                RngRevision.V1,
                WorldGenerationIntent.balanced());
        MountainRecipe recipe = MountainRecipe.balanced();
        boolean[] invoked = {false};
        ElevationField[] returned = {null};

        ElevationGenerator baseGenerator = baseGenesis -> {
            assertEquals(GenerationRevision.V12, baseGenesis.generationRevision());
            assertEquals(recipe.baseTerrainCeilingCells(), baseGenesis.spec().bounds().maxZ());
            long[] values = new long[DenseElevationField.cellCount(baseGenesis.spec().bounds())];
            Arrays.fill(values, ElevationField.SUBUNITS_PER_CELL);
            return new DenseElevationField(baseGenesis.spec().bounds(), values);
        };
        MountainElevationAlgorithm mountainAlgorithm = (receivedGenesis, base, calibration, receivedRecipe) -> {
            invoked[0] = true;
            assertSame(genesis, receivedGenesis);
            assertSame(recipe, receivedRecipe);
            assertEquals(bounds.minX(), base.bounds().minX());
            assertEquals(bounds.maxX(), base.bounds().maxX());
            assertTrue(calibration.typicalHalfWidthCells() > 0);

            long[] values = new long[DenseElevationField.cellCount(bounds)];
            Arrays.fill(values, 7L * ElevationField.SUBUNITS_PER_CELL);
            returned[0] = new DenseElevationField(bounds, values);
            return returned[0];
        };

        V13MountainTerrainGenerator generator = new V13MountainTerrainGenerator(
                baseGenerator,
                MountainCalibrator.standard(),
                recipe,
                mountainAlgorithm);

        ElevationField result = generator.generate(genesis);

        assertTrue(invoked[0]);
        assertSame(returned[0], result);
        assertEquals(7L * ElevationField.SUBUNITS_PER_CELL, result.elevationSubunitsAt(0, 0));
    }
}
