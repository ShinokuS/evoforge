package io.github.evoforge.visualizer.scenario.movement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.github.evoforge.simulation.mechanics.movement.command.MoveToCommand;
import io.github.evoforge.simulation.mechanics.movement.command.MoveToResult;
import io.github.evoforge.simulation.runtime.SimulationRuntime;
import io.github.evoforge.simulation.world.object.ObjectId;
import io.github.evoforge.simulation.world.navigation.pathfinding.PathRoute;
import io.github.evoforge.visualizer.presentation.portal.ViewPortal;
import io.github.evoforge.visualizer.scenario.ScenarioSession;

final class MoveToInteractiveScenarioTest {

    @Test
    void cavePortalIsPresentationOnlyAndPhysicalMoveToMustUseTheDoorway() {
        ScenarioSession session = new MoveToInteractiveScenario().create();
        SimulationRuntime runtime = session.runtime();

        ViewPortal portal = session.portals().surfaceAt(1, 0);
        assertNotNull(portal);
        assertEquals("scenario:cave", portal.interior().id());
        assertEquals(1, runtime.view().terrainSurfaces().topZ(4, 0),
                "surface projection must see the mountain roof rather than cave floor");
        assertEquals(1, runtime.view().cells().objectCount(4, 2, 0),
                "covered interior object remains physically present below the surface");

        ObjectId outside = runtime.view().cells().objectAt(-4, 0, 0, 0);
        MoveToResult move = runtime.submit(new MoveToCommand(outside, 4, 0, 0));
        assertTrue(move.accepted(),
                "opening an interior view must not be required for physical pathfinding");

        PathRoute route = runtime.view().moveTo().activeRoute(outside);
        assertNotNull(route);
        boolean usedDoorway = false;
        for (int index = 0; index < route.size(); index++) {
            if (route.x(index) == 1 && route.y(index) == 0 && route.z(index) == 0) {
                usedDoorway = true;
            }
            if (route.x(index) >= 1 && route.x(index) <= 6
                    && route.y(index) >= -3 && route.y(index) <= 3
                    && route.z(index) == 0) {
                boolean wall = route.x(index) == 6
                        || route.y(index) == -3
                        || route.y(index) == 3
                        || (route.x(index) == 1 && route.y(index) != 0);
                assertTrue(!wall, "route must never cross a solid cave wall");
            }
        }
        assertTrue(usedDoorway, "route into the cave must pass through its only physical doorway");
    }

    @Test
    void physicalDoorwayCellItselfIsAValidMoveDestination() {
        ScenarioSession session = new MoveToInteractiveScenario().create();
        SimulationRuntime runtime = session.runtime();
        ObjectId outside = runtime.view().cells().objectAt(-4, 0, 0, 0);

        MoveToResult move = runtime.submit(new MoveToCommand(outside, 1, 0, 0));

        assertTrue(move.accepted(), "the presentation exit marker must not make its physical cell unwalkable");
        PathRoute route = runtime.view().moveTo().activeRoute(outside);
        assertNotNull(route);
        assertEquals(1, route.x(route.size() - 1));
        assertEquals(0, route.y(route.size() - 1));
        assertEquals(0, route.z(route.size() - 1));
    }
}
