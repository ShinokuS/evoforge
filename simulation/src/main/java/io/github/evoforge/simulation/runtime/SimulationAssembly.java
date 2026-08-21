package io.github.evoforge.simulation.runtime;

import io.github.evoforge.simulation.result.OperationResults;
import io.github.evoforge.simulation.world.agent.AgentDefinition;
import io.github.evoforge.simulation.world.agent.CapabilityId;
import io.github.evoforge.simulation.world.agent.affordance.NeedSatisfaction;
import io.github.evoforge.simulation.world.agent.affordance.liquid.LiquidDrinkDefinition;
import io.github.evoforge.simulation.world.agent.need.NeedId;
import io.github.evoforge.simulation.world.agent.need.NeedSpec;
import io.github.evoforge.simulation.world.agent.need.motivation.NeedMotivationDefinition;
import io.github.evoforge.simulation.world.agent.need.progression.NeedProgressionDefinition;
import io.github.evoforge.simulation.world.agent.perception.vision.VisionDefinition;
import io.github.evoforge.simulation.world.environment.atmosphere.AtmosphericWaterForcing;
import io.github.evoforge.simulation.world.environment.evaporation.EvaporationSchedule;
import io.github.evoforge.simulation.world.environment.precipitation.PrecipitationSchedule;
import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;
import io.github.evoforge.simulation.world.landscape.liquid.LiquidTransportProperties;
import io.github.evoforge.simulation.world.landscape.liquid.LiquidTypeId;
import io.github.evoforge.simulation.world.landscape.soil.SoilProperties;
import io.github.evoforge.simulation.world.landscape.soil.SoilPropertiesLookup;
import io.github.evoforge.simulation.world.landscape.water.WaterSystem;
import io.github.evoforge.simulation.world.mechanics.consumption.ConsumableStockDefinition;
import io.github.evoforge.simulation.world.mechanics.geometry.Shape;
import io.github.evoforge.simulation.world.mechanics.growth.GrowthDefinition;
import io.github.evoforge.simulation.world.mechanics.interaction.InteractionReachProfile;
import io.github.evoforge.simulation.world.mechanics.measurement.PhysicalCellVolume;
import io.github.evoforge.simulation.world.mechanics.movement.MovementRate;
import io.github.evoforge.simulation.world.mechanics.traversal.SurfaceTraversalCost;
import io.github.evoforge.simulation.world.mechanics.traversal.water.WaterWadingProfile;
import io.github.evoforge.simulation.world.object.ObjectId;
import io.github.evoforge.simulation.world.object.WorldObject;
import io.github.evoforge.simulation.world.object.definition.ObjectDefinitionId;
import io.github.evoforge.simulation.world.spatial.WorldBounds;
import io.github.evoforge.simulation.world.spatial.orientation.FacingDirection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Production pre-start facade for configuring one simulation instance.
 *
 * <p>Definition ownership, authoritative base world state and runtime wiring are deliberately
 * delegated to focused package-private collaborators. This class owns only the fluent assembly
 * lifecycle and compatibility surface used by content/bootstrap callers.</p>
 */
public final class SimulationAssembly {
    private final SimulationDefinitions definitions = new SimulationDefinitions();
    private final SimulationWorldState world = new SimulationWorldState(definitions);
    private final Set<ObjectDefinitionId> placedObjectDefinitions = new HashSet<>();
    private final List<ObjectId> createdObjects = new ArrayList<>();
    private final Map<ObjectId, FacingDirection> initialFacing = new HashMap<>();
    private PrecipitationSchedule precipitationSchedule;
    private EvaporationSchedule evaporationSchedule;
    private AtmosphericWaterForcing atmosphericWaterForcing;
    private PhysicalCellVolume physicalCellVolume;
    private WorldBounds worldBounds;
    private boolean started;

    private SimulationAssembly() { }

    public static SimulationAssembly create() {
        return new SimulationAssembly();
    }

