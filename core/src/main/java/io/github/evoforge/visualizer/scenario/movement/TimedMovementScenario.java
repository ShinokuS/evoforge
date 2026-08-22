package io.github.evoforge.visualizer.scenario.movement;

import io.github.evoforge.simulation.mechanics.movement.command.MoveStepCommand;
import io.github.evoforge.simulation.mechanics.movement.command.MoveStepResult;
import io.github.evoforge.simulation.runtime.SimulationAssembly;
import io.github.evoforge.simulation.runtime.SimulationRuntime;
import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;
import io.github.evoforge.simulation.world.object.ObjectId;
import io.github.evoforge.simulation.world.object.definition.ObjectDefinitionId;
import io.github.evoforge.visualizer.scenario.ScenarioSession;
import io.github.evoforge.visualizer.scenario.ScenarioTerrain;
import io.github.evoforge.visualizer.scenario.ScenarioView;
import io.github.evoforge.visualizer.scenario.VisualizerScenario;

/** Flat scene comparing two deterministic timed movement rates. */
public final class TimedMovementScenario implements VisualizerScenario {
    @Override public String id() { return "timed-movement"; }
    @Override public String title() { return "Timed Movement"; }
    @Override public String description() {
        return "A slow and a fast mover perform the same one-cell step on flat ground.";
    }

    @Override
    public ScenarioSession create() {
        SimulationAssembly assembly = SimulationAssembly.create();
        LandscapeDefinitionId ground = assembly.landscapeDefinition("scenario:movement_ground");
        ObjectDefinitionId slowDefinition = assembly.objectDefinition("scenario:slow_walker");
        ObjectDefinitionId fastDefinition = assembly.objectDefinition("scenario:fast_walker");
        assembly.movementRate(slowDefinition, 125);
        assembly.movementRate(fastDefinition, 500);
        assembly.exclusiveOccupancy(slowDefinition);
        assembly.exclusiveOccupancy(fastDefinition);
        ScenarioTerrain.fill(assembly, ground, -5, 5, -2, 2, 0);

        ObjectId slow = assembly.createObject(slowDefinition);
        ObjectId fast = assembly.createObject(fastDefinition);
        assembly.placeObject(slow, -3, -1, 1);
        assembly.placeObject(fast, -3, 1, 1);

        SimulationRuntime runtime = assembly.start();
        requireStarted(runtime.submit(new MoveStepCommand(slow, -2, -1, 1)), "slow walker");
        requireStarted(runtime.submit(new MoveStepCommand(fast, -2, 1, 1)), "fast walker");
        return new ScenarioSession(runtime, new ScenarioView(1, -1f, 0f, 0.8f));
    }

    private static void requireStarted(MoveStepResult result, String label) {
        if (!result.accepted()) {
            throw new IllegalStateException(label + " scenario movement was rejected: " + result.code());
        }
    }
}
