package io.github.evoforge.visualizer.scenario.movement;

import io.github.evoforge.simulation.runtime.SimulationRuntime;
import io.github.evoforge.simulation.mechanics.movement.MoveToCompletion;
import io.github.evoforge.simulation.world.object.ObjectId;
import io.github.evoforge.visualizer.scenario.ScenarioController;
import io.github.evoforge.visualizer.scenario.ScenarioDiagnostics;

final class MoveToPatrolController implements ScenarioController {
    private static final int[][] WAYPOINTS = {
            {2, 2, 1}, {6, -2, 2}, {10, 2, 3}, {14, -2, 4}, {-4, -2, 0}
    };

    private final SimulationRuntime runtime;
    private final ObjectId mover;
    private MoveToScenarioPlan plan;
    private int targetIndex;
    private String state = "starting";
    private boolean stopped;

    MoveToPatrolController(SimulationRuntime runtime, ObjectId mover) {
        this.runtime = runtime;
        this.mover = mover;
        startTarget(0);
    }

    @Override
    public void update(long tick) {
        if (stopped || runtime.view().moveTo().isActive(mover)) return;
        MoveToCompletion completion = runtime.view().moveTo().lastCompletion(mover);
        if (completion == null) return;
        if (!completion.reachedGoal()) {
            state = "stopped: " + completion.code().value();
            stopped = true;
            return;
        }
        startTarget((targetIndex + 1) % WAYPOINTS.length);
    }

    @Override
    public ScenarioDiagnostics diagnostics() {
        return MoveToScenarioDiagnostics.snapshot(
                runtime.view(), mover, plan,
                "waypoint=" + (targetIndex + 1) + "/" + WAYPOINTS.length + " | " + state);
    }

    private void startTarget(int index) {
        targetIndex = index;
        int[] target = WAYPOINTS[index];
        MoveToScenarioPlan candidate = MoveToScenarioRoutes.plan(
                runtime.view(), mover, target[0], target[1], target[2]);
        var result = MoveToScenarioCommands.start(
                runtime, mover, target[0], target[1], target[2]);
        if (result.accepted()) plan = candidate;
        state = result.code().value();
        if (!result.accepted()) stopped = true;
    }
}
