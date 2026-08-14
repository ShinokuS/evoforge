package io.github.evoforge.simulation.world.mechanics.growth;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.evoforge.simulation.definition.DefinitionRegistry;
import io.github.evoforge.simulation.time.ProcessScheduler;
import io.github.evoforge.simulation.time.SimulationClock;
import io.github.evoforge.simulation.world.mechanics.consumption.ConsumableStockDefinition;
import io.github.evoforge.simulation.world.mechanics.consumption.ConsumableStockDefinitions;
import io.github.evoforge.simulation.world.mechanics.consumption.ConsumableStockReductionRelay;
import io.github.evoforge.simulation.world.mechanics.consumption.ConsumableStockSystem;
import io.github.evoforge.simulation.world.object.ObjectFactory;
import io.github.evoforge.simulation.world.object.ObjectId;
import io.github.evoforge.simulation.world.object.ObjectRepository;
import io.github.evoforge.simulation.world.object.definition.ObjectDefinitionId;
import org.junit.jupiter.api.Test;

final class GrowthDormancyTest {

    @Test
    void fullStockSleepsUntilAuthoritativeConsumptionWakesGrowth() {
        DefinitionRegistry<ObjectDefinitionId> objectDefinitions =
                new DefinitionRegistry<>(ObjectDefinitionId::of, ObjectDefinitionId::asInt);
        ObjectDefinitionId plantDefinition = objectDefinitions.register("test:dormant_plant");
        ObjectRepository objects = new ObjectRepository();
        ObjectFactory factory = new ObjectFactory(objects, objectDefinitions);
        ObjectId plant = factory.create(plantDefinition).id();

        ConsumableStockDefinitions stockDefinitions = new ConsumableStockDefinitions();
        stockDefinitions.put(plantDefinition, new ConsumableStockDefinition(4, 4));
        stockDefinitions.freeze();
        ConsumableStockReductionRelay reductions = new ConsumableStockReductionRelay();
        ConsumableStockSystem stocks = new ConsumableStockSystem(objects, stockDefinitions, reductions);
        stocks.attach(plant);

        GrowthDefinitions growthDefinitions = new GrowthDefinitions();
        growthDefinitions.put(plantDefinition, new GrowthDefinition(1, 5));
        growthDefinitions.freeze();

        SimulationClock clock = new SimulationClock();
        CapturingScheduler scheduler = new CapturingScheduler();
        GrowthSystem growth = new GrowthSystem(
                objects,
                growthDefinitions,
                stocks,
                stocks,
                new IntrinsicGrowthRateResolver(),
                clock);
        growth.bindScheduler(scheduler);
        reductions.bind(growth);
        growth.activate(plant);

        assertEquals(GrowthStatus.DORMANT_FULL, growth.status(plant));
        assertEquals(-1, growth.nextEvaluationTick(plant));
        assertEquals(0, scheduler.scheduleCount);

        advance(clock, 20);
        assertEquals(0, scheduler.scheduleCount);

        stocks.consume(plant, 1);
        assertEquals(GrowthStatus.GROWING, growth.status(plant));
        assertEquals(25, growth.nextEvaluationTick(plant));
        assertEquals(1, scheduler.scheduleCount);

        advance(clock, 5);
        growth.resume(scheduler.processId);
        assertEquals(4, stocks.quantity(plant));
        assertEquals(GrowthStatus.DORMANT_FULL, growth.status(plant));
        assertEquals(-1, growth.nextEvaluationTick(plant));
        assertEquals(1, scheduler.scheduleCount);
    }

    private static void advance(SimulationClock clock, int ticks) {
        for (int tick = 0; tick < ticks; tick++) clock.advance();
    }

    private static final class CapturingScheduler implements ProcessScheduler {
        private long processId = -1;
        private int scheduleCount;

        @Override
        public void scheduleAfter(long delayTicks, long processId) {
            this.processId = processId;
            scheduleCount++;
        }
    }
}
