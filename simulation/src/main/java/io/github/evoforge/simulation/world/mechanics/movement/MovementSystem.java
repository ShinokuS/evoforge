package io.github.evoforge.simulation.world.mechanics.movement;

import io.github.evoforge.simulation.time.ProcessScheduler;
import io.github.evoforge.simulation.world.mechanics.geometry.TransitionMask;
import io.github.evoforge.simulation.world.mechanics.occupancy.OccupancyReservationAttempt;
import io.github.evoforge.simulation.world.mechanics.occupancy.OccupancyReservationId;
import io.github.evoforge.simulation.world.mechanics.occupancy.OccupancyReservationResult;
import io.github.evoforge.simulation.world.mechanics.occupancy.OccupancySystem;
import io.github.evoforge.simulation.world.mechanics.traversal.TransitionCostLookup;
import io.github.evoforge.simulation.world.navigation.NavigationLookup;
import io.github.evoforge.simulation.world.object.ObjectId;
import io.github.evoforge.simulation.world.object.ObjectLookup;
import io.github.evoforge.simulation.world.object.WorldObject;
import io.github.evoforge.simulation.world.spatial.TransformLookup;

public final class MovementSystem {

    private final ObjectLookup objects;
    private final TransformLookup transforms;
    private final NavigationLookup navigation;
    private final MovementDefinitions definitions;
    private final TransitionCostLookup transitionCosts;
    private final OccupancySystem occupancy;
    private final MovementStateStore state;
    private final ProcessScheduler scheduler;

    public MovementSystem(
            ObjectLookup objects,
            TransformLookup transforms,
            NavigationLookup navigation,
            MovementDefinitions definitions,
            TransitionCostLookup transitionCosts,
            OccupancySystem occupancy,
            MovementStateStore state,
            ProcessScheduler scheduler) {

        if (objects == null) {
            throw new IllegalArgumentException(
                    "objects must not be null");
        }
        if (transforms == null) {
            throw new IllegalArgumentException(
                    "transforms must not be null");
        }
        if (navigation == null) {
            throw new IllegalArgumentException(
                    "navigation must not be null");
        }
        if (definitions == null) {
            throw new IllegalArgumentException(
                    "definitions must not be null");
        }
        if (transitionCosts == null) {
            throw new IllegalArgumentException(
                    "transitionCosts must not be null");
        }
        if (occupancy == null) {
            throw new IllegalArgumentException(
                    "occupancy must not be null");
        }
        if (state == null) {
            throw new IllegalArgumentException(
                    "state must not be null");
        }
        if (scheduler == null) {
            throw new IllegalArgumentException(
                    "scheduler must not be null");
        }

        this.objects = objects;
        this.transforms = transforms;
        this.navigation = navigation;
        this.definitions = definitions;
        this.transitionCosts = transitionCosts;
        this.occupancy = occupancy;
        this.state = state;
        this.scheduler = scheduler;
    }

    public MovementStartResult startStep(
            ObjectId objectId,
            int toX,
            int toY,
            int toZ) {

        if (objectId == null) {
            throw new IllegalArgumentException(
                    "objectId must not be null");
        }

        WorldObject object = objects.get(objectId);

        if (object == null) {
            throw new IllegalArgumentException(
                    "unknown object: " + objectId);
        }

        if (!definitions.has(object.definitionId())) {
            return MovementStartResult.MOVEMENT_UNAVAILABLE;
        }

        if (!transforms.has(objectId)) {
            return MovementStartResult.NOT_PLACED;
        }

        if (state.isMoving(objectId)) {
            return MovementStartResult.ALREADY_MOVING;
        }

        int fromX = transforms.x(objectId);
        int fromY = transforms.y(objectId);
        int fromZ = transforms.z(objectId);

        long dxLong = (long) toX - fromX;
        long dyLong = (long) toY - fromY;
        long dzLong = (long) toZ - fromZ;

        if (dxLong < -1 || dxLong > 1
                || dyLong < -1 || dyLong > 1
                || dzLong < -1 || dzLong > 1
                || dxLong == 0 && dyLong == 0 && dzLong == 0) {
            return MovementStartResult.NOT_ADJACENT;
        }

        int dx = (int) dxLong;
        int dy = (int) dyLong;
        int dz = (int) dzLong;

        if (!TransitionMask.contains(
                navigation.transitions(
                        fromX,
                        fromY,
                        fromZ),
                dx,
                dy,
                dz)) {
            return MovementStartResult.TRANSITION_UNAVAILABLE;
        }

        long rate = definitions.rate(
                object.definitionId())
                .unitsPerTick();

        long carry = state.carry(objectId);

        if (carry >= rate) {
            throw new IllegalStateException(
                    "movement carry must be less than rate");
        }

        long cost = transitionCosts.cost(
                fromX,
                fromY,
                fromZ,
                toX,
                toY,
                toZ)
                .units();

        Timing timing = timing(
                cost,
                rate,
                carry);

        MovementAction action = state.createAction(
                objectId,
                fromX,
                fromY,
                fromZ,
                toX,
                toY,
                toZ);

        OccupancyReservationAttempt reservation = occupancy.tryReserve(
                objectId,
                toX,
                toY,
                toZ);

        if (reservation.result() == OccupancyReservationResult.OCCUPIED) {
            state.removeAction(action.id());
            return MovementStartResult.DESTINATION_OCCUPIED;
        }
        if (reservation.result() == OccupancyReservationResult.RESERVED) {
            state.removeAction(action.id());
            return MovementStartResult.DESTINATION_RESERVED;
        }

        OccupancyReservationId reservationId =
                reservation.reservationId();

        try {
            if (reservationId != null) {
                state.attachReservation(
                        action.id(),
                        reservationId);
            }

            scheduler.scheduleAfter(
                    timing.ticks(),
                    action.id().asLong());
        } catch (RuntimeException exception) {
            if (reservationId != null) {
                rollbackReservation(
                        reservationId,
                        objectId,
                        toX,
                        toY,
                        toZ,
                        exception);
            }
            state.removeAction(action.id());
            throw exception;
        }

        state.setCarry(
                objectId,
                timing.nextCarry());

        return MovementStartResult.STARTED;
    }

    private void rollbackReservation(
            OccupancyReservationId reservationId,
            ObjectId objectId,
            int x,
            int y,
            int z,
            RuntimeException failure) {

        try {
            if (!occupancy.release(
                    reservationId,
                    objectId,
                    x,
                    y,
                    z)) {
                failure.addSuppressed(new IllegalStateException(
                        "failed to roll back occupancy reservation: "
                                + reservationId));
            }
        } catch (RuntimeException rollbackFailure) {
            if (rollbackFailure != failure) {
                failure.addSuppressed(rollbackFailure);
            }
        }
    }

    private static Timing timing(
            long cost,
            long rate,
            long carry) {

        long ticks = cost / rate;
        long remainder = cost % rate;
        long nextCarry;

        if (remainder == 0) {
            nextCarry = carry;
        } else {
            long untilNextTick = rate - remainder;

            if (carry >= untilNextTick) {
                ticks++;
                nextCarry = carry - untilNextTick;
            } else {
                nextCarry = carry + remainder;
            }
        }

        return new Timing(
                Math.max(1, ticks),
                nextCarry);
    }

    private record Timing(
            long ticks,
            long nextCarry) {
    }
}
