package io.github.evoforge.simulation.world.atlas.hydrology;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.evoforge.simulation.world.spatial.WorldBounds;
import java.util.List;
import org.junit.jupiter.api.Test;

final class BoundaryStandingWaterDomainAnalyzerTest {
    private static final WorldBounds BOUNDS = new WorldBounds(0, 4, 0, 4, -4, 4);

    @Test
    void boundaryConnectivityAloneDefinesOceanicDomainRole() {
        StandingWaterTopology water = topology(
                new StandingWaterBody(0, 20L, 8L, true, 0, 4, 0, 4),
                new StandingWaterBody(1, 400L, 80L, false, 1, 3, 1, 3));

        StandingWaterDomainTopology result = StandingWaterDomainAnalyzer.standard().analyze(water);

        assertEquals(StandingWaterDomainRole.OCEANIC, result.role(0));
        assertEquals(StandingWaterDomainRole.INLAND, result.role(1),
                "even a very large internal body remains inland when it is disconnected from oceanic boundary water");
        assertEquals(1, result.oceanicBodyCount());
        assertEquals(1, result.inlandBodyCount());
    }

    private static StandingWaterTopology topology(StandingWaterBody... bodies) {
        List<StandingWaterBody> values = List.of(bodies);
        return new StandingWaterTopology() {
            @Override
            public WorldBounds bounds() {
                return BOUNDS;
            }

            @Override
            public int bodyCount() {
                return values.size();
            }

            @Override
            public int bodyIdAt(int x, int y) {
                throw new UnsupportedOperationException("domain analyzer consumes per-body boundary facts");
            }

            @Override
            public StandingWaterBody body(int id) {
                return values.get(id);
            }
        };
    }
}
