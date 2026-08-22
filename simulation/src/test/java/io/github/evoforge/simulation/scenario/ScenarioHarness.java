package io.github.evoforge.simulation.scenario;

import io.github.evoforge.simulation.kernel.command.Command;
import io.github.evoforge.simulation.kernel.command.CommandResult;
import io.github.evoforge.simulation.runtime.SimulationRuntime;
import io.github.evoforge.simulation.world.terrain.TerrainLookup;
import io.github.evoforge.simulation.world.geometry.GeometryLookup;
import io.github.evoforge.simulation.world.navigation.NavigationLookup;
import io.github.evoforge.simulation.world.object.ObjectLookup;
import io.github.evoforge.simulation.world.spatial.TransformLookup;

public final class ScenarioHarness {

    private final SimulationRuntime runtime;

    ScenarioHarness(
            SimulationRuntime runtime) {

        if (runtime == null) {
            throw new IllegalArgumentException(
                    "runtime must not be null");
        }

        this.runtime = runtime;
    }

    public <R extends CommandResult> R submit(
            Command<R> command) {
        return runtime.submit(command);
    }

    public void advance() {
        runtime.stepper().advance();
    }

    public void advanceTicks(
            int ticks) {

        if (ticks < 0) {
            throw new IllegalArgumentException(
                    "ticks must be >= 0");
        }

        for (int tick = 0; tick < ticks; tick++) {
            advance();
        }
    }

    public long tick() {
        return runtime.time().tick();
    }

    public ObjectLookup objects() {
        return runtime.view().objects();
    }

    public TransformLookup transforms() {
        return runtime.view().transforms();
    }

    public TerrainLookup terrain() {
        return runtime.view().terrain();
    }

    public GeometryLookup geometry() {
        return runtime.view().geometry();
    }

    public NavigationLookup navigation() {
        return runtime.view().navigation();
    }
}
