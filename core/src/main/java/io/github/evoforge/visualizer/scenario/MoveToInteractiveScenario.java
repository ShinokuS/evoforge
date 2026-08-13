package io.github.evoforge.visualizer.scenario;

import io.github.evoforge.simulation.runtime.SimulationAssembly;
import io.github.evoforge.simulation.runtime.SimulationRuntime;
import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;
import io.github.evoforge.simulation.world.object.ObjectId;
import io.github.evoforge.simulation.world.object.definition.ObjectDefinitionId;

public final class MoveToInteractiveScenario implements VisualizerScenario {

    @Override public String id() { return "movement-click-to-move"; }
    @Override public String title() { return "Click To Move"; }
    @Override public String description() {
        return "LMB select mover; RMB choose destination; route remains visible.";
    }

    @Override
    public ScenarioSession create() {
        SimulationAssembly assembly = SimulationAssembly.create();
        LandscapeDefinitionId ground = assembly.landscapeDefinition(
                "scenario:click_move_ground");
        ObjectDefinitionId moverDefinition = assembly.objectDefinition(
                "scenario:click_move_mover");
        assembly.movementRate(moverDefinition, 500);
        assembly.exclusiveOccupancy(moverDefinition);
        ScenarioTerrain.fill(assembly, ground, -8, 8, -5, 5, 0);

        ObjectId first = assembly.createObject(moverDefinition);
        ObjectId second = assembly.createObject(moverDefinition);
        ObjectId third = assembly.createObject(moverDefinition);
        assembly.placeObject(first, -4, -2, 1);
        assembly.placeObject(second, -4, 0, 1);
        assembly.placeObject(third, -4, 2, 1);

        SimulationRuntime runtime = assembly.start();
        return new ScenarioSession(
                runtime,
                new ScenarioView(1, 0f, 0f, 0.75f),
                new MoveToInteractiveController(runtime));
    }
}
