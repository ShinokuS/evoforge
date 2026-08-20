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
    void v14PreservesOceanicMountainFootprintWhileAuthoringDepth() {
        WorldBounds finalBounds = new WorldBounds(-32, 31, -32, 31, -96, 96);
        WorldBounds baseBounds = new WorldBounds(-32, 31, -32, 31, -1, 96);
        long seed = 4_217L;
        WorldGenerationIntent intent = WorldGenerationIntent.balanced();

        WorldGenesis baseGenesis = new WorldGenesis(
                new WorldSpec(baseBounds),
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

        ElevationField oceanicMountains = new V13MountainTerrainGenerator(
                V14OceanicBaseTerrainGenerator.standard(),
                MountainCalibrator.standard(),
                MountainRecipe.balanced())
                .generate(baseGenesis);
        ElevationField v14 = new ElevationGenerationStage().generate(v14Genesis);
        LandmassBoundaryCalibration boundary = LandmassBoundaryCalibrator.standard()
                .calibrate(baseGenesis, LandmassBoundaryRecipe.balanced());
        boolean foundMeaningfulDepth = false;

        assertEquals(8, boundary.minimumOceanMarginCells(),
                "64x64 balanced worlds must reserve a broad oceanic margin");

        for (int y = finalBounds.minY(); y <= finalBounds.maxY(); y++) {
            for (int x = finalBounds.minX(); x <= finalBounds.maxX(); x++) {
                long base = oceanicMountains.elevationSubunitsAt(x, y);
                long bathymetry = v14.elevationSubunitsAt(x, y);
                assertEquals(base < 0L, bathymetry < 0L,
                        "V14 bathymetry must preserve the oceanic submerged footprint");
                if (base > 0L) {
                    assertEquals(base, bathymetry,
                            "V14 bathymetry must preserve oceanic mountain land exactly");
                } else if (bathymetry < -ElevationField.SUBUNITS_PER_CELL) {
                    foundMeaningfulDepth = true;
                }
                if (edgeDistance(finalBounds, x, y) < boundary.minimumOceanMarginCells()) {
                    assertTrue(bathymetry < 0L,
                            "the guaranteed oceanic margin must remain submerged after bathymetry");
                }
            }
        }

        assertTrue(foundMeaningfulDepth, "a 64x64 ocean-capable world should contain real bathymetric depth");
        assertEquals(finalBounds, v14.bounds());
    }

    private static int edgeDistance(WorldBounds bounds, int x, int y) {
        return Math.min(
                Math.min(x - bounds.minX(), bounds.maxX() - x),
                Math.min(y - bounds.minY(), bounds.maxY() - y));
    }
}
