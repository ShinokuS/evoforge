package io.github.evoforge.simulation.scenario;

import io.github.evoforge.simulation.control.core.Command;
import io.github.evoforge.simulation.control.core.CommandResult;
import io.github.evoforge.simulation.control.sync.SynchronousCommandGateway;
import io.github.evoforge.simulation.world.landscape.terrain.TerrainLookup;
import io.github.evoforge.simulation.world.mechanics.geometry.GeometryLookup;
import io.github.evoforge.simulation.world.navigation.NavigationLookup;

public final class ScenarioHarness {

    private final SynchronousCommandGateway commands;
    private final TerrainLookup terrain;
    private final GeometryLookup geometry;
    private final NavigationLookup navigation;

    ScenarioHarness(
            SynchronousCommandGateway commands,
            TerrainLookup terrain,
            GeometryLookup geometry,
            NavigationLookup navigation) {

        if (commands == null) {
            throw new IllegalArgumentException(
                    "commands must not be null");
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
        this.terrain = terrain;
        this.geometry = geometry;
        this.navigation = navigation;
    }

    public <R extends CommandResult> R submit(
            Command<R> command) {
        return commands.submit(command);
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
