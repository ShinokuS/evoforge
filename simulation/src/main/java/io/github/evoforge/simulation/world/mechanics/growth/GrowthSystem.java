package io.github.evoforge.simulation.world.mechanics.growth;

import io.github.evoforge.simulation.time.ProcessScheduler;
import io.github.evoforge.simulation.time.SimulationTime;
import io.github.evoforge.simulation.world.mechanics.consumption.ConsumableStockLookup;
import io.github.evoforge.simulation.world.mechanics.consumption.ConsumableStockReplenishment;
import io.github.evoforge.simulation.world.object.ObjectId;
import io.github.evoforge.simulation.world.object.ObjectLookup;
import io.github.evoforge.simulation.world.object.WorldObject;
import java.util.HashMap;
import java.util.Map;

/** Owns scheduled growth processes while delegating environmental rate semantics to a resolver. */
public final class GrowthSystem implements GrowthLookup {
    private final ObjectLookup objects;
    private final GrowthDefinitions definitions;
    private final ConsumableStockLookup stocks;
    private final ConsumableStockReplenishment replenishment;
    private final GrowthRateResolver rates;
    private final SimulationTime time;
    private final Map<Long, ActiveGrowth> byProcessId = new HashMap<>();
    private final Map<ObjectId, ActiveGrowth> byObjectId = new HashMap<>();
    private ProcessScheduler scheduler;
    private long nextProcessId;

    public GrowthSystem(
            ObjectLookup objects,
            GrowthDefinitions definitions,
            ConsumableStockLookup stocks,
            ConsumableStockReplenishment replenishment,
            GrowthRateResolver rates,
            SimulationTime time) {
        if (objects == null || definitions == null || stocks == null || replenishment == null
                || rates == null || time == null) {
            throw new IllegalArgumentException("growth dependencies must not be null");
        }
        this.objects = objects;
        this.definitions = definitions;
        this.stocks = stocks;
        this.replenishment = replenishment;
        this.rates = rates;
        this.time = time;
    }

    public void bindScheduler(ProcessScheduler scheduler) {
        if (scheduler == null) throw new IllegalArgumentException("scheduler must not be null");
        if (this.scheduler != null) throw new IllegalStateException("growth scheduler is already bound");
        this.scheduler = scheduler;
    }

    public void activate(ObjectId objectId) {
        requireScheduler();
        if (objectId == null) throw new IllegalArgumentException("objectId must not be null");
        if (byObjectId.containsKey(objectId)) throw new IllegalStateException("growth already active: " + objectId);
        WorldObject object = objects.get(objectId);
        if (object == null) throw new IllegalArgumentException("growth object must be alive: " + objectId);
        if (!definitions.has(object.definitionId())) {
            throw new IllegalArgumentException("object definition does not grow: " + object.definitionId());
        }
        if (!stocks.has(objectId)) {
            throw new IllegalStateException("growing object must own consumable stock: " + objectId);
        }
        if (nextProcessId == Long.MAX_VALUE) throw new IllegalStateException("growth process id space exhausted");
        GrowthDefinition definition = definitions.get(object.definitionId());
        ActiveGrowth active = new ActiveGrowth(nextProcessId++, objectId);
        byProcessId.put(active.processId, active);
        byObjectId.put(objectId, active);
        schedule(active, definition.intervalTicks());
    }

    public void resume(long processId) {
        requireScheduler();
        ActiveGrowth active = byProcessId.get(processId);
        if (active == null) throw new IllegalStateException("unknown growth process: " + processId);
        WorldObject object = objects.get(active.objectId);
        if (object == null) {
            byProcessId.remove(processId);
            byObjectId.remove(active.objectId);
            return;
        }
        GrowthDefinition definition = definitions.get(object.definitionId());
        long resolved = rates.resolve(active.objectId, definition);
        if (resolved < 0) throw new IllegalStateException("growth rate resolver returned negative amount: " + resolved);
        long applied = resolved == 0 ? 0 : replenishment.replenish(active.objectId, resolved);
        active.lastEvaluation = new GrowthTrace(
                time.tick(),
                resolved,
                applied,
                stocks.quantity(active.objectId),
                stocks.capacity(active.objectId));
        schedule(active, definition.intervalTicks());
    }

    @Override
    public boolean has(ObjectId objectId) {
        return objectId != null && byObjectId.containsKey(objectId);
    }

    @Override
    public long nextEvaluationTick(ObjectId objectId) {
        return requireActive(objectId).nextEvaluationTick;
    }

    @Override
    public GrowthTrace lastEvaluation(ObjectId objectId) {
        return requireActive(objectId).lastEvaluation;
    }

    private void schedule(ActiveGrowth active, long delayTicks) {
        try {
            active.nextEvaluationTick = Math.addExact(time.tick(), delayTicks);
        } catch (ArithmeticException exception) {
            throw new IllegalStateException("growth schedule tick overflow", exception);
        }
        scheduler.scheduleAfter(delayTicks, active.processId);
    }

    private ActiveGrowth requireActive(ObjectId objectId) {
        ActiveGrowth active = objectId == null ? null : byObjectId.get(objectId);
        if (active == null) throw new IllegalArgumentException("growth is not active: " + objectId);
        return active;
    }

    private void requireScheduler() {
        if (scheduler == null) throw new IllegalStateException("growth scheduler is not bound");
    }

    private static final class ActiveGrowth {
        private final long processId;
        private final ObjectId objectId;
        private long nextEvaluationTick;
        private GrowthTrace lastEvaluation;

        private ActiveGrowth(long processId, ObjectId objectId) {
            this.processId = processId;
            this.objectId = objectId;
        }
    }
}
