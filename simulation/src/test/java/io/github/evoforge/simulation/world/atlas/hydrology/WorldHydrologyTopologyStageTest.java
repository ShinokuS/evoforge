package io.github.evoforge.simulation.world.atlas.hydrology;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.evoforge.simulation.world.atlas.ElevationField;
import org.junit.jupiter.api.Test;

final class WorldHydrologyTopologyStageTest {

    @Test
    void standardStageAnalyzesClosedBasinsWithoutAuthoringAdditionalWater() {
        ElevationField elevation = PriorityFloodDrainageBasinTopologyAnalyzerTest.bowlElevation();

        WorldHydrologyTopology hydrology = WorldHydrologyTopologyStage.standard().generate(elevation);

        assertEquals(1, hydrology.standingWaterTopology().bodyCount());
        assertEquals(1, hydrology.standingWaterTopology().domains().oceanicBodyCount());
        assertEquals(1, hydrology.drainageBasins().basinCount());
        assertEquals(0, hydrology.inlandLakes().lakeCount(),
                "Priority-Flood is drainage analysis; standard hydrology must not create a second water mask");
    }
}
