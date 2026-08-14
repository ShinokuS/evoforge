package io.github.evoforge.visualizer.scenario.movement;

import io.github.evoforge.simulation.control.movement.MoveToCommand;
import io.github.evoforge.simulation.control.movement.MoveToResult;
import io.github.evoforge.simulation.runtime.SimulationRuntime;
import io.github.evoforge.simulation.world.object.ObjectId;

final class MoveToScenarioCommands {
    private MoveToScenarioCommands() { }

    static MoveToResult start(
            SimulationRuntime runtime,
            ObjectId objectId,
            int x,
            int y,
            int z) {
        return runtime.submit(new MoveToCommand(objectId, x, y, z));
    }
}
