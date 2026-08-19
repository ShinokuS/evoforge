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

final class V14BathymetryTerrainGeneratorCompositionTest {

    @Test
    void compositionUsesV13BaseAndKeepsBathymetryAlgorithmReplaceable() {
        WorldBounds bounds = new WorldBounds(-4, 3, -4, 3, -96, 96);
        WorldGenesis genesis = new WorldGenesis(
                new WorldSpec(bounds),
                73L,
                GenerationRevision.V14,
                RngRevision.V1,
                WorldGenerationIntent.balanced());
        BathymetryRecipe recipe = BathymetryRecipe.balanced();
        boolean[] invoked = {false};
        ElevationField[] returned = {null};

        ElevationGenerator baseGenerator = baseGenesis -> {
            assertEquals(GenerationRevision.V13, baseGenesis.generationRevision());
            assertEquals(-recipe.baseTerrainFloorCells(), baseGenesis.spec().bounds().minZ());
            assertEquals(bounds.maxZ(), baseGenesis.spec().bounds().maxZ());
            long[] values = new long[DenseElevationField.cellCount(baseGenesis.spec().bounds())];
            Arrays.fill(values, -1L);
            return new DenseElevationField(baseGenesis.spec().bounds(), values);
        };
        BathymetryElevationAlgorithm algorithm = (receivedGenesis, base, calibration, receivedRecipe) -> {
            invoked[0] = true;
            assertSame(genesis, receivedGenesis);
            assertSame(recipe, receivedRecipe);
            assertEquals(bounds.minX(), base.bounds().minX());
            assertEquals(bounds.maxX(), base.bounds().maxX());
            assertEquals(bounds.minY(), base.bounds().minY());
            assertEquals(bounds.maxY(), base.bounds().maxY());
            assertEquals(bounds.minZ(), calibration.floorSubunits() / ElevationField.SUBUNITS_PER_CELL);

            long[] values = new long[DenseElevationField.cellCount(bounds)];
            Arrays.fill(values, -7L * ElevationField.SUBUNITS_PER_CELL);
            returned[0] = new DenseElevationField(bounds, values);
            return returned[0];
        };

        V14BathymetryTerrainGenerator generator = new V14BathymetryTerrainGenerator(
                baseGenerator,
                BathymetryCalibrator.standard(),
                recipe,
                algorithm);

        ElevationField result = generator.generate(genesis);

        assertTrue(invoked[0]);
        assertSame(returned[0], result);
        assertEquals(-7L * ElevationField.SUBUNITS_PER_CELL, result.elevationSubunitsAt(0, 0));
    }
}
