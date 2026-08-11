package io.github.evoforge.simulation.scenario;

import io.github.evoforge.simulation.control.core.Command;
import io.github.evoforge.simulation.control.core.CommandResult;
import io.github.evoforge.simulation.control.sync.SynchronousCommandGateway;
import io.github.evoforge.simulation.time.SimulationStepper;
import io.github.evoforge.simulation.time.SimulationTime;
import io.github.evoforge.simulation.world.landscape.terrain.TerrainLookup;
import io.github.evoforge.simulation.world.mechanics.geometry.GeometryLookup;
import io.github.evoforge.simulation.world.navigation.NavigationLookup;
import io.github.evoforge.simulation.world.object.ObjectLookup;
import io.github.evoforge.simulation.world.spatial.TransformLookup;

public final class ScenarioHarness {

    private final SynchronousCommandGateway commands;
    private final SimulationTime time;
    private final SimulationStepper stepper;
    private final ObjectLookup objects;
    private final TransformLookup transforms;
    private final TerrainLookup terrain;
    private final GeometryLookup geometry;
    private final NavigationLookup navigation;

    ScenarioHarness(
            SynchronousCommandGateway commands,
            SimulationTime time,
            SimulationStepper stepper,
            ObjectLookup objects,
            TransformLookup transforms,
            TerrainLookup terrain,
            GeometryLookup geometry,
            NavigationLookup navigation) {

        if (commands == null) {
            throw new IllegalArgumentException(
                    "commands must not be null");
        }
        if (time == null) {
            throw new IllegalArgumentException(
                    "time must not be null");
        }
        if (stepper == null) {
            throw new IllegalArgumentException(
                    "stepper must not be null");
        }
        if (objects == null) {
            throw new IllegalArgumentException(
                    "objects must not be null");
        }
        if (transforms == null) {
            throw new IllegalArgumentException(
                    "transforms must not be null");
        }
        if (terrain == null) {
            throw new IllegalArgumentException(
                    "terrain must not be null");
        }
        if (geometry == null) {
            throw new IllegalArgumentException(
                    "geometry must not be null");
        }
        if (navigation == null) {
            throw new IllegalArgumentException(
                    "navigation must not be null");
        }

        this.commands = commands;
        this.time = time;
        this.stepper = stepper;
        this.objects = objects;
        this.transforms = transforms;
        this.terrain = terrain;
        this.geometry = geometry;
        this.navigation = navigation;
    }

    public <R extends CommandResult> R submit(
            Command<R> command) {
        return commands.submit(command);
    }

    public void advance() {
        stepper.advance();
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
        return time.tick();
    }

    public ObjectLookup objects() {
        return objects;
    }

    public TransformLookup transforms() {
        return transforms;
    }

    public TerrainLookup terrain() {
        return terrain;
    }

    public GeometryLookup geometry() {
        return geometry;
    }

    public NavigationLookup navigation() {
        return navigation;
    }
}
