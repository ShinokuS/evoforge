package io.github.evoforge.simulation.runtime;

import io.github.evoforge.simulation.control.core.CommandDispatcher;
import io.github.evoforge.simulation.control.movement.MoveStepCommand;
import io.github.evoforge.simulation.control.movement.MoveStepHandler;
import io.github.evoforge.simulation.control.movement.MoveToCommand;
import io.github.evoforge.simulation.control.movement.MoveToHandler;
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
import io.github.evoforge.simulation.world.agent.AgentDefinition;
import io.github.evoforge.simulation.world.agent.AgentDefinitions;
import io.github.evoforge.simulation.world.agent.CapabilityId;
import io.github.evoforge.simulation.world.agent.affordance.NeedSatisfaction;
import io.github.evoforge.simulation.world.agent.affordance.NeedSatisfactionDefinitions;
import io.github.evoforge.simulation.world.agent.affordance.NeedSatisfactionOpportunityProvider;
import io.github.evoforge.simulation.world.agent.decision.AgentSystem;
import io.github.evoforge.simulation.world.agent.knowledge.need.NeedSolutionKnowledgeDefinitions;
import io.github.evoforge.simulation.world.agent.need.NeedDefinitions;
import io.github.evoforge.simulation.world.agent.need.NeedId;
import io.github.evoforge.simulation.world.agent.need.NeedSpec;
import io.github.evoforge.simulation.world.agent.need.NeedSystem;
import io.github.evoforge.simulation.world.agent.need.motivation.NeedMotivationDefinition;
import io.github.evoforge.simulation.world.agent.need.motivation.NeedMotivationDefinitions;
import io.github.evoforge.simulation.world.agent.need.progression.IntrinsicNeedProgressionRateResolver;
import io.github.evoforge.simulation.world.agent.need.progression.NeedProgressionDefinition;
import io.github.evoforge.simulation.world.agent.need.progression.NeedProgressionDefinitions;
import io.github.evoforge.simulation.world.agent.need.progression.NeedProgressionSystem;
import io.github.evoforge.simulation.world.agent.opportunity.AgentOpportunityProvider;
import io.github.evoforge.simulation.world.agent.perception.vision.TerrainSightOcclusionLookup;
import io.github.evoforge.simulation.world.agent.perception.vision.VisionDefinition;
import io.github.evoforge.simulation.world.agent.perception.vision.VisionDefinitions;
import io.github.evoforge.simulation.world.agent.perception.vision.VisionSystem;
import io.github.evoforge.simulation.world.agent.search.AgentSearchSystem;
import io.github.evoforge.simulation.world.agent.search.CorrelatedRandomWalkExplorationPolicy;
import io.github.evoforge.simulation.world.agent.search.RelativeSearchLocomotion;
import io.github.evoforge.simulation.world.environment.evaporation.EvaporationSchedule;
import io.github.evoforge.simulation.world.environment.evaporation.EvaporationSystem;
import io.github.evoforge.simulation.world.environment.evaporation.PeriodicEvaporationSystem;
import io.github.evoforge.simulation.world.environment.precipitation.PeriodicPrecipitationSystem;
import io.github.evoforge.simulation.world.environment.precipitation.PrecipitationEventLookup;
import io.github.evoforge.simulation.world.environment.precipitation.PrecipitationSchedule;
import io.github.evoforge.simulation.world.environment.precipitation.PrecipitationSystem;
import io.github.evoforge.simulation.world.environment.precipitation.SkyPrecipitationSystem;
import io.github.evoforge.simulation.world.environment.sky.VerticalSkySurfaceSystem;
import io.github.evoforge.simulation.world.landscape.LandscapeSystem;
import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;
import io.github.evoforge.simulation.world.landscape.soil.SoilHydrology;
import io.github.evoforge.simulation.world.landscape.soil.SoilHydrologyDefinitions;
import io.github.evoforge.simulation.world.landscape.soil.SoilHydrologyVariation;
import io.github.evoforge.simulation.world.landscape.soil.SoilHydrologyVariationDefinitions;
import io.github.evoforge.simulation.world.landscape.soil.SoilMoistureSystem;
import io.github.evoforge.simulation.world.landscape.soil.TerrainSoilHydrologyLookup;
import io.github.evoforge.simulation.world.landscape.soil.storage.SparseSoilMoistureStorage;
import io.github.evoforge.simulation.world.landscape.terrain.storage.SparseTerrainStorage;
import io.github.evoforge.simulation.world.landscape.water.SurfaceWaterStorageDefinitions;
import io.github.evoforge.simulation.world.landscape.water.SurfaceWaterStorageLookup;
import io.github.evoforge.simulation.world.landscape.water.TerrainSurfaceWaterStorageLookup;
import io.github.evoforge.simulation.world.landscape.water.WaterFlowProcess;
import io.github.evoforge.simulation.world.landscape.water.WaterFlowSystem;
import io.github.evoforge.simulation.world.landscape.water.WaterSoilExchangeSystem;
import io.github.evoforge.simulation.world.landscape.water.WaterSystem;
import io.github.evoforge.simulation.world.landscape.water.storage.SparseWaterStorage;
import io.github.evoforge.simulation.world.mechanics.consumption.ConsumableStockDefinition;
import io.github.evoforge.simulation.world.mechanics.consumption.ConsumableStockDefinitions;
import io.github.evoforge.simulation.world.mechanics.consumption.ConsumableStockReductionRelay;
import io.github.evoforge.simulation.world.mechanics.consumption.ConsumableStockSystem;
import io.github.evoforge.simulation.world.mechanics.geometry.Shape;
import io.github.evoforge.simulation.world.mechanics.geometry.WorldGeometryLookup;
import io.github.evoforge.simulation.world.mechanics.growth.GrowthDefinition;
import io.github.evoforge.simulation.world.mechanics.growth.GrowthDefinitions;
import io.github.evoforge.simulation.world.mechanics.growth.GrowthSystem;
import io.github.evoforge.simulation.world.mechanics.growth.IntrinsicGrowthRateResolver;
import io.github.evoforge.simulation.world.mechanics.movement.MoveToSystem;
import io.github.evoforge.simulation.world.mechanics.movement.MovementActionProcessor;
import io.github.evoforge.simulation.world.mechanics.movement.MovementDefinitions;
import io.github.evoforge.simulation.world.mechanics.movement.MovementRate;
import io.github.evoforge.simulation.world.mechanics.movement.MovementStateStore;
import io.github.evoforge.simulation.world.mechanics.movement.MovementStepCompletionRelay;
import io.github.evoforge.simulation.world.mechanics.movement.MovementSystem;
import io.github.evoforge.simulation.world.mechanics.occupancy.OccupancyDefinitions;
import io.github.evoforge.simulation.world.mechanics.occupancy.OccupancySystem;
import io.github.evoforge.simulation.world.mechanics.traversal.LandscapeTraversalDefinitions;
import io.github.evoforge.simulation.world.mechanics.traversal.MoverTraversalQueryConstraintProvider;
import io.github.evoforge.simulation.world.mechanics.traversal.SurfaceTraversalCost;
import io.github.evoforge.simulation.world.mechanics.traversal.TransitionCostCalculator;
import io.github.evoforge.simulation.world.mechanics.traversal.TransitionCostLowerBoundCalculator;
import io.github.evoforge.simulation.world.mechanics.traversal.TransitionCostLowerBoundLookup;
import io.github.evoforge.simulation.world.mechanics.traversal.water.WaterWadingConstraint;
import io.github.evoforge.simulation.world.mechanics.traversal.water.WaterWadingDefinitions;
import io.github.evoforge.simulation.world.mechanics.traversal.water.WaterWadingProfile;
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
import io.github.evoforge.simulation.world.spatial.WorldBounds;
import io.github.evoforge.simulation.world.spatial.indexes.CellSpatialIndex;
import io.github.evoforge.simulation.world.spatial.orientation.FacingDirection;
import io.github.evoforge.simulation.world.spatial.orientation.OrientationSystem;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Production composition root for a simulation instance. */
public final class SimulationAssembly {
    private final DefinitionRegistry<LandscapeDefinitionId> landscapeDefinitions;
    private final LandscapeTraversalDefinitions landscapeTraversalDefinitions;
    private final SoilHydrologyDefinitions soilHydrologyDefinitions;
    private final SoilHydrologyVariationDefinitions soilHydrologyVariationDefinitions;
    private final SurfaceWaterStorageDefinitions surfaceWaterStorageDefinitions;
    private final DefinitionRegistry<ObjectDefinitionId> objectDefinitions;
    private final MovementDefinitions movementDefinitions;
    private final WaterWadingDefinitions waterWadingDefinitions;
    private final OccupancyDefinitions occupancyDefinitions;
    private final AgentDefinitions agentDefinitions;
    private final VisionDefinitions visionDefinitions;
    private final NeedDefinitions needDefinitions;
    private final NeedMotivationDefinitions needMotivationDefinitions;
    private final NeedProgressionDefinitions needProgressionDefinitions;
    private final NeedSatisfactionDefinitions needSatisfactionDefinitions;
    private final NeedSolutionKnowledgeDefinitions needSolutionKnowledgeDefinitions;
    private final ConsumableStockDefinitions consumableStockDefinitions;
    private final GrowthDefinitions growthDefinitions;
    private final LandscapeSystem landscape;
    private final WorldGeometryLookup worldGeometry;
    private final SoilMoistureSystem soilMoisture;
    private final WaterSystem water;
    private final NavigationSystem navigation;
    private final ObjectRepository objects;
    private final ObjectFactory objectFactory;
    private final CellSpatialIndex cells;
    private final SpatialSystem spatial;
    private final OrientationSystem orientations;
    private final OccupancySystem occupancy;
    private final ObjectPlacementSystem objectPlacement;
    private final MovementStateStore movementState;
    private final Set<ObjectDefinitionId> placedObjectDefinitions = new HashSet<>();
    private final List<ObjectId> createdObjects = new ArrayList<>();
    private final Map<ObjectId, FacingDirection> initialFacing = new HashMap<>();
    private PrecipitationSchedule precipitationSchedule;
    private EvaporationSchedule evaporationSchedule;
    private boolean started;

