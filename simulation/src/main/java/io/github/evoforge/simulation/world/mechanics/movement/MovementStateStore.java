package io.github.evoforge.simulation.world.mechanics.movement;

import io.github.evoforge.simulation.world.mechanics.occupancy.OccupancyReservationId;
import io.github.evoforge.simulation.world.object.ObjectId;

import java.util.HashMap;
import java.util.Map;

public final class MovementStateStore {

    private final Map<ObjectId, ObjectState> states =
            new HashMap<>();

    private final Map<MovementActionId, MovementAction> actions =
            new HashMap<>();

    private final Map<MovementActionId, OccupancyReservationId> reservations =
            new HashMap<>();

    private long nextActionId;

    public boolean isMoving(
            ObjectId objectId) {

        ObjectState state = states.get(objectId);

        return state != null
                && state.activeActionId != null;
    }

    public long carry(
            ObjectId objectId) {

        if (objectId == null) {
            throw new IllegalArgumentException(
                    "objectId must not be null");
        }

        ObjectState state = states.get(objectId);

        return state == null
                ? 0
                : state.carry;
    }

    public void setCarry(
            ObjectId objectId,
            long carry) {

        if (objectId == null) {
            throw new IllegalArgumentException(
                    "objectId must not be null");
        }
        if (carry < 0) {
            throw new IllegalArgumentException(
                    "carry must be >= 0");
        }

        state(objectId).carry = carry;
    }

    public MovementAction createAction(
            ObjectId objectId,
            int fromX,
            int fromY,
            int fromZ,
            int toX,
            int toY,
            int toZ) {

        if (objectId == null) {
            throw new IllegalArgumentException(
                    "objectId must not be null");
        }

        ObjectState state = state(objectId);

        if (state.activeActionId != null) {
            throw new IllegalStateException(
                    "object is already moving: " + objectId);
        }

        if (nextActionId == Long.MAX_VALUE) {
            throw new IllegalStateException(
                    "movement action id space exhausted");
        }

        MovementActionId actionId =
                MovementActionId.of(nextActionId++);

        MovementAction action = new MovementAction(
                actionId,
                objectId,
                fromX,
                fromY,
                fromZ,
                toX,
                toY,
                toZ);

        actions.put(
                actionId,
                action);

        state.activeActionId = actionId;

        return action;
    }

    public void attachReservation(
            MovementActionId actionId,
            OccupancyReservationId reservationId) {

        if (actionId == null) {
            throw new IllegalArgumentException(
                    "actionId must not be null");
        }
        if (reservationId == null) {
            throw new IllegalArgumentException(
                    "reservationId must not be null");
        }
        if (!actions.containsKey(actionId)) {
            throw new IllegalArgumentException(
                    "movement action not found: " + actionId);
        }
        if (reservations.putIfAbsent(actionId, reservationId) != null) {
            throw new IllegalStateException(
                    "movement action already has a reservation: " + actionId);
        }
    }

    public OccupancyReservationId reservationId(
            MovementActionId actionId) {

        if (actionId == null) {
            return null;
        }
        return reservations.get(actionId);
    }

    public MovementAction get(
            MovementActionId actionId) {

        if (actionId == null) {
            return null;
        }

        return actions.get(actionId);
    }

    public MovementAction removeAction(
            MovementActionId actionId) {

        if (actionId == null) {
            return null;
        }

        MovementAction action = actions.remove(actionId);

        if (action == null) {
            return null;
        }

        reservations.remove(actionId);

        ObjectState state = states.get(
                action.objectId());

        if (state != null
                && actionId.equals(state.activeActionId)) {
            state.activeActionId = null;
        }

        return action;
    }

    public void discardObject(
            ObjectId objectId) {

        if (objectId == null) {
            return;
        }

        ObjectState state = states.remove(objectId);

        if (state != null
                && state.activeActionId != null) {
            actions.remove(state.activeActionId);
            reservations.remove(state.activeActionId);
        }
    }

    public int activeActionCount() {
        return actions.size();
    }

    private ObjectState state(
            ObjectId objectId) {

        return states.computeIfAbsent(
                objectId,
                ignored -> new ObjectState());
    }

    private static final class ObjectState {
        private long carry;
        private MovementActionId activeActionId;
    }
}
