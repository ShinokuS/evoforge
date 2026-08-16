package io.github.evoforge.simulation.runtime;

import io.github.evoforge.simulation.time.BoundProcessScheduler;
import io.github.evoforge.simulation.time.HandlerId;
import io.github.evoforge.simulation.time.ProcessScheduler;
import io.github.evoforge.simulation.world.agent.affordance.NeedSatisfactionOpportunityProvider;
import io.github.evoforge.simulation.world.agent.affordance.liquid.LiquidDrinkOpportunityProvider;
import io.github.evoforge.simulation.world.agent.decision.AgentSystem;
import io.github.evoforge.simulation.world.agent.need.NeedSystem;
import io.github.evoforge.simulation.world.agent.need.progression.IntrinsicNeedProgressionRateResolver;
import io.github.evoforge.simulation.world.agent.need.progression.NeedProgressionSystem;
import io.github.evoforge.simulation.world.agent.opportunity.AgentOpportunityProvider;
import io.github.evoforge.simulation.world.agent.perception.vision.TerrainSightOcclusionLookup;
import io.github.evoforge.simulation.world.agent.perception.vision.VisionSystem;
import io.github.evoforge.simulation.world.agent.search.AgentSearchSystem;
import io.github.evoforge.simulation.world.agent.search.CorrelatedRandomWalkExplorationPolicy;
import io.github.evoforge.simulation.world.agent.search.RelativeSearchLocomotion;
import io.github.evoforge.simulation.world.mechanics.consumption.ConsumableStockReductionRelay;
import io.github.evoforge.simulation.world.mechanics.consumption.ConsumableStockSystem;
import io.github.evoforge.simulation.world.mechanics.growth.GrowthSystem;
import io.github.evoforge.simulation.world.mechanics.growth.IntrinsicGrowthRateResolver;
import io.github.evoforge.simulation.world.mechanics.interaction.InteractionAccessResolver;
import io.github.evoforge.simulation.world.object.ObjectId;
import io.github.evoforge.simulation.world.object.WorldObject;
import java.util.ArrayList;
import java.util.List;

/** Builds needs, growth, perception, opportunities, search and autonomous decisions. */
final class AgentRuntimeAssembly {
    private AgentRuntimeAssembly() { }

