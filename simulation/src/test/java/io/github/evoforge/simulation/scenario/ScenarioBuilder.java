package io.github.evoforge.simulation.scenario;

import io.github.evoforge.simulation.control.core.CommandDispatcher;
import io.github.evoforge.simulation.control.movement.MoveStepCommand;
import io.github.evoforge.simulation.control.movement.MoveStepHandler;
import io.github.evoforge.simulation.control.sync.SynchronousCommandGateway;
import io.github.evoforge.simulation.control.terrain.PlaceTerrainCommand;
import io.github.evoforge.simulation.control.terrain.PlaceTerrainHandler;
import io.github.evoforge.simulation.definition.DefinitionRegistry;
import io.github.evoforge.simulation.result.OperationResults;
import io.github.evoforge.simulation.time.BoundProcessScheduler;
import io.github.evoforge.simulation.time.HandlerId;
import io.github.evoforge.simulation.time.HandlerRegistry;
import io.github.evoforge.simulation.time.ProcessScheduler;
import io.github.evoforge.simulation.time.Scheduler;
import io.github.evoforge.simulation.time.SimulationClock;
import io.github.evoforge.simulation.time.SimulationStepper;
import io.github.evoforge.simulation.world.landscape.LandscapeSystem;
import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;
import io.github.evoforge.simulation.world.landscape.terrain.TerrainSystem;
import io.github.evoforge.simulation.world.landscape.terrain.storage.SparseTerrainStorage;
import io.github.evoforge.simulation.world.mechanics.geometry.GeometrySystem;
import io.github.evoforge.simulation.world.mechanics.geometry.Shape;
import io.github.evoforge.simulation.world.mechanics.movement.MovementActionProcessor;
import io.github.evoforge.simulation.world.mechanics.movement.MovementDefinitions;
import io.github.evoforge.simulation.world.mechanics.movement.MovementRate;
import io.github.evoforge.simulation.world.mechanics.movement.MovementStateStore;
import io.github.evoforge.simulation.world.mechanics.movement.MovementSystem;
import io.github.evoforge.simulation.world.navigation.NavigationSystem;
import io.github.evoforge.simulation.world.object.ObjectFactory;
import io.github.evoforge.simulation.world.object.ObjectId;
import io.github.evoforge.simulation.world.object.ObjectRepository;
import io.github.evoforge.simulation.world.object.WorldObject;
import io.github.evoforge.simulation.world.object.definition.ObjectDefinitionId;
import io.github.evoforge.simulation.world.spatial.SpatialSystem;

public final class ScenarioBuilder {

    private final DefinitionRegistry<LandscapeDefinitionId>
            landscapeDefinitions;
    private final DefinitionRegistry<ObjectDefinitionId>
            objectDefinitions;
    private final MovementDefinitions movementDefinitions;
    private final TerrainSystem terrain;
    private final GeometrySystem geometry;
    private final LandscapeSystem landscape;
    private final NavigationSystem navigation;
    private final ObjectRepository objects;
    private final ObjectFactory objectFactory;
    private final SpatialSystem spatial;
    private final MovementStateStore movementState;

    private boolean started;

    private ScenarioBuilder() {
        landscapeDefinitions = new DefinitionRegistry<>(
                LandscapeDefinitionId::of,
                LandscapeDefinitionId::asInt);

        objectDefinitions = new DefinitionRegistry<>(
                ObjectDefinitionId::of,
                ObjectDefinitionId::asInt);

        movementDefinitions = new MovementDefinitions();

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

        objects = new ObjectRepository();
        objectFactory = new ObjectFactory(
                objects,
                objectDefinitions);
        spatial = new SpatialSystem();
        movementState = new MovementStateStore();
    }

    public static ScenarioBuilder create() {
        return new ScenarioBuilder();
    }

    public LandscapeDefinitionId landscapeDefinition(
            String key) {

        requireNotStarted();
        return landscapeDefinitions.register(key);
    }

    public ObjectDefinitionId objectDefinition(
            String key) {

        requireNotStarted();
        return objectDefinitions.register(key);
    }

    public ScenarioBuilder movementRate(
            ObjectDefinitionId definitionId,
            long unitsPerTick) {

        requireNotStarted();

        if (!objectDefinitions.contains(definitionId)) {
            throw new IllegalArgumentException(
                    "unknown object definition: " + definitionId);
        }

        movementDefinitions.put(
                definitionId,
                MovementRate.of(unitsPerTick));

        return this;
    }

    public ObjectId createObject(
            ObjectDefinitionId definitionId) {

        requireNotStarted();

        WorldObject object = objectFactory.create(
                definitionId);

        return object.id();
    }

    public ScenarioBuilder placeObject(
            ObjectId objectId,
            int x,
            int y,
            int z) {

        requireNotStarted();

        if (!objects.isAlive(objectId)) {
            throw new IllegalArgumentException(
                    "unknown object: " + objectId);
        }

        spatial.place(
                objectId,
                x,
                y,
                z);

        return this;
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
        objectDefinitions.freeze();
        movementDefinitions.freeze();

        HandlerRegistry scheduledHandlers =
                new HandlerRegistry();
        Scheduler scheduler =
                new Scheduler(scheduledHandlers);
        SimulationClock clock =
                new SimulationClock();
        SimulationStepper stepper =
                new SimulationStepper(
                        clock,
                        scheduler);

        MovementActionProcessor movementActions =
                new MovementActionProcessor(
                        movementState,
                        objects,
                        spatial.transforms(),
                        navigation.lookup(),
                        spatial);

        HandlerId movementHandlerId =
                scheduledHandlers.register(
                        movementActions::complete);

        ProcessScheduler movementScheduler =
                new BoundProcessScheduler(
                        clock,
                        scheduler,
                        movementHandlerId);

        MovementSystem movement =
                new MovementSystem(
                        objects,
                        spatial.transforms(),
                        navigation.lookup(),
                        movementDefinitions,
                        movementState,
                        movementScheduler);

        CommandDispatcher dispatcher =
                new CommandDispatcher();

        dispatcher.register(
                PlaceTerrainCommand.class,
                new PlaceTerrainHandler(landscape));

        dispatcher.register(
                MoveStepCommand.class,
                new MoveStepHandler(movement));

        return new ScenarioHarness(
                new SynchronousCommandGateway(dispatcher),
                clock,
                stepper,
                objects,
                spatial.transforms(),
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