    private SimulationAssembly() {
        landscapeDefinitions = new DefinitionRegistry<>(LandscapeDefinitionId::of, LandscapeDefinitionId::asInt);
        landscapeTraversalDefinitions = new LandscapeTraversalDefinitions();
        soilHydrologyDefinitions = new SoilHydrologyDefinitions();
        soilHydrologyVariationDefinitions = new SoilHydrologyVariationDefinitions();
        surfaceWaterStorageDefinitions = new SurfaceWaterStorageDefinitions();
        objectDefinitions = new DefinitionRegistry<>(ObjectDefinitionId::of, ObjectDefinitionId::asInt);
        movementDefinitions = new MovementDefinitions();
        waterWadingDefinitions = new WaterWadingDefinitions();
        occupancyDefinitions = new OccupancyDefinitions();
        agentDefinitions = new AgentDefinitions();
        visionDefinitions = new VisionDefinitions();
        needDefinitions = new NeedDefinitions();
        needMotivationDefinitions = new NeedMotivationDefinitions();
        needProgressionDefinitions = new NeedProgressionDefinitions();
        needSatisfactionDefinitions = new NeedSatisfactionDefinitions();
        needSolutionKnowledgeDefinitions = new NeedSolutionKnowledgeDefinitions();
        consumableStockDefinitions = new ConsumableStockDefinitions();
        growthDefinitions = new GrowthDefinitions();
        landscape = LandscapeSystem.create(new SparseTerrainStorage(), landscapeDefinitions);
        worldGeometry = new WorldGeometryLookup(landscape.geometry());
        soilMoisture = new SoilMoistureSystem(
                new SparseSoilMoistureStorage(),
                new TerrainSoilHydrologyLookup(
                        landscape.terrain(),
                        soilHydrologyDefinitions,
                        soilHydrologyVariationDefinitions));
        water = new WaterSystem(
                new SparseWaterStorage(),
                worldGeometry);
        navigation = new NavigationSystem(worldGeometry);
        objects = new ObjectRepository();
        objectFactory = new ObjectFactory(objects, objectDefinitions);
        cells = new CellSpatialIndex();
        spatial = new SpatialSystem(cells);
        orientations = new OrientationSystem(objects);
        occupancy = new OccupancySystem(objects, cells.lookup(), occupancyDefinitions);
        objectPlacement = new ObjectPlacementSystem(objects, occupancy, spatial);
        movementState = new MovementStateStore();
    }

