package io.github.evoforge.visualizer.scenario;

import io.github.evoforge.simulation.runtime.SimulationRuntime;
import io.github.evoforge.simulation.world.mechanics.movement.MoveToCompletion;
import io.github.evoforge.simulation.world.object.ObjectId;
import io.github.evoforge.visualizer.visual.LandscapeSliceResolver;

final class MoveToInteractiveController implements ScenarioController {

    private static final int MAX_VISIBLE_DROP =
            MoveToScenarioCourse.MAX_STANDING_Z
                    - MoveToScenarioCourse.MIN_STANDING_Z;

    private final SimulationRuntime runtime;
    private final LandscapeSliceResolver slices;
    private ObjectId mover;
    private MoveToScenarioPlan plan;
    private String state = "LMB select mover";

    MoveToInteractiveController(SimulationRuntime runtime) {
        this.runtime = runtime;
        slices = new LandscapeSliceResolver(runtime.view());
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
    public boolean secondaryCellAction(int x, int y, int selectedZ) {
        if (mover == null) {
            state = "LMB select mover first";
            return true;
        }

        StandingTarget target = resolveVisibleStandingTarget(
                x,
                y,
                selectedZ);
        if (target == null) {
            state = "no walkable surface under cursor on selected slice";
            return true;
        }

        MoveToScenarioPlan candidate =
                MoveToScenarioRoutes.plan(
                        runtime.view(),
                        mover,
                        target.x(),
                        target.y(),
                        target.z());
        var result = MoveToScenarioCommands.start(
                runtime,
                mover,
                target.x(),
                target.y(),
                target.z());
        if (result.accepted()) {
            plan = candidate;
        }

        state = result.code().value();
        if (result.accepted()
                && !runtime.view().moveTo().isActive(mover)) {
            MoveToCompletion completion =
                    runtime.view().moveTo().lastCompletion(mover);
            if (completion != null
                    && completion.actionId().equals(result.actionId())) {
                state = completion.code().value();
            }
        }
        return true;
    }

    private StandingTarget resolveVisibleStandingTarget(
            int x,
            int y,
            int selectedZ) {

        LandscapeSliceResolver.Cell slice = slices.resolve(
                x,
                y,
                selectedZ,
                MAX_VISIBLE_DROP);

        return switch (slice.kind()) {
            case CURRENT_SURFACE ->
                    new StandingTarget(x, y, selectedZ);
            case LOWER_SURFACE ->
                    new StandingTarget(x, y, slice.terrainZ() + 1);
            case SOLID_BODY, EMPTY -> null;
        };
    }

    private record StandingTarget(
            int x,
            int y,
            int z) {
    }
}
