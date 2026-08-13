package io.github.evoforge.simulation.runtime;

import io.github.evoforge.simulation.control.core.CommandDispatcher;
import io.github.evoforge.simulation.control.movement.MoveStepCommand;
import io.github.evoforge.simulation.control.movement.MoveStepHandler;
import io.github.evoforge.simulation.control.sync.SynchronousCommandGateway;
import io.github.evoforge.simulation.control.terrain.PlaceTerrainCommand;
import io.github.evoforge.simulation.control.terrain.PlaceTerrainHandler;
import io.github.evoforge.simulation.control.terrain.ReplaceTerrainCommand;
import io.github.evoforge.simulation.control.terrain.ReplaceTerrainHandler;
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
import io.github.evoforge.simulation.world.mechanics.occupancy.OccupancyDefinitions;
import io.github.evoforge.simulation.world.mechanics.occupancy.OccupancySystem;
import io.github.evoforge.simulation.world.mechanics.traversal.LandscapeTraversalDefinitions;
import io.github.evoforge.simulation.world.mechanics.traversal.SurfaceTraversalCost;
import io.github.evoforge.simulation.world.mechanics.traversal.TransitionCostCalculator;
import io.github.evoforge.simulation.world.mechanics.traversal.TransitionCostLowerBoundCalculator;
import io.github.evoforge.simulation.world.mechanics.traversal.TransitionCostLowerBoundLookup;
import io.github.evoforge.simulation.world.navigation.NavigationSystem;
import io.github.evoforge.simulation.world.object.ObjectFactory;
import io.github.evoforge.simulation.world.object.ObjectId;
import io.github.evoforge.simulation.world.object.ObjectRepository;
import io.github.evoforge.simulation.world.object.WorldObject;
import io.github.evoforge.simulation.world.object.definition.ObjectDefinitionId;
import io.github.evoforge.simulation.world.object.placement.ObjectPlacementSystem;
import io.github.evoforge.simulation.world.pathfinding.ExactAStarPathfinder;
import io.github.evoforge.simulation.world.pathfinding.HierarchicalPathfinder;
import io.github.evoforge.simulation.world.pathfinding.PathHeuristics;
import io.github.evoforge.simulation.world.pathfinding.PathHierarchyConfig;
import io.github.evoforge.simulation.world.pathfinding.PathHierarchyIndex;
import io.github.evoforge.simulation.world.pathfinding.Pathfinder;
import io.github.evoforge.simulation.world.spatial.SpatialSystem;
import io.github.evoforge.simulation.world.spatial.indexes.CellSpatialIndex;

import java.util.HashSet;
import java.util.Set;

/** Production composition root for a simulation instance. */
public final class SimulationAssembly {

    private final DefinitionRegistry<LandscapeDefinitionId> landscapeDefinitions;
    private final LandscapeTraversalDefinitions landscapeTraversalDefinitions;
    private final DefinitionRegistry<ObjectDefinitionId> objectDefinitions;
    private final MovementDefinitions movementDefinitions;
    private final OccupancyDefinitions occupancyDefinitions;
    private final LandscapeSystem landscape;
    private final NavigationSystem navigation;
    private final ObjectRepository objects;
    private final ObjectFactory objectFactory;
    private final CellSpatialIndex cells;
    private final SpatialSystem spatial;
    private final OccupancySystem occupancy;
    private final ObjectPlacementSystem objectPlacement;
    private final MovementStateStore movementState;
    private final Set<ObjectDefinitionId> placedObjectDefinitions = new HashSet<>();
    private boolean started;

    private SimulationAssembly() {
        landscapeDefinitions = new DefinitionRegistry<>(LandscapeDefinitionId::of, LandscapeDefinitionId::asInt);
        landscapeTraversalDefinitions = new LandscapeTraversalDefinitions();
        objectDefinitions = new DefinitionRegistry<>(ObjectDefinitionId::of, ObjectDefinitionId::asInt);
        movementDefinitions = new MovementDefinitions();
        occupancyDefinitions = new OccupancyDefinitions();
        landscape = LandscapeSystem.create(new SparseTerrainStorage(), landscapeDefinitions);
        navigation = new NavigationSystem(landscape.geometry());
        objects = new ObjectRepository();
        objectFactory = new ObjectFactory(objects, objectDefinitions);
        cells = new CellSpatialIndex();
        spatial = new SpatialSystem(cells);
        occupancy = new OccupancySystem(objects, cells.lookup(), occupancyDefinitions);
        objectPlacement = new ObjectPlacementSystem(objects, occupancy, spatial);
        movementState = new MovementStateStore();
    }

    public static SimulationAssembly create() { return new SimulationAssembly(); }

    public LandscapeDefinitionId landscapeDefinition(String key) {
        return landscapeDefinition(key, SurfaceTraversalCost.NEUTRAL_UNITS);
    }

    public LandscapeDefinitionId landscapeDefinition(String key, long traversalCostUnits) {
        requireNotStarted();
        LandscapeDefinitionId definitionId = landscapeDefinitions.register(key);
        landscapeTraversalDefinitions.put(definitionId, SurfaceTraversalCost.of(traversalCostUnits));
        return definitionId;
    }

