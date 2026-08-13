package io.github.evoforge.visualizer.scenario;

import io.github.evoforge.simulation.runtime.SimulationRuntime;
import io.github.evoforge.simulation.world.object.ObjectId;

final class MoveToInteractiveController implements ScenarioController {

    private final SimulationRuntime runtime;
    private ObjectId mover;
    private MoveToScenarioPlan plan;
    private String state = "LMB select mover";

    MoveToInteractiveController(SimulationRuntime runtime) {
        this.runtime = runtime;
    }

    @Override public void update(long tick) { }

    @Override
    public ScenarioDiagnostics diagnostics() {
        return MoveToScenarioDiagnostics.snapshot(
                runtime.view(), mover, plan, state);
    }

    @Override
    public void primaryCellAction(int x, int y, int z) {
        int count = runtime.view().cells().objectCount(x, y, z);
        mover = count == 0 ? null : runtime.view().cells().objectAt(x, y, z, 0);
        plan = null;
        state = mover == null ? "no mover selected" : "selected=" + mover;
    }

    @Override
    public boolean secondaryCellAction(int x, int y, int z) {
        if (mover == null) {
            state = "LMB select mover first";
            return true;
        }
        plan = MoveToScenarioRoutes.plan(runtime.view(), mover, x, y, z);
        var result = MoveToScenarioCommands.start(runtime, mover, x, y, z);
        state = result.code().value();
        return true;
    }
}
