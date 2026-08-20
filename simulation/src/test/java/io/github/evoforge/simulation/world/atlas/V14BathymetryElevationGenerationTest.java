package io.github.evoforge.simulation.world.atlas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.evoforge.simulation.world.genesis.GenerationRevision;
import io.github.evoforge.simulation.world.genesis.RngRevision;
import io.github.evoforge.simulation.world.genesis.WorldGenerationIntent;
import io.github.evoforge.simulation.world.genesis.WorldGenesis;
import io.github.evoforge.simulation.world.genesis.WorldSpec;
import io.github.evoforge.simulation.world.spatial.WorldBounds;
import org.junit.jupiter.api.Test;

final class V14BathymetryElevationGenerationTest {

    @Test
    void v14PreservesAcceptedV13LandAndSubmergedFootprintWhileAuthoringDepth() {
        WorldBounds finalBounds = new WorldBounds(-32, 31, -32, 31, -96, 96);
        WorldBounds v13BaseBounds = new WorldBounds(-32, 31, -32, 31, -1, 96);
        long seed = 4_217L;
        WorldGenerationIntent intent = WorldGenerationIntent.balanced();

        WorldGenesis v13Genesis = new WorldGenesis(
                new WorldSpec(v13BaseBounds),
                seed,
                GenerationRevision.V13,
                RngRevision.V1,
                intent);
        WorldGenesis v14Genesis = new WorldGenesis(
                new WorldSpec(finalBounds),
                seed,
                GenerationRevision.V14,
                RngRevision.V1,
                intent);

        ElevationField v13 = new ElevationGenerationStage().generate(v13Genesis);
        ElevationField v14 = new ElevationGenerationStage().generate(v14Genesis);
        boolean foundMeaningfulDepth = false;

        for (int y = finalBounds.minY(); y <= finalBounds.maxY(); y++) {
            for (int x = finalBounds.minX(); x <= finalBounds.maxX(); x++) {
                long base = v13.elevationSubunitsAt(x, y);
                long bathymetry = v14.elevationSubunitsAt(x, y);
                assertEquals(base < 0L, bathymetry < 0L, "V14 must preserve the V13 submerged footprint");
                if (base > 0L) {
                    assertEquals(base, bathymetry, "V14 must preserve accepted V13 land exactly");
                } else if (bathymetry < -ElevationField.SUBUNITS_PER_CELL) {
                    foundMeaningfulDepth = true;
                }
            }
        }

        assertTrue(foundMeaningfulDepth, "a 64x64 ocean-capable world should contain real bathymetric depth");
        assertEquals(finalBounds, v14.bounds());
    }
}
