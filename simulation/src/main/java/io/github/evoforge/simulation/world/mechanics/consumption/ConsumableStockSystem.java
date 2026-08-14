package io.github.evoforge.simulation.world.mechanics.consumption;

import io.github.evoforge.simulation.world.object.ObjectId;
import io.github.evoforge.simulation.world.object.ObjectLookup;
import io.github.evoforge.simulation.world.object.WorldObject;
import java.util.HashMap;
import java.util.Map;

/** Authoritative owner of mutable consumable quantities for object instances. */
public final class ConsumableStockSystem implements ConsumableStockLookup, ConsumableStockReplenishment {
    private final ObjectLookup objects;
    private final ConsumableStockDefinitions definitions;
    private final Map<ObjectId, State> states = new HashMap<>();

    public ConsumableStockSystem(ObjectLookup objects, ConsumableStockDefinitions definitions) {
        if (objects == null || definitions == null) {
            throw new IllegalArgumentException("consumable stock dependencies must not be null");
        }
        this.objects = objects;
        this.definitions = definitions;
    }

    public void attach(ObjectId objectId) {
        if (objectId == null) throw new IllegalArgumentException("objectId must not be null");
        if (states.containsKey(objectId)) throw new IllegalStateException("consumable stock already attached: " + objectId);
        WorldObject object = objects.get(objectId);
        if (object == null) throw new IllegalArgumentException("object must be alive: " + objectId);
        if (!definitions.has(object.definitionId())) return;
        ConsumableStockDefinition definition = definitions.get(object.definitionId());
        states.put(objectId, new State(definition.capacity(), definition.initialQuantity()));
    }

    /** Removes exactly requested quantity when available, otherwise removes nothing. */
    public boolean consume(ObjectId objectId, long requestedQuantity) {
        if (requestedQuantity <= 0) throw new IllegalArgumentException("requestedQuantity must be > 0");
        State state = requireState(objectId);
        if (state.quantity < requestedQuantity) return false;
        state.quantity -= requestedQuantity;
        return true;
    }

    /** Adds up to requested quantity without exceeding authoritative capacity and returns the actual addition. */
    @Override
    public long replenish(ObjectId objectId, long requestedQuantity) {
        if (requestedQuantity <= 0) throw new IllegalArgumentException("requestedQuantity must be > 0");
        State state = requireState(objectId);
        long available = state.capacity - state.quantity;
        long added = Math.min(requestedQuantity, available);
        state.quantity += added;
        return added;
    }

    @Override
    public boolean has(ObjectId objectId) {
        return objectId != null && states.containsKey(objectId);
    }

    @Override
    public long quantity(ObjectId objectId) {
        return requireState(objectId).quantity;
    }

    @Override
    public long capacity(ObjectId objectId) {
        return requireState(objectId).capacity;
    }

    private State requireState(ObjectId objectId) {
        State state = objectId == null ? null : states.get(objectId);
        if (state == null) throw new IllegalArgumentException("consumable stock not attached: " + objectId);
        return state;
    }

    private static final class State {
        private final long capacity;
        private long quantity;

        private State(long capacity, long quantity) {
            this.capacity = capacity;
            this.quantity = quantity;
        }
    }
}