    public static SimulationAssembly create() { return new SimulationAssembly(); }

    /**
     * Closes the simulation inside one inclusive integer box. Outside coordinates
     * become physically solid to Geometry consumers while unconfigured assemblies
     * preserve the historical unbounded-world behavior.
     */
    public SimulationAssembly worldBounds(
            int minX,
            int maxX,
            int minY,
            int maxY,
            int minZ,
            int maxZ) {
        requireNotStarted();
        worldGeometry.configureBounds(
                new WorldBounds(minX, maxX, minY, maxY, minZ, maxZ));
        return this;
    }

    public LandscapeDefinitionId landscapeDefinition(String key) {
        return landscapeDefinition(key, SurfaceTraversalCost.NEUTRAL_UNITS);
    }

    public LandscapeDefinitionId landscapeDefinition(String key, long traversalCostUnits) {
        requireNotStarted();
        LandscapeDefinitionId definitionId = landscapeDefinitions.register(key);
        landscapeTraversalDefinitions.put(definitionId, SurfaceTraversalCost.of(traversalCostUnits));
        return definitionId;
    }

    public SimulationAssembly soilHydrology(
            LandscapeDefinitionId definitionId,
            int capacity,
            int infiltrationLimit) {
        requireNotStarted();
        requireLandscapeDefinition(definitionId);
        soilHydrologyDefinitions.put(
                definitionId,
                new SoilHydrology(capacity, infiltrationLimit));
        return this;
    }