    static AgentRuntime assemble(
            SimulationDefinitions definitions,
            SimulationWorldState world,
            SimulationStartupConfig config,
            RuntimeKernel kernel,
            EnvironmentRuntime environment,
            MovementRuntime movement) {
        NeedSystem needs = new NeedSystem(world.objects, definitions.needs);
        ConsumableStockReductionRelay stockReductions = new ConsumableStockReductionRelay();
        ConsumableStockSystem consumableStocks = new ConsumableStockSystem(
                world.objects,
                definitions.consumableStock,
                stockReductions);
        for (ObjectId objectId : config.createdObjects()) {
            needs.attach(objectId);
            consumableStocks.attach(objectId);
        }

        NeedProgressionSystem needProgression = new NeedProgressionSystem(
                world.objects,
                definitions.needProgression,
                needs,
                needs,
                new IntrinsicNeedProgressionRateResolver(),
                kernel.clock);
        HandlerId needProgressionHandlerId = kernel.handlers.register(needProgression::resume);
        ProcessScheduler needProgressionScheduler = new BoundProcessScheduler(
                kernel.clock,
                kernel.scheduler,
                needProgressionHandlerId);
        needProgression.bindScheduler(needProgressionScheduler);
        for (ObjectId objectId : config.createdObjects()) {
            WorldObject object = world.objects.get(objectId);
            if (object != null && definitions.needProgression.has(object.definitionId())) {
                needProgression.activate(objectId);
            }
        }

        GrowthSystem growth = new GrowthSystem(
                world.objects,
                definitions.growth,
                consumableStocks,
                consumableStocks,
                new IntrinsicGrowthRateResolver(),
                kernel.clock);
        HandlerId growthHandlerId = kernel.handlers.register(growth::resume);
        ProcessScheduler growthScheduler = new BoundProcessScheduler(
                kernel.clock,
                kernel.scheduler,
                growthHandlerId);
        growth.bindScheduler(growthScheduler);
        stockReductions.bind(growth);
        for (ObjectId objectId : config.createdObjects()) {
            WorldObject object = world.objects.get(objectId);
            if (object != null && definitions.growth.has(object.definitionId())) {
                growth.activate(objectId);
            }
        }

        VisionSystem vision = new VisionSystem(
                world.objects,
                world.spatial.transforms(),
                world.cells.lookup(),
                world.orientations,
                definitions.vision,
                new TerrainSightOcclusionLookup(world.landscape.terrain()));
        AgentSearchSystem searches = new AgentSearchSystem(
                world.orientations,
                world.orientations,
                vision,
                CorrelatedRandomWalkExplorationPolicy.standard());
        RelativeSearchLocomotion searchLocomotion = new RelativeSearchLocomotion(
                world.spatial.transforms(),
                world.navigation.lookup(),
                vision,
                movement.moveTo(),
                movement.moveTo());

        NeedSatisfactionOpportunityProvider needSatisfaction = new NeedSatisfactionOpportunityProvider(
                world.objects,
                world.spatial.transforms(),
                definitions.agents,
                definitions.needSatisfaction,
                definitions.needSolutionKnowledge,
                definitions.needMotivation,
                needs,
                consumableStocks,
                kernel.clock);
        HandlerId needSatisfactionHandlerId = kernel.handlers.register(needSatisfaction::resume);
        ProcessScheduler needSatisfactionScheduler = new BoundProcessScheduler(
                kernel.clock,
                kernel.scheduler,
                needSatisfactionHandlerId);
        needSatisfaction.bindScheduler(needSatisfactionScheduler);

        List<AgentOpportunityProvider> opportunityProviders = new ArrayList<>();
        opportunityProviders.add(needSatisfaction);
        if (!definitions.liquidDrink.isEmpty()) {
            LiquidDrinkOpportunityProvider liquidDrinking = new LiquidDrinkOpportunityProvider(
                    world.objects,
                    world.spatial.transforms(),
                    definitions.liquidDrink,
                    definitions.needSolutionKnowledge,
                    definitions.needMotivation,
                    needs,
                    world.liquids,
                    config.physicalCellVolume(),
                    new InteractionAccessResolver(world.geometry),
                    environment.liquidFlowProcess()::activate,
                    kernel.clock);
            HandlerId liquidDrinkingHandlerId = kernel.handlers.register(liquidDrinking::resume);
            ProcessScheduler liquidDrinkingScheduler = new BoundProcessScheduler(
                    kernel.clock,
                    kernel.scheduler,
                    liquidDrinkingHandlerId);
            liquidDrinking.bindScheduler(liquidDrinkingScheduler);
            opportunityProviders.add(liquidDrinking);
        }

        AgentSystem agents = new AgentSystem(
                world.objects,
                world.spatial.transforms(),
                definitions.agents,
                List.copyOf(opportunityProviders),
                movement.moveTo(),
                movement.moveTo(),
                movement.destinationAccess(),
                vision,
                searches,
                searchLocomotion,
                kernel.clock);
        HandlerId agentHandlerId = kernel.handlers.register(agents::resume);
        ProcessScheduler agentScheduler = new BoundProcessScheduler(
                kernel.clock,
                kernel.scheduler,
                agentHandlerId);
        agents.bindScheduler(agentScheduler);
        for (ObjectId objectId : config.createdObjects()) {
            WorldObject object = world.objects.get(objectId);
            if (object != null && definitions.agents.has(object.definitionId())) {
                agents.activate(objectId);
            }
        }

        return new AgentRuntime(
                vision,
                needs,
                needProgression,
                consumableStocks,
                growth,
                agents,
                searches);
    }
}