    public SimulationAssembly worldBounds(
            int minX,
            int maxX,
            int minY,
            int maxY,
            int minZ,
            int maxZ) {
        requireNotStarted();
        WorldBounds configured = new WorldBounds(minX, maxX, minY, maxY, minZ, maxZ);
        world.geometry.configureBounds(configured);
        worldBounds = configured;
        return this;
    }

    public LandscapeDefinitionId landscapeDefinition(String key) {
        return landscapeDefinition(key, SurfaceTraversalCost.NEUTRAL_UNITS);
    }

    public LandscapeDefinitionId landscapeDefinition(String key, long traversalCostUnits) {
        requireNotStarted();
        LandscapeDefinitionId definitionId = definitions.landscape.register(key);
        definitions.landscapeTraversal.put(
                definitionId,
                SurfaceTraversalCost.of(traversalCostUnits));
        return definitionId;
    }

    public SimulationAssembly soilProperties(
            LandscapeDefinitionId definitionId,
            int capacity,
            int permeability) {
        requireNotStarted();
        requireLandscapeDefinition(definitionId);
        definitions.soilProperties.put(
                definitionId,
                new SoilProperties(capacity, permeability));
        return this;
    }

    /** Selects authoritative local Soil properties for this runtime before it starts. */
    public SimulationAssembly resolvedSoilProperties(SoilPropertiesLookup lookup) {
        requireNotStarted();
        world.soilProperties.configure(lookup);
        return this;
    }

