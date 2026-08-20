package io.github.evoforge.simulation.world.atlas.hydrology;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.evoforge.simulation.world.atlas.ElevationField;
import org.junit.jupiter.api.Test;

final class WorldHydrologyTopologyStageTest {
    private static final long CELL = ElevationField.SUBUNITS_PER_CELL;

    @Test
    void standardStageKeepsOceanTopologyAndAboveSeaLakeFormationSeparate() {
        ElevationField elevation = PriorityFloodDrainageBasinTopologyAnalyzerTest.bowlElevation();

        WorldHydrologyTopology hydrology = WorldHydrologyTopologyStage.standard().generate(elevation);

        assertEquals(1, hydrology.standingWaterTopology().bodyCount());
        assertEquals(1, hydrology.standingWaterTopology().domains().oceanicBodyCount());
        assertEquals(1, hydrology.drainageBasins().basinCount());
        assertEquals(1, hydrology.inlandLakes().lakeCount());
        assertEquals(3L * CELL, hydrology.inlandLakes().lake(0).surfaceElevationSubunits());
        assertTrue(hydrology.inlandLakes().lake(0).surfaceElevationSubunits() > 0L);
    }
}
