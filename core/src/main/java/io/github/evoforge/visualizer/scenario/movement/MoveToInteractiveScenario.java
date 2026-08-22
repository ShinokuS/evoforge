package io.github.evoforge.visualizer.scenario.movement;

import io.github.evoforge.simulation.runtime.SimulationAssembly;
import io.github.evoforge.simulation.runtime.SimulationRuntime;
import io.github.evoforge.simulation.world.material.MaterialDefinitionId;
import io.github.evoforge.simulation.world.object.ObjectId;
import io.github.evoforge.simulation.world.object.definition.ObjectDefinitionId;
import io.github.evoforge.visualizer.presentation.object.ObjectPresentationBindings;
import io.github.evoforge.visualizer.presentation.portal.FixedViewPortals;
import io.github.evoforge.visualizer.presentation.portal.InteriorView;
import io.github.evoforge.visualizer.presentation.portal.ViewPortal;
import io.github.evoforge.visualizer.presentation.portal.ViewPortalKind;
import io.github.evoforge.visualizer.presentation.weather.WeatherPresentationLookup;
import io.github.evoforge.visualizer.scenario.ScenarioController;
import io.github.evoforge.visualizer.scenario.ScenarioSession;
import io.github.evoforge.visualizer.scenario.ScenarioTerrain;
import io.github.evoforge.visualizer.scenario.ScenarioView;
import io.github.evoforge.visualizer.scenario.VisualizerScenario;

/** End-to-end acceptance for persistent Move targeting across a physically enclosed cave. */
public final class MoveToInteractiveScenario implements VisualizerScenario {

    @Override public String id() { return "movement-click-to-move"; }
    @Override public String title() { return "Surface / Interior Move"; }
    @Override public String description() {
        return "Select the mover, choose Move, open the cave marker, View inside, then choose a destination. The cave has one physical entrance.";
    }

    @Override
    public ScenarioSession create() {
        SimulationAssembly assembly = SimulationAssembly.create()
                .worldBounds(-8, 8, -5, 5, -1, 3);
        MaterialDefinitionId ground = assembly.landscapeDefinition(
                "scenario:surface_interior_ground");
        MaterialDefinitionId hill = assembly.landscapeDefinition(
                "scenario:surface_interior_hill");
        ObjectDefinitionId moverDefinition = assembly.objectDefinition(
                "scenario:surface_interior_mover");
        assembly.movementRate(moverDefinition, 500);
        assembly.exclusiveOccupancy(moverDefinition);

        ScenarioTerrain.fill(assembly, ground, -8, 8, -5, 5, -1);
        ScenarioTerrain.fill(assembly, hill, 1, 6, -3, 3, 1);

        // FullShape terrain at standing level is the actual cave enclosure. The
        // west doorway at (1,0,0) is the only opening; the Interior view includes
        // these wall cells so the whole room boundary remains visible.
        for (int x = 1; x <= 6; x++) {
            assembly.placeTerrain(x, -3, 0, hill);
            assembly.placeTerrain(x, 3, 0, hill);
        }
        for (int y = -2; y <= 2; y++) {
            if (y != 0) assembly.placeTerrain(1, y, 0, hill);
            assembly.placeTerrain(6, y, 0, hill);
        }

        ObjectId outsideMover = assembly.createObject(moverDefinition);
        assembly.placeObject(outsideMover, -4, 0, 0);
        ObjectId insideReference = assembly.createObject(moverDefinition);
        assembly.placeObject(insideReference, 4, 2, 0);

        SimulationRuntime runtime = assembly.start();
        InteriorView cave = new InteriorView(
                "scenario:cave",
                "Covered cave",
                1, 6,
                -3, 3,
                0, 0,
                0);
        ViewPortal entrance = new ViewPortal(
                "scenario:cave-entrance",
                ViewPortalKind.ENTRANCE,
                "Cave entrance",
                1, 0, 2,
                1, 0, 0,
                cave);

        return new ScenarioSession(
                runtime,
                new ScenarioView(0, 0f, 0f, 0.85f),
                ScenarioController.NONE,
                ObjectPresentationBindings.empty(),
                WeatherPresentationLookup.CLEAR_LOOKUP,
                new FixedViewPortals(entrance));
    }
}
