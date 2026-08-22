package io.github.evoforge.simulation.runtime;

import io.github.evoforge.simulation.definition.DefinitionRegistry;
import io.github.evoforge.simulation.agents.AgentDefinitions;
import io.github.evoforge.simulation.agents.affordance.NeedSatisfactionDefinitions;
import io.github.evoforge.simulation.agents.affordance.liquid.LiquidDrinkDefinitions;
import io.github.evoforge.simulation.agents.knowledge.need.NeedSolutionKnowledgeDefinitions;
import io.github.evoforge.simulation.agents.need.NeedDefinitions;
import io.github.evoforge.simulation.agents.need.motivation.NeedMotivationDefinitions;
import io.github.evoforge.simulation.agents.need.progression.NeedProgressionDefinitions;
import io.github.evoforge.simulation.agents.perception.vision.VisionDefinitions;
import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;
import io.github.evoforge.simulation.world.liquid.LiquidTransportDefinitions;
import io.github.evoforge.simulation.world.liquid.LiquidTransportProperties;
import io.github.evoforge.simulation.world.liquid.SurfaceRetentionDefinitions;
import io.github.evoforge.simulation.world.soil.SoilPropertiesDefinitions;
import io.github.evoforge.simulation.world.liquid.water.WaterSystem;
import io.github.evoforge.simulation.world.object.stock.ConsumableStockDefinitions;
import io.github.evoforge.simulation.world.object.stock.growth.GrowthDefinitions;
import io.github.evoforge.simulation.mechanics.movement.MovementDefinitions;
import io.github.evoforge.simulation.world.space.occupancy.OccupancyDefinitions;
import io.github.evoforge.simulation.world.navigation.traversal.LandscapeTraversalDefinitions;
import io.github.evoforge.simulation.world.navigation.traversal.water.WaterWadingDefinitions;
import io.github.evoforge.simulation.world.object.definition.ObjectDefinitionId;

/** Mutable authored/runtime definition set owned by one pre-start simulation assembly. */
final class SimulationDefinitions {
    final DefinitionRegistry<LandscapeDefinitionId> landscape =
            new DefinitionRegistry<>(LandscapeDefinitionId::of, LandscapeDefinitionId::asInt);
    final LandscapeTraversalDefinitions landscapeTraversal = new LandscapeTraversalDefinitions();
    final SoilPropertiesDefinitions soilProperties = new SoilPropertiesDefinitions();
    final SurfaceRetentionDefinitions surfaceRetention = new SurfaceRetentionDefinitions();
    final LiquidTransportDefinitions liquidTransport = new LiquidTransportDefinitions();
    final DefinitionRegistry<ObjectDefinitionId> objects =
            new DefinitionRegistry<>(ObjectDefinitionId::of, ObjectDefinitionId::asInt);
    final MovementDefinitions movement = new MovementDefinitions();
    final WaterWadingDefinitions waterWading = new WaterWadingDefinitions();
    final OccupancyDefinitions occupancy = new OccupancyDefinitions();
    final AgentDefinitions agents = new AgentDefinitions();
    final VisionDefinitions vision = new VisionDefinitions();
    final NeedDefinitions needs = new NeedDefinitions();
    final NeedMotivationDefinitions needMotivation = new NeedMotivationDefinitions();
    final NeedProgressionDefinitions needProgression = new NeedProgressionDefinitions();
    final NeedSatisfactionDefinitions needSatisfaction = new NeedSatisfactionDefinitions();
    final NeedSolutionKnowledgeDefinitions needSolutionKnowledge =
            new NeedSolutionKnowledgeDefinitions();
    final LiquidDrinkDefinitions liquidDrink = new LiquidDrinkDefinitions();
    final ConsumableStockDefinitions consumableStock = new ConsumableStockDefinitions();
    final GrowthDefinitions growth = new GrowthDefinitions();

    SimulationDefinitions() {
        liquidTransport.put(WaterSystem.TYPE, LiquidTransportProperties.reference());
    }

    void freeze() {
        landscape.freeze();
        landscapeTraversal.freeze();
        soilProperties.freeze();
        surfaceRetention.freeze();
        liquidTransport.freeze();
        objects.freeze();
        movement.freeze();
        waterWading.freeze();
        occupancy.freeze();
        agents.freeze();
        vision.freeze();
        needs.freeze();
        needMotivation.freeze();
        needProgression.freeze();
        needSatisfaction.freeze();
        needSolutionKnowledge.freeze();
        liquidDrink.freeze();
        consumableStock.freeze();
        growth.freeze();
    }
}
