package io.github.evoforge.simulation.world.mechanics.growth;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.evoforge.simulation.definition.DefinitionRegistry;
import io.github.evoforge.simulation.time.ProcessScheduler;
import io.github.evoforge.simulation.time.SimulationClock;
import io.github.evoforge.simulation.world.mechanics.consumption.ConsumableStockDefinition;
import io.github.evoforge.simulation.world.mechanics.consumption.ConsumableStockDefinitions;
import io.github.evoforge.simulation.world.mechanics.consumption.ConsumableStockSystem;
import io.github.evoforge.simulation.world.object.ObjectFactory;
import io.github.evoforge.simulation.world.object.ObjectId;
import io.github.evoforge.simulation.world.object.ObjectRepository;
import io.github.evoforge.simulation.world.object.definition.ObjectDefinitionId;
import org.junit.jupiter.api.Test;

final class GrowthRateResolverTest {

    @Test
    void injectedResolverCanSuppressAndBoostGrowthWithoutChangingGrowthSystem() {
        DefinitionRegistry<ObjectDefinitionId> objectDefinitions =
                new DefinitionRegistry<>(ObjectDefinitionId::of, ObjectDefinitionId::asInt);
        ObjectDefinitionId plantDefinition = objectDefinitions.register("test:resolver_plant");
        ObjectRepository objects = new ObjectRepository();
        ObjectFactory factory = new ObjectFactory(objects, objectDefinitions);
        ObjectId plant = factory.create(plantDefinition).id();

        ConsumableStockDefinitions stockDefinitions = new ConsumableStockDefinitions();
        stockDefinitions.put(plantDefinition, new ConsumableStockDefinition(20, 2));
        stockDefinitions.freeze();
        ConsumableStockSystem stocks = new ConsumableStockSystem(objects, stockDefinitions);
        stocks.attach(plant);

        GrowthDefinitions growthDefinitions = new GrowthDefinitions();
        growthDefinitions.put(plantDefinition, new GrowthDefinition(2, 4));
        growthDefinitions.freeze();

        long[] resolvedAmount = {0};
        GrowthRateResolver resolver = (objectId, definition) -> resolvedAmount[0];
        SimulationClock clock = new SimulationClock();
        CapturingScheduler scheduler = new CapturingScheduler();
        GrowthSystem growth = new GrowthSystem(
                objects,
                growthDefinitions,
                stocks,
                stocks,
                resolver,
                clock);
        growth.bindScheduler(scheduler);
        growth.activate(plant);

        advance(clock, 4);
        growth.resume(scheduler.processId);
        assertEquals(2, stocks.quantity(plant));
        assertEquals(0, growth.lastEvaluation(plant).resolvedAmount());

        resolvedAmount[0] = 7;
        advance(clock, 4);
        growth.resume(scheduler.processId);
        assertEquals(9, stocks.quantity(plant));
        assertEquals(7, growth.lastEvaluation(plant).resolvedAmount());
        assertEquals(7, growth.lastEvaluation(plant).appliedAmount());
    }

    private static void advance(SimulationClock clock, int ticks) {
        for (int tick = 0; tick < ticks; tick++) clock.advance();
    }

    private static final class CapturingScheduler implements ProcessScheduler {
        private long processId = -1;

        @Override
        public void scheduleAfter(long delayTicks, long processId) {
            this.processId = processId;
        }
    }
}
