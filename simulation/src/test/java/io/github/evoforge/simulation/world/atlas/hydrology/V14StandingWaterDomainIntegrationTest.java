package io.github.evoforge.simulation.world.atlas.hydrology;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.evoforge.simulation.world.atlas.ElevationField;
import io.github.evoforge.simulation.world.atlas.ElevationGenerationStage;
import io.github.evoforge.simulation.world.genesis.GenerationRevision;
import io.github.evoforge.simulation.world.genesis.RngRevision;
import io.github.evoforge.simulation.world.genesis.WorldGenerationIntent;
import io.github.evoforge.simulation.world.genesis.WorldGenesis;
import io.github.evoforge.simulation.world.genesis.WorldSpec;
import io.github.evoforge.simulation.world.spatial.WorldBounds;
import org.junit.jupiter.api.Test;

final class V14StandingWaterDomainIntegrationTest {

    @Test
    void standardV14HasOneOceanicBoundaryComponentAndOnlyInlandOtherBodies() {
        WorldBounds bounds = new WorldBounds(-32, 31, -32, 31, -96, 96);
        WorldGenesis genesis = new WorldGenesis(
                new WorldSpec(bounds),
                4_217L,
                GenerationRevision.V14,
                RngRevision.V1,
                WorldGenerationIntent.balanced());
        ElevationField elevation = new ElevationGenerationStage().generate(genesis);

        StandingWaterHydrologyTopology topology =
                StandingWaterHydrologyTopologyStage.standard().generate(elevation);

        assertEquals(1, topology.domains().oceanicBodyCount(),
                "the guaranteed V14 water perimeter must form one four-connected oceanic component");
        int oceanicBody = -1;
        for (int bodyId = 0; bodyId < topology.bodyCount(); bodyId++) {
            if (topology.domains().isOceanic(bodyId)) oceanicBody = bodyId;
        }
        assertTrue(oceanicBody >= 0);
        for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
            for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                if (!isBoundary(bounds, x, y)) continue;
                assertEquals(oceanicBody, topology.standingWater().bodyIdAt(x, y),
                        "every boundary cell must belong to the same oceanic hydrology body");
            }
        }
        for (int bodyId = 0; bodyId < topology.bodyCount(); bodyId++) {
            if (bodyId != oceanicBody) {
                assertEquals(StandingWaterDomainRole.INLAND, topology.domains().role(bodyId));
            }
        }
    }

    private static boolean isBoundary(WorldBounds bounds, int x, int y) {
        return x == bounds.minX()
                || x == bounds.maxX()
                || y == bounds.minY()
                || y == bounds.maxY();
    }
}
