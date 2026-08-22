package io.github.evoforge.visualizer.scenario.occupancy;

import io.github.evoforge.simulation.mechanics.movement.command.MoveStepCommand;
import io.github.evoforge.simulation.mechanics.movement.command.MoveStepResult;
import io.github.evoforge.simulation.runtime.SimulationAssembly;
import io.github.evoforge.simulation.runtime.SimulationRuntime;
import io.github.evoforge.simulation.world.material.MaterialDefinitionId;
import io.github.evoforge.simulation.world.object.ObjectId;
import io.github.evoforge.simulation.world.object.definition.ObjectDefinitionId;
import io.github.evoforge.visualizer.scenario.ScenarioSession;
import io.github.evoforge.visualizer.scenario.ScenarioTerrain;
import io.github.evoforge.visualizer.scenario.ScenarioView;
import io.github.evoforge.visualizer.scenario.VisualizerScenario;

/** Minimal execution-conflict scene: two exclusive movers claim one destination. */
public final class OccupancyContentionScenario implements VisualizerScenario {
    @Override public String id() { return "occupancy-contention"; }
    @Override public String title() { return "Occupancy Contention"; }
    @Override public String description() {
        return "Two exclusive movers compete for one destination. Use F5 to inspect the claim.";
    }

    @Override
    public ScenarioSession create() {
        SimulationAssembly assembly = SimulationAssembly.create();
        MaterialDefinitionId ground = assembly.landscapeDefinition("scenario:occupancy_ground");
        ObjectDefinitionId moverDefinition = assembly.objectDefinition("scenario:exclusive_mover");
        assembly.movementRate(moverDefinition, 500);
        assembly.exclusiveOccupancy(moverDefinition);
        ScenarioTerrain.fill(assembly, ground, -3, 3, -2, 2, 0);

        ObjectId left = assembly.createObject(moverDefinition);
        ObjectId right = assembly.createObject(moverDefinition);
        assembly.placeObject(left, -1, 0, 1);
        assembly.placeObject(right, 1, 0, 1);
        SimulationRuntime runtime = assembly.start();
        requireAccepted(runtime.submit(new MoveStepCommand(left, 0, 0, 1)), "first mover");
        requireRejected(
                runtime.submit(new MoveStepCommand(right, 0, 0, 1)),
                "movement:destination_reserved",
                "competing mover");
        return new ScenarioSession(runtime, new ScenarioView(1, 0f, 0f, 0.65f));
    }

    private static void requireAccepted(MoveStepResult result, String label) {
        if (!result.accepted()) throw new IllegalStateException(label + " was rejected: " + result.code());
    }

    private static void requireRejected(MoveStepResult result, String expectedCode, String label) {
        if (result.accepted() || !expectedCode.equals(result.code().value())) {
            throw new IllegalStateException(label + " expected " + expectedCode + " but was " + result.code());
        }
    }
}