    /** Adds deterministic coordinate-local variation to a material Soil capacity. */
    public SimulationAssembly soilHydrologyVariation(
            LandscapeDefinitionId definitionId,
            long seed,
            int capacityAmplitude) {
        requireNotStarted();
        requireLandscapeDefinition(definitionId);
        soilHydrologyVariationDefinitions.put(
                definitionId,
                new SoilHydrologyVariation(seed, capacityAmplitude));
        return this;
    }

    /** Declares how much free Water a material surface retains before horizontal runoff. */
    public SimulationAssembly surfaceWaterStorage(
            LandscapeDefinitionId definitionId,
            int capacity) {
        requireNotStarted();
        requireLandscapeDefinition(definitionId);
        surfaceWaterStorageDefinitions.put(
                definitionId,
                capacity);
        return this;
    }

    public SimulationAssembly periodicPrecipitation(
            int amountPerColumn,
            long intervalTicks) {
        requireNotStarted();
        precipitationSchedule = new PrecipitationSchedule(
                amountPerColumn,
                intervalTicks);
        return this;
    }

    public SimulationAssembly periodicEvaporation(
            int amountPerColumn,
            long intervalTicks) {
        requireNotStarted();
        evaporationSchedule = new EvaporationSchedule(
                amountPerColumn,
                intervalTicks);
        return this;
    }

    public ObjectDefinitionId objectDefinition(String key) {
        requireNotStarted();
        return objectDefinitions.register(key);
    }

    public SimulationAssembly movementRate(ObjectDefinitionId definitionId, long unitsPerTick) {
        requireNotStarted(); requireObjectDefinition(definitionId);
        movementDefinitions.put(definitionId, MovementRate.of(unitsPerTick));
        return this;
    }

    public SimulationAssembly waterWading(
            ObjectDefinitionId definitionId,
            int maxDepth) {
        requireNotStarted();
        requireObjectDefinition(definitionId);
        waterWadingDefinitions.put(
                definitionId,
                new WaterWadingProfile(maxDepth));
        return this;
    }

    public SimulationAssembly exclusiveOccupancy(ObjectDefinitionId definitionId) {
        requireNotStarted(); requireObjectDefinition(definitionId);
        if (placedObjectDefinitions.contains(definitionId)) {
            throw new IllegalStateException(
                    "exclusive occupancy must be configured before placing instances of definition: " + definitionId);
        }
        occupancyDefinitions.put(definitionId, true);
        return this;
    }

    public SimulationAssembly agent(ObjectDefinitionId definitionId, CapabilityId... capabilities) {
        requireNotStarted(); requireObjectDefinition(definitionId);
        agentDefinitions.put(definitionId, new AgentDefinition(capabilities));
        return this;
    }

    public SimulationAssembly vision(ObjectDefinitionId definitionId, int range, int horizontalFovDegrees) {
        requireNotStarted(); requireObjectDefinition(definitionId);
        visionDefinitions.put(definitionId, new VisionDefinition(range, horizontalFovDegrees));
        return this;
    }

