package io.github.evoforge.simulation.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.evoforge.simulation.mechanics.terrainmutation.command.PlaceTerrainCommand;
import io.github.evoforge.simulation.world.material.MaterialDefinitionId;
import io.github.evoforge.simulation.world.navigation.pathfinding.PathQuery;
import io.github.evoforge.simulation.world.navigation.pathfinding.PathRoute;
import io.github.evoforge.simulation.world.navigation.pathfinding.PathSearch;
import io.github.evoforge.simulation.world.navigation.pathfinding.PathSearchStatus;
import org.junit.jupiter.api.Test;

final class SimulationPathfindingIntegrationTest {

    @Test
    void runtimeExposesPathfinderOverProductionTraversalFacts() {
        SimulationAssembly assembly = SimulationAssembly.create();
        MaterialDefinitionId ground =
                assembly.landscapeDefinition("test:ground");

        for (int x = 0; x <= 5; x++) {
            assembly.placeTerrain(x, 0, -1, ground);
        }

        SimulationRuntime runtime = assembly.start();
        PathSearch search = runtime.view().pathfinder().begin(
                PathQuery.between(
                        0, 0, 0,
                        5, 0, 0));

        complete(search);

        assertEquals(PathSearchStatus.FOUND, search.status());
        PathRoute route = search.route();
        assertEquals(5, route.size());
        assertEquals(5000, route.totalCostUnits());
        assertEquals(5, route.x(4));
    }

    @Test
    void runtimeTerrainMutationInvalidatesResumableSearch() {
        SimulationAssembly assembly = SimulationAssembly.create();
        MaterialDefinitionId ground =
                assembly.landscapeDefinition("test:ground");

        for (int x = 0; x <= 20; x++) {
            assembly.placeTerrain(x, 0, -1, ground);
        }

        SimulationRuntime runtime = assembly.start();
        PathSearch search = runtime.view().pathfinder().begin(
                PathQuery.between(
                        0, 0, 0,
                        20, 0, 0));

        assertEquals(
                PathSearchStatus.RUNNING,
                search.advance(1));

        runtime.submit(new PlaceTerrainCommand(
                100,
                100,
                -1,
                ground));

        assertEquals(
                PathSearchStatus.STALE,
                search.advance(1));
    }

    private static void complete(
            PathSearch search) {

        while (search.status() == PathSearchStatus.RUNNING) {
            search.advance(256);
        }
    }
}
