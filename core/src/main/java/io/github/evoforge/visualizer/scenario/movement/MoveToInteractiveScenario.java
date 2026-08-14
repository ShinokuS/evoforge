package io.github.evoforge.visualizer.scenario.movement;

import io.github.evoforge.simulation.runtime.SimulationAssembly;
import io.github.evoforge.simulation.runtime.SimulationRuntime;
import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;
import io.github.evoforge.simulation.world.object.ObjectId;
import io.github.evoforge.simulation.world.object.definition.ObjectDefinitionId;
import io.github.evoforge.visualizer.scenario.ScenarioSession;
import io.github.evoforge.visualizer.scenario.ScenarioView;
import io.github.evoforge.visualizer.scenario.VisualizerScenario;

public final class MoveToInteractiveScenario implements VisualizerScenario {
    @Override public String id() { return "movement-click-to-move"; }
    @Override public String title() { return "Click To Move"; }
    @Override public String description() {
        return "LMB select mover; PgUp/PgDn inspect Z; RMB a visible walkable surface.";
    }

    @Override
    public ScenarioSession create() {
        SimulationAssembly assembly = SimulationAssembly.create();
        LandscapeDefinitionId ground = assembly.landscapeDefinition("scenario:click_move_ground");
        ObjectDefinitionId moverDefinition = assembly.objectDefinition("scenario:click_move_mover");
        assembly.movementRate(moverDefinition, 500);
        assembly.exclusiveOccupancy(moverDefinition);
        MoveToScenarioCourse.build(assembly, ground);

        ObjectId low = assembly.createObject(moverDefinition);
        ObjectId middle = assembly.createObject(moverDefinition);
        ObjectId high = assembly.createObject(moverDefinition);
        assembly.placeObject(low, -4, -2, 0);
        assembly.placeObject(middle, 6, 2, 2);
        assembly.placeObject(high, 14, -2, 4);

        SimulationRuntime runtime = assembly.start();
        return new ScenarioSession(
                runtime,
                new ScenarioView(0, 5f, 0f, 0.85f),
                new MoveToInteractiveController(runtime));
    }
}
