package io.github.evoforge.simulation.runtime;

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
import io.github.evoforge.simulation.world.landscape.terrain.storage.SparseTerrainStorage;
import io.github.evoforge.simulation.world.mechanics.geometry.Shape;
import io.github.evoforge.simulation.world.mechanics.movement.MovementActionProcessor;
import io.github.evoforge.simulation.world.mechanics.movement.MovementDefinitions;
import io.github.evoforge.simulation.world.mechanics.movement.MovementRate;
import io.github.evoforge.simulation.world.mechanics.movement.MovementStateStore;
import io.github.evoforge.simulation.world.mechanics.movement.MovementSystem;
import io.github.evoforge.simulation.world.mechanics.traversal.LandscapeTraversalDefinitions;
import io.github.evoforge.simulation.world.mechanics.traversal.SurfaceTraversalCost;
import io.github.evoforge.simulation.world.mechanics.traversal.TransitionCostCalculator;
import io.github.evoforge.simulation.world.navigation.NavigationSystem;
import io.github.evoforge.simulation.world.object.ObjectFactory;
import io.github.evoforge.simulation.world.object.ObjectId;
import io.github.evoforge.simulation.world.object.ObjectRepository;
import io.github.evoforge.simulation.world.object.WorldObject;
import io.github.evoforge.simulation.world.object.definition.ObjectDefinitionId;
import io.github.evoforge.simulation.world.spatial.SpatialSystem;
import io.github.evoforge.simulation.world.spatial.indexes.CellSpatialIndex;

/**
 * Production composition root for a simulation instance.
 *
 * <p>The assembly itself is the setup phase. Once {@link #start()} succeeds,
 * all setup mutation methods reject further use and the caller receives a
 * separate {@link SimulationRuntime} containing only runtime control and
 * read-only observation capabilities.</p>
 */
public final class SimulationAssembly {

    private final DefinitionRegistry<LandscapeDefinitionId>
            landscapeDefinitions;
    private final LandscapeTraversalDefinitions
            landscapeTraversalDefinitions;
    private final DefinitionRegistry<ObjectDefinitionId>
            objectDefinitions;
    private final MovementDefinitions movementDefinitions;
    private final LandscapeSystem landscape;
    private final NavigationSystem navigation;
    private final ObjectRepository objects;
    private final ObjectFactory objectFactory;
    private final CellSpatialIndex cells;
    private final SpatialSystem spatial;
    private final MovementStateStore movementState;

    private boolean started;

    private SimulationAssembly() {
        landscapeDefinitions = new DefinitionRegistry<>(
                LandscapeDefinitionId::of,
                LandscapeDefinitionId::asInt);
        landscapeTraversalDefinitions =
                new LandscapeTraversalDefinitions();

        objectDefinitions = new DefinitionRegistry<>(
                ObjectDefinitionId::of,
                ObjectDefinitionId::asInt);

        movementDefinitions = new MovementDefinitions();

        landscape = LandscapeSystem.create(
                new SparseTerrainStorage(),
                landscapeDefinitions);

        navigation = new NavigationSystem(
                landscape.geometry());

        objects = new ObjectRepository();
        objectFactory = new ObjectFactory(
                objects,
                objectDefinitions);

        cells = new CellSpatialIndex();
        spatial = new SpatialSystem(cells);
        movementState = new MovementStateStore();
    }

    public static SimulationAssembly create() {
        return new SimulationAssembly();
    }

    public LandscapeDefinitionId landscapeDefinition(
            String key) {

        return landscapeDefinition(
                key,
                SurfaceTraversalCost.NEUTRAL_UNITS);
    }

    public LandscapeDefinitionId landscapeDefinition(
            String key,
            long traversalCostUnits) {

        requireNotStarted();

        LandscapeDefinitionId definitionId =
                landscapeDefinitions.register(key);

        landscapeTraversalDefinitions.put(
                definitionId,
                SurfaceTraversalCost.of(
                        traversalCostUnits));

        return definitionId;
    }

    public ObjectDefinitionId objectDefinition(
            String key) {

        requireNotStarted();
        return objectDefinitions.register(key);
    }

    public SimulationAssembly movementRate(
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

    public SimulationAssembly placeObject(
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

    public SimulationAssembly placeTerrain(
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

    public SimulationAssembly setShape(
            int x,
            int y,
            int z,
            Shape shape) {

        requireNotStarted();

        landscape.setShape(
                x,
                y,
                z,
                shape);

        return this;
    }

    public SimulationRuntime start() {
        requireNotStarted();
        started = true;

        landscapeDefinitions.freeze();
        landscapeTraversalDefinitions.freeze();
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

        TransitionCostCalculator transitionCosts =
                new TransitionCostCalculator(
                        landscape.terrain(),
                        landscape.geometry(),
                        landscapeTraversalDefinitions);

        MovementSystem movement =
                new MovementSystem(
                        objects,
                        spatial.transforms(),
                        navigation.lookup(),
                        movementDefinitions,
                        transitionCosts,
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

        SimulationView view = new SimulationView(
                objects,
                spatial.transforms(),
                landscape.terrain(),
                landscape.terrainExtents(),
                landscape.geometry(),
                navigation.lookup(),
                cells.lookup());

        return new SimulationRuntime(
                new SynchronousCommandGateway(dispatcher),
                clock,
                stepper,
                view);
    }

    private void requireNotStarted() {
        if (started) {
            throw new IllegalStateException(
                    "simulation assembly has already started");
        }
    }
}
