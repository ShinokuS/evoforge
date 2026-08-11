package io.github.evoforge.simulation.scenario;

import io.github.evoforge.simulation.control.core.CommandDispatcher;
import io.github.evoforge.simulation.control.sync.SynchronousCommandGateway;
import io.github.evoforge.simulation.control.terrain.PlaceTerrainCommand;
import io.github.evoforge.simulation.control.terrain.PlaceTerrainHandler;
import io.github.evoforge.simulation.definition.DefinitionRegistry;
import io.github.evoforge.simulation.result.OperationResults;
import io.github.evoforge.simulation.world.landscape.LandscapeSystem;
import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;
import io.github.evoforge.simulation.world.landscape.terrain.TerrainSystem;
import io.github.evoforge.simulation.world.landscape.terrain.storage.SparseTerrainStorage;
import io.github.evoforge.simulation.world.mechanics.geometry.GeometrySystem;
import io.github.evoforge.simulation.world.mechanics.geometry.Shape;
import io.github.evoforge.simulation.world.navigation.NavigationSystem;

public final class ScenarioBuilder {

    private final DefinitionRegistry<LandscapeDefinitionId>
            landscapeDefinitions;
    private final TerrainSystem terrain;
    private final GeometrySystem geometry;
    private final LandscapeSystem landscape;
    private final NavigationSystem navigation;

    private boolean started;

    private ScenarioBuilder() {
        landscapeDefinitions = new DefinitionRegistry<>(
                LandscapeDefinitionId::of,
                LandscapeDefinitionId::asInt);

        terrain = new TerrainSystem(
                new SparseTerrainStorage(),
                landscapeDefinitions);

        geometry = new GeometrySystem(
                terrain.lookup());

        landscape = new LandscapeSystem(
                terrain,
                geometry);

        navigation = new NavigationSystem(
                geometry.lookup());
    }

    public static ScenarioBuilder create() {
        return new ScenarioBuilder();
    }

    public LandscapeDefinitionId landscapeDefinition(
            String key) {

        requireNotStarted();
        return landscapeDefinitions.register(key);
    }

    public ScenarioBuilder placeTerrain(
            int x,
            int y,
            int z,
            LandscapeDefinitionId definitionId) {

        requireNotStarted();

        OperationResults.requireAccepted(
                landscape.placeTerrain(
                        x,
                        y,
                        z,
                        definitionId));

        return this;
    }

    public ScenarioBuilder setShape(
            int x,
            int y,
            int z,
            Shape shape) {

        requireNotStarted();
        geometry.setShape(
                x,
                y,
                z,
                shape);
        return this;
    }

    public ScenarioHarness start() {
        requireNotStarted();
        started = true;
        landscapeDefinitions.freeze();

        CommandDispatcher dispatcher =
                new CommandDispatcher();

        dispatcher.register(
                PlaceTerrainCommand.class,
                new PlaceTerrainHandler(landscape));

        return new ScenarioHarness(
                new SynchronousCommandGateway(dispatcher),
                terrain.lookup(),
                geometry.lookup(),
                navigation.lookup());
    }

    private void requireNotStarted() {
        if (started) {
            throw new IllegalStateException(
                    "scenario has already started");
        }
    }
}