    public ObjectDefinitionId objectDefinition(String key) {
        requireNotStarted();
        return objectDefinitions.register(key);
    }

    public SimulationAssembly movementRate(ObjectDefinitionId definitionId, long unitsPerTick) {
        requireNotStarted();
        requireObjectDefinition(definitionId);
        movementDefinitions.put(definitionId, MovementRate.of(unitsPerTick));
        return this;
    }

    public SimulationAssembly exclusiveOccupancy(ObjectDefinitionId definitionId) {
        requireNotStarted();
        requireObjectDefinition(definitionId);
        if (placedObjectDefinitions.contains(definitionId)) {
            throw new IllegalStateException(
                    "exclusive occupancy must be configured before placing instances of definition: " + definitionId);
        }
        occupancyDefinitions.put(definitionId, true);
        return this;
    }

    public ObjectId createObject(ObjectDefinitionId definitionId) {
        requireNotStarted();
        WorldObject object = objectFactory.create(definitionId);
        return object.id();
    }

    public SimulationAssembly placeObject(ObjectId objectId, int x, int y, int z) {
        requireNotStarted();
        OperationResults.requireAccepted(objectPlacement.place(objectId, x, y, z));
        WorldObject object = objects.get(objectId);
        if (object == null) {
            throw new IllegalStateException("placed object disappeared from repository: " + objectId);
        }
        placedObjectDefinitions.add(object.definitionId());
        return this;
    }

    public SimulationAssembly placeTerrain(
            int x, int y, int z, LandscapeDefinitionId definitionId) {
        requireNotStarted();
        OperationResults.requireAccepted(landscape.placeTerrain(x, y, z, definitionId));
        return this;
    }

    public SimulationAssembly setShape(int x, int y, int z, Shape shape) {
        requireNotStarted();
        landscape.setShape(x, y, z, shape);
        return this;
    }

    public SimulationRuntime start() {
        requireNotStarted();
        started = true;
        landscapeDefinitions.freeze();
        landscapeTraversalDefinitions.freeze();
        objectDefinitions.freeze();
        movementDefinitions.freeze();
        occupancyDefinitions.freeze();

        HandlerRegistry scheduledHandlers = new HandlerRegistry();
        Scheduler scheduler = new Scheduler(scheduledHandlers);
        SimulationClock clock = new SimulationClock();
        SimulationStepper stepper = new SimulationStepper(clock, scheduler);

        MovementActionProcessor movementActions = new MovementActionProcessor(
                movementState, objects, spatial.transforms(), navigation.lookup(), occupancy, spatial);
        HandlerId movementHandlerId = scheduledHandlers.register(movementActions::complete);
        ProcessScheduler movementScheduler = new BoundProcessScheduler(
                clock, scheduler, movementHandlerId);

        TransitionCostCalculator transitionCosts = new TransitionCostCalculator(
                landscape.terrain(), landscape.geometry(), landscapeTraversalDefinitions);
        TransitionCostLowerBoundLookup transitionCostBounds = new TransitionCostLowerBoundCalculator(
                landscapeTraversalDefinitions, landscape.shapeTraversalBounds());

        ExactAStarPathfinder exactPathfinder = new ExactAStarPathfinder(
                navigation.lookup(),
                transitionCosts,
                landscape.traversalRevision(),
                PathHeuristics.chebyshev(transitionCostBounds));
        PathHierarchyIndex hierarchy = new PathHierarchyIndex(
                navigation.lookup(),
                landscape.traversalChanges(),
                PathHierarchyConfig.standard());
        Pathfinder pathfinder = new HierarchicalPathfinder(hierarchy, exactPathfinder);

        MovementSystem movement = new MovementSystem(
                objects,
                spatial.transforms(),
                navigation.lookup(),
                movementDefinitions,
                transitionCosts,
                occupancy,
                movementState,
                movementScheduler);

        CommandDispatcher dispatcher = new CommandDispatcher();
        dispatcher.register(PlaceTerrainCommand.class, new PlaceTerrainHandler(landscape));
        dispatcher.register(ReplaceTerrainCommand.class, new ReplaceTerrainHandler(landscape));
        dispatcher.register(MoveStepCommand.class, new MoveStepHandler(movement));

        SimulationView view = new SimulationView(
                objects,
                spatial.transforms(),
                landscape.terrain(),
                landscape.terrainExtents(),
                landscape.terrainRevision(),
                landscape.geometry(),
                navigation.lookup(),
                occupancy,
                cells.lookup(),
                pathfinder);

        return new SimulationRuntime(
                new SynchronousCommandGateway(dispatcher), clock, stepper, view);
    }

    private void requireObjectDefinition(ObjectDefinitionId definitionId) {
        if (!objectDefinitions.contains(definitionId)) {
            throw new IllegalArgumentException("unknown object definition: " + definitionId);
        }
    }

    private void requireNotStarted() {
        if (started) {
            throw new IllegalStateException("simulation assembly has already started");
        }
    }
}
