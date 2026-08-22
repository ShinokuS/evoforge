package io.github.evoforge.simulation.agents.need.progression;

import io.github.evoforge.simulation.kernel.scheduling.ProcessScheduler;
import io.github.evoforge.simulation.kernel.time.SimulationTime;
import io.github.evoforge.simulation.agents.need.NeedDeficitIncrease;
import io.github.evoforge.simulation.agents.need.NeedId;
import io.github.evoforge.simulation.agents.need.NeedLookup;
import io.github.evoforge.simulation.world.object.ObjectId;
import io.github.evoforge.simulation.world.object.ObjectLookup;
import io.github.evoforge.simulation.world.object.WorldObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Owns scheduled Need progression while delegating effective-rate semantics to a resolver. */
public final class NeedProgressionSystem implements NeedProgressionLookup {
    private final ObjectLookup objects;
    private final NeedProgressionDefinitions definitions;
    private final NeedLookup needs;
    private final NeedDeficitIncrease deficitIncrease;
    private final NeedProgressionRateResolver rates;
    private final SimulationTime time;
    private final Map<Long, ActiveProgression> byProcessId = new HashMap<>();
    private final Map<ObjectId, List<ActiveProgression>> byObjectId = new HashMap<>();
    private ProcessScheduler scheduler;
    private long nextProcessId;

    public NeedProgressionSystem(
            ObjectLookup objects,
            NeedProgressionDefinitions definitions,
            NeedLookup needs,
            NeedDeficitIncrease deficitIncrease,
            NeedProgressionRateResolver rates,
            SimulationTime time) {
        if (objects == null || definitions == null || needs == null || deficitIncrease == null
                || rates == null || time == null) {
            throw new IllegalArgumentException("need progression dependencies must not be null");
        }
        this.objects = objects;
        this.definitions = definitions;
        this.needs = needs;
        this.deficitIncrease = deficitIncrease;
        this.rates = rates;
        this.time = time;
    }

    public void bindScheduler(ProcessScheduler scheduler) {
        if (scheduler == null) throw new IllegalArgumentException("scheduler must not be null");
        if (this.scheduler != null) throw new IllegalStateException("need progression scheduler is already bound");
        this.scheduler = scheduler;
    }

    public void activate(ObjectId objectId) {
        requireScheduler();
        if (objectId == null) throw new IllegalArgumentException("objectId must not be null");
        if (byObjectId.containsKey(objectId)) {
            throw new IllegalStateException("need progression already active: " + objectId);
        }
        WorldObject object = objects.get(objectId);
        if (object == null) throw new IllegalArgumentException("progressing object must be alive: " + objectId);
        if (!definitions.has(object.definitionId())) {
            throw new IllegalArgumentException("object definition has no need progression: " + object.definitionId());
        }

        List<ActiveProgression> active = new ArrayList<>();
        for (int index = 0; index < definitions.count(object.definitionId()); index++) {
            NeedProgressionDefinition definition = definitions.definitionAt(object.definitionId(), index);
            if (!needs.has(objectId, definition.needId())) {
                throw new IllegalStateException(
                        "need progression requires declared Need " + definition.needId() + " on object " + objectId);
            }
            if (nextProcessId == Long.MAX_VALUE) {
                throw new IllegalStateException("need progression process id space exhausted");
            }
            ActiveProgression process = new ActiveProgression(nextProcessId++, objectId, definition.needId());
            active.add(process);
            byProcessId.put(process.processId, process);
            schedule(process, definition.intervalTicks());
        }
        byObjectId.put(objectId, List.copyOf(active));
    }

    public void resume(long processId) {
        requireScheduler();
        ActiveProgression active = byProcessId.get(processId);
        if (active == null) throw new IllegalStateException("unknown need progression process: " + processId);
        WorldObject object = objects.get(active.objectId);
        if (object == null) {
            byProcessId.remove(processId);
            removeObjectProcess(active);
            return;
        }

        NeedProgressionDefinition definition = definitions.get(object.definitionId(), active.needId);
        if (definition == null) {
            throw new IllegalStateException("active need progression definition disappeared: " + active.needId);
        }
        long resolved = rates.resolve(active.objectId, definition);
        if (resolved < 0) {
            throw new IllegalStateException("need progression resolver returned negative amount: " + resolved);
        }
        long applied = resolved == 0 ? 0 : deficitIncrease.increase(active.objectId, active.needId, resolved);
        active.lastEvaluation = new NeedProgressionTrace(
                time.tick(),
                active.needId,
                resolved,
                applied,
                needs.level(active.objectId, active.needId),
                needs.maxLevel(active.objectId, active.needId));
        schedule(active, definition.intervalTicks());
    }

    @Override
    public boolean has(ObjectId objectId, NeedId needId) {
        return find(objectId, needId) != null;
    }

    @Override
    public long nextEvaluationTick(ObjectId objectId, NeedId needId) {
        return requireActive(objectId, needId).nextEvaluationTick;
    }

    @Override
    public NeedProgressionTrace lastEvaluation(ObjectId objectId, NeedId needId) {
        return requireActive(objectId, needId).lastEvaluation;
    }

    private void schedule(ActiveProgression active, long delayTicks) {
        try {
            active.nextEvaluationTick = Math.addExact(time.tick(), delayTicks);
        } catch (ArithmeticException exception) {
            throw new IllegalStateException("need progression schedule tick overflow", exception);
        }
        scheduler.scheduleAfter(delayTicks, active.processId);
    }

    private ActiveProgression find(ObjectId objectId, NeedId needId) {
        List<ActiveProgression> list = objectId == null ? null : byObjectId.get(objectId);
        if (list == null || needId == null) return null;
        for (ActiveProgression active : list) {
            if (active.needId.equals(needId)) return active;
        }
        return null;
    }

    private ActiveProgression requireActive(ObjectId objectId, NeedId needId) {
        ActiveProgression active = find(objectId, needId);
        if (active == null) {
            throw new IllegalArgumentException("need progression is not active: " + objectId + " / " + needId);
        }
        return active;
    }

    private void removeObjectProcess(ActiveProgression removed) {
        List<ActiveProgression> list = byObjectId.get(removed.objectId);
        if (list == null) return;
        List<ActiveProgression> remaining = new ArrayList<>(list.size());
        for (ActiveProgression active : list) {
            if (active.processId != removed.processId) remaining.add(active);
        }
        if (remaining.isEmpty()) byObjectId.remove(removed.objectId);
        else byObjectId.put(removed.objectId, List.copyOf(remaining));
    }

    private void requireScheduler() {
        if (scheduler == null) throw new IllegalStateException("need progression scheduler is not bound");
    }

    private static final class ActiveProgression {
        private final long processId;
        private final ObjectId objectId;
        private final NeedId needId;
        private long nextEvaluationTick;
        private NeedProgressionTrace lastEvaluation;

        private ActiveProgression(long processId, ObjectId objectId, NeedId needId) {
            this.processId = processId;
            this.objectId = objectId;
            this.needId = needId;
        }
    }
}