    public SimulationAssembly initialFacing(ObjectId objectId, int dx, int dy) {
        requireNotStarted();
        if (!objects.isAlive(objectId)) throw new IllegalArgumentException("object must be alive: " + objectId);
        initialFacing.put(objectId, FacingDirection.of(dx, dy));
        return this;
    }

    public SimulationAssembly need(
            ObjectDefinitionId definitionId,
            NeedId needId,
            long maxLevel,
            long initialLevel) {
        requireNotStarted(); requireObjectDefinition(definitionId);
        needDefinitions.add(definitionId, new NeedSpec(needId, maxLevel, initialLevel));
        return this;
    }

    /** Declares when a Need becomes strong enough to generate autonomous environmental action. */
    public SimulationAssembly needMotivation(
            ObjectDefinitionId definitionId,
            NeedId needId,
            long activationLevel) {
        requireNotStarted(); requireObjectDefinition(definitionId);
        needMotivationDefinitions.add(
                definitionId,
                new NeedMotivationDefinition(needId, activationLevel));
        return this;
    }

    public SimulationAssembly needProgression(
            ObjectDefinitionId definitionId,
            NeedId needId,
            long baseAmount,
            long intervalTicks) {
        requireNotStarted(); requireObjectDefinition(definitionId);
        needProgressionDefinitions.add(
                definitionId,
                new NeedProgressionDefinition(needId, baseAmount, intervalTicks));
        return this;
    }

    /** Declares general knowledge that this agent definition knows the need has environmental solutions. */
    public SimulationAssembly knowsNeedSolution(ObjectDefinitionId definitionId, NeedId needId) {
        requireNotStarted(); requireObjectDefinition(definitionId);
        needSolutionKnowledgeDefinitions.add(definitionId, needId);
        return this;
    }

    public SimulationAssembly consumableStock(
            ObjectDefinitionId definitionId,
            long capacity,
            long initialQuantity) {
        requireNotStarted(); requireObjectDefinition(definitionId);
        consumableStockDefinitions.put(
                definitionId,
                new ConsumableStockDefinition(capacity, initialQuantity));
        return this;
    }

    public SimulationAssembly growth(
            ObjectDefinitionId definitionId,
            long baseAmount,
            long intervalTicks) {
        requireNotStarted(); requireObjectDefinition(definitionId);
        growthDefinitions.put(definitionId, new GrowthDefinition(baseAmount, intervalTicks));
        return this;
    }

    public SimulationAssembly satisfiesNeed(
            ObjectDefinitionId sourceDefinitionId,
            NeedId needId,
            long amount,
            CapabilityId requiredCapability) {
        return satisfiesNeed(sourceDefinitionId, needId, amount, 0L, 0L, requiredCapability);
    }

    public SimulationAssembly satisfiesNeed(
            ObjectDefinitionId sourceDefinitionId,
            NeedId needId,
            long amount,
            long consumedQuantity,
            CapabilityId requiredCapability) {
        return satisfiesNeed(sourceDefinitionId, needId, amount, consumedQuantity, 0L, requiredCapability);
    }

    public SimulationAssembly satisfiesNeed(
            ObjectDefinitionId sourceDefinitionId,
            NeedId needId,
            long amount,
            long consumedQuantity,
            long useDurationTicks,
            CapabilityId requiredCapability) {
        requireNotStarted(); requireObjectDefinition(sourceDefinitionId);
        needSatisfactionDefinitions.add(
                sourceDefinitionId,
                new NeedSatisfaction(
                        needId,
                        amount,
                        consumedQuantity,
                        useDurationTicks,
                        requiredCapability));
        return this;
    }

    public ObjectId createObject(ObjectDefinitionId definitionId) {
        requireNotStarted();
        WorldObject object = objectFactory.create(definitionId);
        createdObjects.add(object.id());
        return object.id();
    }

    public SimulationAssembly placeObject(ObjectId objectId, int x, int y, int z) {
        requireNotStarted();
        requireInsideWorld(x, y, z);
        OperationResults.requireAccepted(objectPlacement.place(objectId, x, y, z));
        WorldObject object = objects.get(objectId);
        if (object == null) throw new IllegalStateException("placed object disappeared from repository: " + objectId);
        placedObjectDefinitions.add(object.definitionId());
        return this;
    }

