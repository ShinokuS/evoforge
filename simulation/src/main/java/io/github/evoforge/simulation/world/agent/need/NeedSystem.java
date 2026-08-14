package io.github.evoforge.simulation.world.agent.need;

import io.github.evoforge.simulation.world.object.ObjectId;
import io.github.evoforge.simulation.world.object.ObjectLookup;
import io.github.evoforge.simulation.world.object.WorldObject;
import io.github.evoforge.simulation.world.object.definition.ObjectDefinitionId;
import java.util.HashMap;
import java.util.Map;

/** Authoritative owner of mutable need levels for autonomous objects. */
public final class NeedSystem implements NeedLookup, NeedDeficitIncrease {

    private final ObjectLookup objects;
    private final NeedDefinitions definitions;
    private final Map<ObjectId, NeedState> states = new HashMap<>();

    public NeedSystem(ObjectLookup objects, NeedDefinitions definitions) {
        if (objects == null) throw new IllegalArgumentException("objects must not be null");
        if (definitions == null) throw new IllegalArgumentException("definitions must not be null");
        this.objects = objects;
        this.definitions = definitions;
    }

    public void attach(ObjectId objectId) {
        WorldObject object = objects.get(objectId);
        if (object == null) throw new IllegalArgumentException("object must be alive: " + objectId);
        if (states.containsKey(objectId)) throw new IllegalStateException("need state already attached: " + objectId);

        ObjectDefinitionId definitionId = object.definitionId();
        if (!definitions.has(definitionId)) return;

        int count = definitions.count(definitionId);
        NeedId[] ids = new NeedId[count];
        long[] levels = new long[count];
        long[] maxLevels = new long[count];
        for (int index = 0; index < count; index++) {
            NeedSpec spec = definitions.specAt(definitionId, index);
            ids[index] = spec.id();
            levels[index] = spec.initialLevel();
            maxLevels[index] = spec.maxLevel();
        }
        states.put(objectId, new NeedState(ids, levels, maxLevels));
    }

    @Override
    public boolean has(ObjectId objectId, NeedId needId) {
        NeedState state = states.get(objectId);
        return state != null && state.indexOf(needId) >= 0;
    }

    @Override
    public long level(ObjectId objectId, NeedId needId) {
        NeedState state = requireState(objectId);
        int index = state.indexOf(needId);
        if (index < 0) throw new IllegalArgumentException("need not present on object " + objectId + ": " + needId);
        return state.levels[index];
    }

    @Override
    public long maxLevel(ObjectId objectId, NeedId needId) {
        NeedState state = requireState(objectId);
        int index = state.indexOf(needId);
        if (index < 0) throw new IllegalArgumentException("need not present on object " + objectId + ": " + needId);
        return state.maxLevels[index];
    }

    @Override
    public int needCount(ObjectId objectId) {
        NeedState state = states.get(objectId);
        return state == null ? 0 : state.ids.length;
    }

    @Override
    public NeedId needAt(ObjectId objectId, int index) {
        return requireState(objectId).ids[index];
    }

    /** Applies satisfaction and returns the amount actually removed from the deficit. */
    public long satisfy(ObjectId objectId, NeedId needId, long amount) {
        if (amount <= 0) throw new IllegalArgumentException("amount must be > 0");
        NeedState state = states.get(objectId);
        if (state == null) return 0;
        int index = state.indexOf(needId);
        if (index < 0) return 0;
        long before = state.levels[index];
        long applied = Math.min(before, amount);
        state.levels[index] = before - applied;
        return applied;
    }

    /** Increases an existing deficit and returns the amount actually applied before reaching maxLevel. */
    @Override
    public long increase(ObjectId objectId, NeedId needId, long requestedAmount) {
        if (requestedAmount <= 0) throw new IllegalArgumentException("requestedAmount must be > 0");
        NeedState state = states.get(objectId);
        if (state == null) return 0;
        int index = state.indexOf(needId);
        if (index < 0) return 0;
        long before = state.levels[index];
        long remaining = state.maxLevels[index] - before;
        long applied = Math.min(remaining, requestedAmount);
        state.levels[index] = before + applied;
        return applied;
    }

    private NeedState requireState(ObjectId objectId) {
        NeedState state = states.get(objectId);
        if (state == null) throw new IllegalArgumentException("need state not found: " + objectId);
        return state;
    }

    private static final class NeedState {
        private final NeedId[] ids;
        private final long[] levels;
        private final long[] maxLevels;

        private NeedState(NeedId[] ids, long[] levels, long[] maxLevels) {
            this.ids = ids;
            this.levels = levels;
            this.maxLevels = maxLevels;
        }

        private int indexOf(NeedId needId) {
            if (needId == null) return -1;
            for (int index = 0; index < ids.length; index++) {
                if (ids[index].equals(needId)) return index;
            }
            return -1;
        }
    }
}