    public SimulationAssembly liquidTransport(
            LiquidTypeId type,
            long kinematicViscositySquareMicrometersPerSecond) {
        requireNotStarted();
        definitions.liquidTransport.put(
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
        definitions.surfaceRetention.put(definitionId, capacity);
        return this;
    }

    public SimulationAssembly precipitation(PrecipitationSchedule schedule) {
        requireNotStarted();
        if (schedule == null) {
            throw new IllegalArgumentException("precipitation schedule must not be null");
        }
        requireNoAtmosphericWaterForcing();
        precipitationSchedule = schedule;
        return this;
    }

    public SimulationAssembly periodicPrecipitation(
            int amountPerColumn,
            long intervalTicks) {
        return precipitation(new PrecipitationSchedule(amountPerColumn, intervalTicks));
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
        requireNoAtmosphericWaterForcing();
        evaporationSchedule = new EvaporationSchedule(amountPerColumn, intervalTicks);
        return this;
    }

    /** Selects one runtime atmospheric Water source/sink producer. */
    public SimulationAssembly atmosphericWaterForcing(AtmosphericWaterForcing forcing) {
        requireNotStarted();
        if (forcing == null) {
            throw new IllegalArgumentException("atmospheric water forcing must not be null");
        }
        if (worldBounds == null) {
            throw new IllegalStateException(
                    "world bounds must be configured before atmospheric water forcing");
        }
        if (!worldBounds.equals(forcing.bounds())) {
            throw new IllegalArgumentException(
                    "atmospheric water forcing bounds must match runtime world bounds");
        }
        if (precipitationSchedule != null || evaporationSchedule != null) {
            throw new IllegalStateException(
                    "atmospheric water forcing cannot be combined with periodic atmospheric schedules");
        }
        if (atmosphericWaterForcing != null) {
            throw new IllegalStateException("atmospheric water forcing is already configured");
        }
        atmosphericWaterForcing = forcing;
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
        return definitions.objects.register(key);
    }

    public SimulationAssembly movementRate(ObjectDefinitionId definitionId, long unitsPerTick) {
        requireNotStarted();
        requireObjectDefinition(definitionId);
        definitions.movement.put(definitionId, MovementRate.of(unitsPerTick));
        return this;
    }

    public SimulationAssembly waterWading(
            ObjectDefinitionId definitionId,
            int maxDepth) {
        requireNotStarted();
        requireObjectDefinition(definitionId);
        definitions.waterWading.put(definitionId, new WaterWadingProfile(maxDepth));
        return this;
    }

    public SimulationAssembly exclusiveOccupancy(ObjectDefinitionId definitionId) {
        requireNotStarted();
        requireObjectDefinition(definitionId);
        if (placedObjectDefinitions.contains(definitionId)) {
            throw new IllegalStateException(
                    "exclusive occupancy must be configured before placing instances of definition: "
                            + definitionId);
        }
        definitions.occupancy.put(definitionId, true);
        return this;
    }

    public SimulationAssembly agent(
            ObjectDefinitionId definitionId,
            CapabilityId... capabilities) {
        requireNotStarted();
        requireObjectDefinition(definitionId);
        definitions.agents.put(definitionId, new AgentDefinition(capabilities));
        return this;
    }

    public SimulationAssembly vision(
            ObjectDefinitionId definitionId,
            int range,
            int horizontalFovDegrees) {
        requireNotStarted();
        requireObjectDefinition(definitionId);
        definitions.vision.put(
                definitionId,
                new VisionDefinition(range, horizontalFovDegrees));
        return this;
    }

    public SimulationAssembly initialFacing(ObjectId objectId, int dx, int dy) {
        requireNotStarted();
        if (!world.objects.isAlive(objectId)) {
            throw new IllegalArgumentException("object must be alive: " + objectId);
        }
        initialFacing.put(objectId, FacingDirection.of(dx, dy));
        return this;
    }

    public SimulationAssembly need(
            ObjectDefinitionId definitionId,
            NeedId needId,
            long maxLevel,
            long initialLevel) {
        requireNotStarted();
        requireObjectDefinition(definitionId);
        definitions.needs.add(definitionId, new NeedSpec(needId, maxLevel, initialLevel));
        return this;
    }

    public SimulationAssembly needMotivation(
            ObjectDefinitionId definitionId,
            NeedId needId,
            long activationLevel) {
        requireNotStarted();
        requireObjectDefinition(definitionId);
        definitions.needMotivation.add(
                definitionId,
                new NeedMotivationDefinition(needId, activationLevel));
        return this;
    }

    public SimulationAssembly needProgression(
            ObjectDefinitionId definitionId,
            NeedId needId,
            long baseAmount,
            long intervalTicks) {
        requireNotStarted();
        requireObjectDefinition(definitionId);
        definitions.needProgression.add(
                definitionId,
                new NeedProgressionDefinition(needId, baseAmount, intervalTicks));
        return this;
    }

    public SimulationAssembly knowsNeedSolution(
            ObjectDefinitionId definitionId,
            NeedId needId) {
        requireNotStarted();
        requireObjectDefinition(definitionId);
        definitions.needSolutionKnowledge.add(definitionId, needId);
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
        if (liquidType == null || !definitions.liquidTransport.has(liquidType)) {
            throw new IllegalArgumentException(
                    "drinkable liquid must have transport properties: " + liquidType);
        }
        definitions.liquidDrink.add(
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
        requireNotStarted();
        requireObjectDefinition(definitionId);
        definitions.consumableStock.put(
                definitionId,
                new ConsumableStockDefinition(capacity, initialQuantity));
        return this;
    }

    public SimulationAssembly growth(
            ObjectDefinitionId definitionId,
            long baseAmount,
            long intervalTicks) {
        requireNotStarted();
        requireObjectDefinition(definitionId);
        definitions.growth.put(
                definitionId,
                new GrowthDefinition(baseAmount, intervalTicks));
        return this;
    }

    public SimulationAssembly satisfiesNeed(
            ObjectDefinitionId sourceDefinitionId,
            NeedId needId,
            long amount,
            CapabilityId requiredCapability) {
        return satisfiesNeed(
                sourceDefinitionId,
                needId,
                amount,
                0L,
                0L,
                requiredCapability);
    }

    public SimulationAssembly satisfiesNeed(
            ObjectDefinitionId sourceDefinitionId,
            NeedId needId,
            long amount,
            long consumedQuantity,
            CapabilityId requiredCapability) {
        return satisfiesNeed(
                sourceDefinitionId,
                needId,
                amount,
                consumedQuantity,
                0L,
                requiredCapability);
    }

    public SimulationAssembly satisfiesNeed(
            ObjectDefinitionId sourceDefinitionId,
            NeedId needId,
            long amount,
            long consumedQuantity,
            long useDurationTicks,
            CapabilityId requiredCapability) {
        requireNotStarted();
        requireObjectDefinition(sourceDefinitionId);
        definitions.needSatisfaction.add(
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
        WorldObject object = world.objectFactory.create(definitionId);
        createdObjects.add(object.id());
        return object.id();
    }

    public SimulationAssembly placeObject(ObjectId objectId, int x, int y, int z) {
        requireNotStarted();
        requireInsideWorld(x, y, z);
        OperationResults.requireAccepted(world.objectPlacement.place(objectId, x, y, z));
        WorldObject object = world.objects.get(objectId);
        if (object == null) {
            throw new IllegalStateException(
                    "placed object disappeared from repository: " + objectId);
        }
        placedObjectDefinitions.add(object.definitionId());
        return this;
    }

    public SimulationAssembly placeTerrain(
            int x,
            int y,
            int z,
            LandscapeDefinitionId definitionId) {
        requireNotStarted();
        requireInsideWorld(x, y, z);
        OperationResults.requireAccepted(world.landscape.placeTerrain(x, y, z, definitionId));
        return this;
    }

    public SimulationAssembly setShape(int x, int y, int z, Shape shape) {
        requireNotStarted();
        requireInsideWorld(x, y, z);
        world.landscape.setShape(x, y, z, shape);
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
        if (!definitions.liquidTransport.has(type)) {
            throw new IllegalArgumentException(
                    "liquid transport properties must be configured before placement: " + type);
        }
        int added = world.liquids.addAtMost(type, x, y, z, amount);
        if (added != amount) {
            if (added > 0) {
                world.liquids.removeAtMost(type, x, y, z, added);
            }
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
        if (!definitions.liquidDrink.isEmpty() && physicalCellVolume == null) {
            throw new IllegalStateException(
                    "physical cell volume must be configured before liquid drinking is enabled");
        }
        world.soilProperties.freeze();
        started = true;
        return SimulationRuntimeStarter.start(
                definitions,
                world,
                new SimulationStartupConfig(
                        precipitationSchedule,
                        evaporationSchedule,
                        atmosphericWaterForcing,
                        physicalCellVolume,
                        createdObjects,
                        initialFacing));
    }

    private void requireLandscapeDefinition(LandscapeDefinitionId definitionId) {
        if (!definitions.landscape.contains(definitionId)) {
            throw new IllegalArgumentException(
                    "unknown landscape definition: " + definitionId);
        }
    }

    private void requireObjectDefinition(ObjectDefinitionId definitionId) {
        if (!definitions.objects.contains(definitionId)) {
            throw new IllegalArgumentException("unknown object definition: " + definitionId);
        }
    }

    private void requireInsideWorld(int x, int y, int z) {
        if (!world.geometry.contains(x, y, z)) {
            throw new IllegalArgumentException(
                    "coordinate is outside configured world bounds: ("
                            + x + ", " + y + ", " + z + ")");
        }
    }

    private void requireNoAtmosphericWaterForcing() {
        if (atmosphericWaterForcing != null) {
            throw new IllegalStateException(
                    "periodic atmospheric schedules cannot be combined with atmospheric water forcing");
        }
    }

    private void requireNotStarted() {
        if (started) {
            throw new IllegalStateException("simulation assembly has already started");
        }
    }
}