    public SimulationAssembly placeTerrain(int x, int y, int z, LandscapeDefinitionId definitionId) {
        requireNotStarted();
        requireInsideWorld(x, y, z);
        OperationResults.requireAccepted(landscape.placeTerrain(x, y, z, definitionId));
        return this;
    }

    public SimulationAssembly setShape(int x, int y, int z, Shape shape) {
        requireNotStarted();
        requireInsideWorld(x, y, z);
        landscape.setShape(x, y, z, shape);
        return this;
    }

    /** Adds exact finite Water during setup without creating a precipitation source. */
    public SimulationAssembly initialWater(
            int x,
            int y,
            int z,
            int amount) {
        requireNotStarted();
        requireInsideWorld(x, y, z);
        int added = water.addAtMost(x, y, z, amount);
        if (added != amount) {
            if (added > 0) {
                water.removeAtMost(x, y, z, added);
            }
            throw new IllegalArgumentException(
                    "initial Water does not fit cell geometry: requested="
                            + amount + ", accepted=" + added);
        }
        return this;
    }

    public SimulationRuntime start() {
        requireNotStarted();
        started = true;
        landscapeDefinitions.freeze();
        landscapeTraversalDefinitions.freeze();
        soilHydrologyDefinitions.freeze();
        soilHydrologyVariationDefinitions.freeze();
        surfaceWaterStorageDefinitions.freeze();
        objectDefinitions.freeze();
        movementDefinitions.freeze();
        waterWadingDefinitions.freeze();
        occupancyDefinitions.freeze();
        agentDefinitions.freeze();
        visionDefinitions.freeze();
        needDefinitions.freeze();
        needMotivationDefinitions.freeze();
        needProgressionDefinitions.freeze();
        needSatisfactionDefinitions.freeze();
        needSolutionKnowledgeDefinitions.freeze();
        consumableStockDefinitions.freeze();
        growthDefinitions.freeze();

        for (ObjectId objectId : createdObjects) {
            WorldObject object = objects.get(objectId);
            if (object != null && (visionDefinitions.has(object.definitionId()) || initialFacing.containsKey(objectId))) {
                orientations.attach(objectId, initialFacing.getOrDefault(objectId, FacingDirection.EAST));
            }
        }

        HandlerRegistry scheduledHandlers = new HandlerRegistry();
        Scheduler scheduler = new Scheduler(scheduledHandlers);
        SimulationClock clock = new SimulationClock();
        SimulationStepper stepper = new SimulationStepper(clock, scheduler);

        SurfaceWaterStorageLookup surfaceStorage =
                new TerrainSurfaceWaterStorageLookup(
                        landscape.terrain(),
                        surfaceWaterStorageDefinitions);
        WaterFlowSystem waterFlow = new WaterFlowSystem(
                water,
                worldGeometry,
                surfaceStorage);
        WaterSoilExchangeSystem waterSoilExchange =
                new WaterSoilExchangeSystem(
                        water,
                        landscape.terrain(),
                        soilMoisture);
        WaterFlowProcess waterFlowProcess = new WaterFlowProcess(
                waterFlow,
                waterSoilExchange);
        HandlerId waterFlowHandlerId = scheduledHandlers.register(waterFlowProcess::resume);
        ProcessScheduler waterFlowScheduler =
                new BoundProcessScheduler(clock, scheduler, waterFlowHandlerId);
        waterFlowProcess.bindScheduler(waterFlowScheduler);
        waterFlowProcess.activate();

        VerticalSkySurfaceSystem skySurfaces = new VerticalSkySurfaceSystem(
                landscape.terrainSurfaces(),
                water.surfaces());
        PrecipitationEventLookup precipitationEvents = tick -> false;

        if (precipitationSchedule != null) {
            PrecipitationSystem precipitation = new PrecipitationSystem(
                    landscape.terrain(),
                    worldGeometry,
                    soilMoisture,
                    water);
            SkyPrecipitationSystem skyPrecipitation = new SkyPrecipitationSystem(
                    skySurfaces,
                    precipitation);
            PeriodicPrecipitationSystem periodicPrecipitation =
                    new PeriodicPrecipitationSystem(
                            skyPrecipitation,
                            precipitationSchedule,
                            clock);
            precipitationEvents = periodicPrecipitation;
            HandlerId precipitationHandlerId = scheduledHandlers.register(processId -> {
                periodicPrecipitation.resume(processId);
                waterFlowProcess.activate();
            });
            ProcessScheduler precipitationScheduler =
                    new BoundProcessScheduler(
                            clock,
                            scheduler,
                            precipitationHandlerId);
            periodicPrecipitation.bindScheduler(precipitationScheduler);
            periodicPrecipitation.start();
        }

        if (evaporationSchedule != null) {
            EvaporationSystem evaporation = new EvaporationSystem(
                    skySurfaces,
                    water.surfaces(),
                    soilMoisture.cells(),
                    worldGeometry,
                    water,
                    soilMoisture);
            PeriodicEvaporationSystem periodicEvaporation =
                    new PeriodicEvaporationSystem(
                            evaporation,
                            evaporationSchedule,
                            clock,
                            precipitationEvents);
            HandlerId evaporationHandlerId = scheduledHandlers.register(processId -> {
                periodicEvaporation.resume(processId);
                if (periodicEvaporation.lastResult().surfaceWaterRemoved() > 0L) {
                    waterFlowProcess.activate();
                }
            });
            ProcessScheduler evaporationScheduler =
                    new BoundProcessScheduler(
                            clock,
                            scheduler,
                            evaporationHandlerId);
            periodicEvaporation.bindScheduler(evaporationScheduler);
            periodicEvaporation.start();
        }

        WaterWadingConstraint waterWading = new WaterWadingConstraint(
                objects,
                waterWadingDefinitions,
                water.lookup(),
                worldGeometry);

        MovementStepCompletionRelay movementCompletions = new MovementStepCompletionRelay();
        MovementActionProcessor movementActions = new MovementActionProcessor(
                movementState,
                objects,
                spatial.transforms(),
                navigation.lookup(),
                waterWading,
                occupancy,
                spatial,
                orientations,
                movementCompletions);
        HandlerId movementHandlerId = scheduledHandlers.register(movementActions::complete);
        ProcessScheduler movementScheduler = new BoundProcessScheduler(clock, scheduler, movementHandlerId);

        TransitionCostCalculator transitionCosts = new TransitionCostCalculator(
                landscape.terrain(), worldGeometry, landscapeTraversalDefinitions);
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
                waterWading,
                occupancy,
                movementState,
                movementScheduler);
        MoveToSystem moveTo = new MoveToSystem(
                spatial.transforms(),
                pathfinder,
                movement,
                new MoverTraversalQueryConstraintProvider(waterWading));
        movementCompletions.bind(moveTo);

