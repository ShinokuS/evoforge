package io.github.evoforge.simulation.runtime;

import io.github.evoforge.simulation.control.core.CommandDispatcher;
import io.github.evoforge.simulation.control.movement.CancelMoveToCommand;
import io.github.evoforge.simulation.control.movement.CancelMoveToHandler;
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
import io.github.evoforge.simulation.world.agent.affordance.liquid.LiquidDrinkDefinition;
import io.github.evoforge.simulation.world.agent.affordance.liquid.LiquidDrinkDefinitions;
import io.github.evoforge.simulation.world.agent.affordance.liquid.LiquidDrinkOpportunityProvider;
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
import io.github.evoforge.simulation.world.landscape.liquid.LiquidFlowProcess;
import io.github.evoforge.simulation.world.landscape.liquid.LiquidFlowSystem;
import io.github.evoforge.simulation.world.landscape.liquid.LiquidSurfaceRetentionLookup;
import io.github.evoforge.simulation.world.landscape.liquid.LiquidSystem;
import io.github.evoforge.simulation.world.landscape.liquid.LiquidTransportDefinitions;
import io.github.evoforge.simulation.world.landscape.liquid.LiquidTransportProperties;
import io.github.evoforge.simulation.world.landscape.liquid.LiquidTypeId;
import io.github.evoforge.simulation.world.landscape.liquid.SurfaceRetentionDefinitions;
import io.github.evoforge.simulation.world.landscape.liquid.TerrainSurfaceRetentionLookup;
import io.github.evoforge.simulation.world.landscape.liquid.storage.SparseLiquidStorage;
import io.github.evoforge.simulation.world.landscape.soil.SoilLiquidInfiltrationSystem;
import io.github.evoforge.simulation.world.landscape.soil.SoilLiquidSystem;
import io.github.evoforge.simulation.world.landscape.soil.SoilProperties;
import io.github.evoforge.simulation.world.landscape.soil.SoilPropertiesDefinitions;
import io.github.evoforge.simulation.world.landscape.soil.SoilPropertiesLookup;
import io.github.evoforge.simulation.world.landscape.soil.SoilPropertiesVariation;
import io.github.evoforge.simulation.world.landscape.soil.SoilPropertiesVariationDefinitions;
import io.github.evoforge.simulation.world.landscape.soil.TerrainSoilPropertiesLookup;
import io.github.evoforge.simulation.world.landscape.soil.storage.SparseSoilLiquidStorage;
import io.github.evoforge.simulation.world.landscape.terrain.storage.SparseTerrainStorage;
import io.github.evoforge.simulation.world.landscape.water.WaterFlowLookup;
import io.github.evoforge.simulation.world.landscape.water.WaterSystem;
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
import io.github.evoforge.simulation.world.mechanics.interaction.InteractionAccessResolver;
import io.github.evoforge.simulation.world.mechanics.interaction.InteractionReachProfile;
import io.github.evoforge.simulation.world.mechanics.measurement.PhysicalCellVolume;
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
import io.github.evoforge.simulation.world.mechanics.traversal.MoverDestinationAccessResolver;
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
    private final SoilPropertiesDefinitions soilPropertiesDefinitions;
    private final SoilPropertiesVariationDefinitions soilPropertiesVariationDefinitions;
    private final SurfaceRetentionDefinitions surfaceRetentionDefinitions;
    private final LiquidTransportDefinitions liquidTransportDefinitions;
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
    private final LiquidDrinkDefinitions liquidDrinkDefinitions;
    private final ConsumableStockDefinitions consumableStockDefinitions;
    private final GrowthDefinitions growthDefinitions;
    private final LandscapeSystem landscape;
    private final WorldGeometryLookup worldGeometry;
    private final LiquidSystem liquids;
    private final SoilPropertiesLookup soilProperties;
    private final SoilLiquidSystem soilLiquids;
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
    private PhysicalCellVolume physicalCellVolume;
    private boolean started;

    private SimulationAssembly() {
        landscapeDefinitions = new DefinitionRegistry<>(LandscapeDefinitionId::of, LandscapeDefinitionId::asInt);
        landscapeTraversalDefinitions = new LandscapeTraversalDefinitions();
        soilPropertiesDefinitions = new SoilPropertiesDefinitions();
        soilPropertiesVariationDefinitions = new SoilPropertiesVariationDefinitions();
        surfaceRetentionDefinitions = new SurfaceRetentionDefinitions();
        liquidTransportDefinitions = new LiquidTransportDefinitions();
        liquidTransportDefinitions.put(
                WaterSystem.TYPE,
                LiquidTransportProperties.reference());
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
        liquidDrinkDefinitions = new LiquidDrinkDefinitions();
        consumableStockDefinitions = new ConsumableStockDefinitions();
        growthDefinitions = new GrowthDefinitions();
        landscape = LandscapeSystem.create(new SparseTerrainStorage(), landscapeDefinitions);
        worldGeometry = new WorldGeometryLookup(landscape.geometry());
        liquids = new LiquidSystem(new SparseLiquidStorage(), worldGeometry);
        soilProperties = new TerrainSoilPropertiesLookup(
                landscape.terrain(),
                soilPropertiesDefinitions,
                soilPropertiesVariationDefinitions);
        soilLiquids = new SoilLiquidSystem(
                new SparseSoilLiquidStorage(),
                soilProperties,
                liquidTransportDefinitions);
        water = new WaterSystem(liquids);
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

    public SimulationAssembly soilProperties(
            LandscapeDefinitionId definitionId,
            int capacity,
            int permeability) {
        requireNotStarted();
        requireLandscapeDefinition(definitionId);
        soilPropertiesDefinitions.put(
                definitionId,
                new SoilProperties(capacity, permeability));
        return this;
    }

    public SimulationAssembly soilPropertiesVariation(
            LandscapeDefinitionId definitionId,
            long seed,
            int capacityAmplitude) {
        requireNotStarted();
        requireLandscapeDefinition(definitionId);
        soilPropertiesVariationDefinitions.put(
                definitionId,
                new SoilPropertiesVariation(seed, capacityAmplitude));
        return this;
    }

    public SimulationAssembly liquidTransport(
            LiquidTypeId type,
            long kinematicViscositySquareMicrometersPerSecond) {
        requireNotStarted();
        liquidTransportDefinitions.put(
                type,
                LiquidTransportProperties.ofKinematicViscosity(
                        kinematicViscositySquareMicrometersPerSecond));
        return this;
    }

    /** Declares material microtopography that retains free liquid before horizontal runoff. */
    public SimulationAssembly surfaceRetention(
            LandscapeDefinitionId definitionId,
            int capacity) {
        requireNotStarted();
        requireLandscapeDefinition(definitionId);
        surfaceRetentionDefinitions.put(definitionId, capacity);
        return this;
    }

    public SimulationAssembly precipitation(PrecipitationSchedule schedule) {
        requireNotStarted();
        if (schedule == null) {
            throw new IllegalArgumentException(
                    "precipitation schedule must not be null");
        }
        precipitationSchedule = schedule;
        return this;
    }

    public SimulationAssembly periodicPrecipitation(
            int amountPerColumn,
            long intervalTicks) {
        return precipitation(new PrecipitationSchedule(
                amountPerColumn,
                intervalTicks));
    }

    public SimulationAssembly cyclicPrecipitation(
            int amountPerColumn,
            long intervalTicks,
            long activeTicks,
            long cycleTicks) {
        return precipitation(PrecipitationSchedule.cyclic(
                amountPerColumn,
                intervalTicks,
                activeTicks,
                cycleTicks));
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

    /** Defines the physical volume represented by one completely open simulation cell. */
    public SimulationAssembly physicalCellVolumeMilliliters(long millilitersPerFullCell) {
        requireNotStarted();
        physicalCellVolume = new PhysicalCellVolume(millilitersPerFullCell);
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

    public SimulationAssembly knowsNeedSolution(ObjectDefinitionId definitionId, NeedId needId) {
        requireNotStarted(); requireObjectDefinition(definitionId);
        needSolutionKnowledgeDefinitions.add(definitionId, needId);
        return this;
    }

    public SimulationAssembly drinksLiquid(
            ObjectDefinitionId definitionId,
            NeedId needId,
            LiquidTypeId liquidType,
            long requestedMillilitersPerUse,
            long needReliefPerFullUse,
            long useDurationTicks,
            InteractionReachProfile reach) {
        requireNotStarted();
        requireObjectDefinition(definitionId);
        if (liquidType == null || !liquidTransportDefinitions.has(liquidType)) {
            throw new IllegalArgumentException(
                    "drinkable liquid must have transport properties: " + liquidType);
        }
        liquidDrinkDefinitions.add(
                definitionId,
                new LiquidDrinkDefinition(
                        needId,
                        liquidType,
                        requestedMillilitersPerUse,
                        needReliefPerFullUse,
                        useDurationTicks,
                        reach));
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

    public SimulationAssembly initialLiquid(
            LiquidTypeId type,
            int x,
            int y,
            int z,
            int amount) {
        requireNotStarted();
        requireInsideWorld(x, y, z);
        if (!liquidTransportDefinitions.has(type)) {
            throw new IllegalArgumentException(
                    "liquid transport properties must be configured before placement: " + type);
        }
        int added = liquids.addAtMost(type, x, y, z, amount);
        if (added != amount) {
            if (added > 0) liquids.removeAtMost(type, x, y, z, added);
            throw new IllegalArgumentException(
                    "initial liquid does not fit cell geometry/contact invariant: requested="
                            + amount + ", accepted=" + added + ", type=" + type);
        }
        return this;
    }

    public SimulationAssembly initialWater(int x, int y, int z, int amount) {
        return initialLiquid(WaterSystem.TYPE, x, y, z, amount);
    }

    public SimulationRuntime start() {
        requireNotStarted();
        if (!liquidDrinkDefinitions.isEmpty() && physicalCellVolume == null) {
            throw new IllegalStateException(
                    "physical cell volume must be configured before liquid drinking is enabled");
        }
        started = true;
        landscapeDefinitions.freeze();
        landscapeTraversalDefinitions.freeze();
        soilPropertiesDefinitions.freeze();
        soilPropertiesVariationDefinitions.freeze();
        surfaceRetentionDefinitions.freeze();
        liquidTransportDefinitions.freeze();
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
        liquidDrinkDefinitions.freeze();
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

        LiquidSurfaceRetentionLookup surfaceRetention =
                new TerrainSurfaceRetentionLookup(
                        landscape.terrain(),
                        surfaceRetentionDefinitions);
        LiquidFlowSystem liquidFlow = new LiquidFlowSystem(
                liquids,
                worldGeometry,
                surfaceRetention,
                liquidTransportDefinitions);
        SoilLiquidInfiltrationSystem infiltration = new SoilLiquidInfiltrationSystem(
                liquids,
                landscape.terrain(),
                soilLiquids);
        LiquidFlowProcess liquidFlowProcess = new LiquidFlowProcess(
                liquidFlow,
                infiltration::update);
        HandlerId liquidFlowHandlerId = scheduledHandlers.register(liquidFlowProcess::resume);
        ProcessScheduler liquidFlowScheduler =
                new BoundProcessScheduler(clock, scheduler, liquidFlowHandlerId);
        liquidFlowProcess.bindScheduler(liquidFlowScheduler);
        liquidFlowProcess.activate();

        VerticalSkySurfaceSystem skySurfaces = new VerticalSkySurfaceSystem(
                landscape.terrainSurfaces(),
                water.surfaces());
        PrecipitationEventLookup precipitationEvents = tick -> false;

        if (precipitationSchedule != null) {
            PrecipitationSystem precipitation = new PrecipitationSystem(
                    landscape.terrain(),
                    worldGeometry,
                    soilLiquids,
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
                liquidFlowProcess.activate();
            });
            ProcessScheduler precipitationScheduler =
                    new BoundProcessScheduler(clock, scheduler, precipitationHandlerId);
            periodicPrecipitation.bindScheduler(precipitationScheduler);
            periodicPrecipitation.start();
        }

        if (evaporationSchedule != null) {
            EvaporationSystem evaporation = new EvaporationSystem(
                    skySurfaces,
                    water.surfaces(),
                    soilLiquids.cells(),
                    worldGeometry,
                    water,
                    soilLiquids);
            PeriodicEvaporationSystem periodicEvaporation =
                    new PeriodicEvaporationSystem(
                            evaporation,
                            evaporationSchedule,
                            clock,
                            precipitationEvents);
            HandlerId evaporationHandlerId = scheduledHandlers.register(processId -> {
                periodicEvaporation.resume(processId);
                if (periodicEvaporation.lastResult().surfaceWaterRemoved() > 0L) {
                    liquidFlowProcess.activate();
                }
            });
            ProcessScheduler evaporationScheduler =
                    new BoundProcessScheduler(clock, scheduler, evaporationHandlerId);
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
        MoverDestinationAccessResolver destinationAccess = new MoverDestinationAccessResolver(
                navigation.lookup(),
                waterWading);

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

        List<AgentOpportunityProvider> opportunityProviders = new ArrayList<>();
        opportunityProviders.add(needSatisfaction);
        if (!liquidDrinkDefinitions.isEmpty()) {
            LiquidDrinkOpportunityProvider liquidDrinking = new LiquidDrinkOpportunityProvider(
                    objects,
                    spatial.transforms(),
                    liquidDrinkDefinitions,
                    needSolutionKnowledgeDefinitions,
                    needMotivationDefinitions,
                    needs,
                    liquids,
                    physicalCellVolume,
                    new InteractionAccessResolver(worldGeometry),
                    liquidFlowProcess::activate,
                    clock);
            HandlerId liquidDrinkingHandlerId = scheduledHandlers.register(liquidDrinking::resume);
            ProcessScheduler liquidDrinkingScheduler =
                    new BoundProcessScheduler(clock, scheduler, liquidDrinkingHandlerId);
            liquidDrinking.bindScheduler(liquidDrinkingScheduler);
            opportunityProviders.add(liquidDrinking);
        }

        AgentSystem agents = new AgentSystem(
                objects,
                spatial.transforms(),
                agentDefinitions,
                List.copyOf(opportunityProviders),
                moveTo,
                moveTo,
                destinationAccess,
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
        dispatcher.register(CancelMoveToCommand.class, new CancelMoveToHandler(moveTo));

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
                soilLiquids.lookup(),
                soilProperties,
                surfaceRetention,
                water.lookup(),
                water.surfaces(),
                WaterFlowLookup.from(liquidFlow.flowLookup()),
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

    private void requireInsideWorld(int x, int y, int z) {
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
