package io.github.evoforge.visualizer.scenario;

import io.github.evoforge.simulation.control.movement.MoveStepCommand;
import io.github.evoforge.simulation.control.movement.MoveStepResult;
import io.github.evoforge.simulation.runtime.SimulationAssembly;
import io.github.evoforge.simulation.runtime.SimulationRuntime;
import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;
import io.github.evoforge.simulation.world.object.ObjectId;
import io.github.evoforge.simulation.world.object.definition.ObjectDefinitionId;

/** Minimal execution-conflict scene: two exclusive movers claim one destination. */
public final class OccupancyContentionScenario implements VisualizerScenario {

    @Override
    public String id() {
        return "occupancy-contention";
    }

    @Override
    public String title() {
        return "Occupancy Contention";
    }

    @Override
    public String description() {
        return "Two exclusive movers compete for one destination. Use F5 to inspect the claim.";
    }

    @Override
    public ScenarioSession create() {
        SimulationAssembly assembly = SimulationAssembly.create();
        LandscapeDefinitionId ground = assembly.landscapeDefinition(
                "scenario:occupancy_ground");
        ObjectDefinitionId moverDefinition = assembly.objectDefinition(
                "scenario:exclusive_mover");

        assembly.movementRate(moverDefinition, 500);
        assembly.exclusiveOccupancy(moverDefinition);
        ScenarioTerrain.fill(assembly, ground, -3, 3, -2, 2, 0);

        ObjectId left = assembly.createObject(moverDefinition);
        ObjectId right = assembly.createObject(moverDefinition);
        assembly.placeObject(left, -1, 0, 1);
        assembly.placeObject(right, 1, 0, 1);

        SimulationRuntime runtime = assembly.start();
        require(
                runtime.submit(new MoveStepCommand(left, 0, 0, 1)),
                MoveStepResult.STARTED,
                "first mover");
        require(
                runtime.submit(new MoveStepCommand(right, 0, 0, 1)),
                MoveStepResult.DESTINATION_RESERVED,
                "competing mover");

        return new ScenarioSession(
                runtime,
                new ScenarioView(1, 0f, 0f, 0.65f));
    }

    private static void require(
            MoveStepResult actual,
            MoveStepResult expected,
            String label) {

        if (actual != expected) {
            throw new IllegalStateException(
                    label + " expected " + expected + " but was " + actual);
        }
    }
}