        NeedSystem needs = new NeedSystem(objects, needDefinitions);
        ConsumableStockReductionRelay stockReductions = new ConsumableStockReductionRelay();
        ConsumableStockSystem consumableStocks = new ConsumableStockSystem(
                objects,
                consumableStockDefinitions,
                stockReductions);
        for (ObjectId objectId : createdObjects) {
            needs.attach(objectId);
            consumableStocks.attach(objectId);
        }

        NeedProgressionSystem needProgression = new NeedProgressionSystem(
                objects,
                needProgressionDefinitions,
                needs,
                needs,
                new IntrinsicNeedProgressionRateResolver(),
                clock);
        HandlerId needProgressionHandlerId = scheduledHandlers.register(needProgression::resume);
        ProcessScheduler needProgressionScheduler =
                new BoundProcessScheduler(clock, scheduler, needProgressionHandlerId);
        needProgression.bindScheduler(needProgressionScheduler);
        for (ObjectId objectId : createdObjects) {
            WorldObject object = objects.get(objectId);
            if (object != null && needProgressionDefinitions.has(object.definitionId())) {
                needProgression.activate(objectId);
            }
        }

        GrowthSystem growth = new GrowthSystem(
                objects,
                growthDefinitions,
                consumableStocks,
                consumableStocks,
                new IntrinsicGrowthRateResolver(),
                clock);
        HandlerId growthHandlerId = scheduledHandlers.register(growth::resume);
        ProcessScheduler growthScheduler = new BoundProcessScheduler(clock, scheduler, growthHandlerId);
        growth.bindScheduler(growthScheduler);
        stockReductions.bind(growth);
        for (ObjectId objectId : createdObjects) {
            WorldObject object = objects.get(objectId);
            if (object != null && growthDefinitions.has(object.definitionId())) growth.activate(objectId);
        }

