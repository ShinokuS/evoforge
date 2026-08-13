package io.github.evoforge.simulation.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;
import io.github.evoforge.simulation.world.mechanics.geometry.RampShape;
import io.github.evoforge.simulation.world.pathfinding.PathQuery;
import io.github.evoforge.simulation.world.pathfinding.PathRoute;
import io.github.evoforge.simulation.world.pathfinding.PathSearch;
import io.github.evoforge.simulation.world.pathfinding.PathSearchStatus;
import org.junit.jupiter.api.Test;

final class SimulationPathfinding3dTest {

    @Test
    void productionPathfinderTraversesSuccessiveRampElevations() {
        SimulationAssembly assembly = SimulationAssembly.create();
        LandscapeDefinitionId ground =
                assembly.landscapeDefinition("test:path_3d_ground");

        assembly.placeTerrain(-1, 0, -1, ground);
        assembly.placeTerrain(0, 0, 0, ground);
        assembly.setShape(0, 0, 0, RampShape.POSITIVE_X);
        assembly.placeTerrain(1, 0, 0, ground);
        assembly.placeTerrain(2, 0, 1, ground);
        assembly.setShape(2, 0, 1, RampShape.POSITIVE_X);
        assembly.placeTerrain(3, 0, 1, ground);

        SimulationRuntime runtime = assembly.start();
        PathSearch search = runtime.view().pathfinder().begin(
                PathQuery.between(
                        -1, 0, 0,
                        3, 0, 2));
        complete(search);

        assertEquals(PathSearchStatus.FOUND, search.status());
        PathRoute route = search.route();
        assertEquals(4, route.size());
        assertEquals(3, route.x(route.size() - 1));
        assertEquals(2, route.z(route.size() - 1));

        boolean climbedFirstLevel = false;
        boolean climbedSecondLevel = false;
        for (int index = 0; index < route.size(); index++) {
            climbedFirstLevel |= route.z(index) >= 1;
            climbedSecondLevel |= route.z(index) >= 2;
        }
        assertTrue(climbedFirstLevel);
        assertTrue(climbedSecondLevel);
    }

    private static void complete(PathSearch search) {
        while (search.status() == PathSearchStatus.RUNNING) {
            search.advance(256);
        }
    }
}
