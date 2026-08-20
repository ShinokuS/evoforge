package io.github.evoforge.simulation.world.atlas.hydrology;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.evoforge.simulation.world.atlas.ElevationField;
import org.junit.jupiter.api.Test;

final class SpillLevelInlandLakeFormationAlgorithmTest {
    private static final long CELL = ElevationField.SUBUNITS_PER_CELL;

    @Test
    void explicitExperimentalAlgorithmFillsSignificantBasinWithoutMutatingTerrainOrSeaLevel() {
        ElevationField elevation = PriorityFloodDrainageBasinTopologyAnalyzerTest.bowlElevation();
        StandingWaterTopology outlets = new BroadStandingWaterBodySelector().select(
                new ConnectedStandingWaterTopologyAnalyzer().analyze(elevation));
        DrainageBasinTopology basins =
                DrainageBasinTopologyAnalyzer.standard().analyze(elevation, outlets);

        InlandLakeTopology lakes = new SpillLevelInlandLakeFormationAlgorithm().generate(
                elevation,
                basins,
                InlandLakeFormationRecipe.balanced());

        assertEquals(1, lakes.lakeCount());
        assertTrue(lakes.isLakeAt(3, 3));
        assertFalse(lakes.isLakeAt(2, 2));
        assertEquals(3L * CELL, lakes.surfaceElevationSubunitsAt(3, 3));
        assertEquals(1L * CELL, elevation.elevationSubunitsAt(3, 3));
        assertTrue(lakes.surfaceElevationSubunitsAt(3, 3) > 0L);
    }
}