        VisionSystem vision = new VisionSystem(
                objects,
                spatial.transforms(),
                cells.lookup(),
                orientations,
                visionDefinitions,
                new TerrainSightOcclusionLookup(landscape.terrain()));
        AgentSearchSystem searches = new AgentSearchSystem(
                orientations,
                orientations,
                vision,
                CorrelatedRandomWalkExplorationPolicy.standard());
        RelativeSearchLocomotion searchLocomotion = new RelativeSearchLocomotion(
                spatial.transforms(),
                navigation.lookup(),
                vision,
                moveTo,
                moveTo);

        NeedSatisfactionOpportunityProvider needSatisfaction = new NeedSatisfactionOpportunityProvider(
                objects,
                spatial.transforms(),
                agentDefinitions,
                needSatisfactionDefinitions,
                needSolutionKnowledgeDefinitions,
                needMotivationDefinitions,
                needs,
                consumableStocks,
                clock);
        HandlerId needSatisfactionHandlerId = scheduledHandlers.register(needSatisfaction::resume);
        ProcessScheduler needSatisfactionScheduler =
                new BoundProcessScheduler(clock, scheduler, needSatisfactionHandlerId);
        needSatisfaction.bindScheduler(needSatisfactionScheduler);
        AgentOpportunityProvider needSatisfactionProvider = needSatisfaction;

        AgentSystem agents = new AgentSystem(
                objects,
                spatial.transforms(),
                agentDefinitions,
                List.of(needSatisfactionProvider),
                moveTo,
                moveTo,
                vision,
                searches,
                searchLocomotion,
                clock);
        HandlerId agentHandlerId = scheduledHandlers.register(agents::resume);
        ProcessScheduler agentScheduler = new BoundProcessScheduler(clock, scheduler, agentHandlerId);
        agents.bindScheduler(agentScheduler);
        for (ObjectId objectId : createdObjects) {
            WorldObject object = objects.get(objectId);
            if (object != null && agentDefinitions.has(object.definitionId())) agents.activate(objectId);
        }

        CommandDispatcher dispatcher = new CommandDispatcher();
        dispatcher.register(PlaceTerrainCommand.class, new PlaceTerrainHandler(landscape));
        dispatcher.register(ReplaceTerrainCommand.class, new ReplaceTerrainHandler(landscape));
        dispatcher.register(MoveStepCommand.class, new MoveStepHandler(movement));
        dispatcher.register(MoveToCommand.class, new MoveToHandler(moveTo));

        SimulationView view = new SimulationView(
                objects,
                spatial.transforms(),
                orientations,
                vision,
                landscape.terrain(),
                landscape.terrainExtents(),
                landscape.terrainSurfaces(),
                landscape.terrainRevision(),
                worldGeometry,
                soilMoisture.lookup(),
                soilMoisture::hydrologyAt,
                surfaceStorage,
                water.lookup(),
                water.surfaces(),
                waterFlow.flowLookup(),
                navigation.lookup(),
                occupancy,
                cells.lookup(),
                pathfinder,
                moveTo,
                needs,
                needProgression,
                consumableStocks,
                growth,
                agents,
                searches);
        return new SimulationRuntime(
                new SynchronousCommandGateway(dispatcher), clock, stepper, view);
    }

    private void requireLandscapeDefinition(LandscapeDefinitionId definitionId) {
        if (!landscapeDefinitions.contains(definitionId)) {
            throw new IllegalArgumentException(
                    "unknown landscape definition: " + definitionId);
        }
    }

    private void requireObjectDefinition(ObjectDefinitionId definitionId) {
        if (!objectDefinitions.contains(definitionId)) {
            throw new IllegalArgumentException("unknown object definition: " + definitionId);
        }
    }

    private void requireInsideWorld(
            int x,
            int y,
            int z) {
        if (!worldGeometry.contains(x, y, z)) {
            throw new IllegalArgumentException(
                    "coordinate is outside configured world bounds: ("
                            + x + ", " + y + ", " + z + ")");
        }
    }

    private void requireNotStarted() {
        if (started) throw new IllegalStateException("simulation assembly has already started");
    }
}
