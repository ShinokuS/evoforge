package io.github.evoforge.simulation.world.mechanics.movement;

import io.github.evoforge.simulation.result.ResultCode;
import io.github.evoforge.simulation.time.ProcessScheduler;
import io.github.evoforge.simulation.world.mechanics.geometry.TransitionMask;
import io.github.evoforge.simulation.world.mechanics.occupancy.OccupancyReservationAttempt;
import io.github.evoforge.simulation.world.mechanics.occupancy.OccupancyReservationId;
import io.github.evoforge.simulation.world.mechanics.occupancy.OccupancyReservationResult;
import io.github.evoforge.simulation.world.mechanics.occupancy.OccupancySystem;
import io.github.evoforge.simulation.world.mechanics.traversal.MoverTraversalConstraint;
import io.github.evoforge.simulation.world.mechanics.traversal.TransitionCostLookup;
import io.github.evoforge.simulation.world.navigation.NavigationLookup;
import io.github.evoforge.simulation.world.object.ObjectId;
import io.github.evoforge.simulation.world.object.ObjectLookup;
import io.github.evoforge.simulation.world.object.WorldObject;
import io.github.evoforge.simulation.world.spatial.TransformLookup;

public final class MovementSystem {

    private static final ResultCode STARTED =
            ResultCode.of("movement", "started");
    private static final ResultCode CLAIM_ACQUIRED =
            ResultCode.of("movement", "claim_acquired");
    private static final ResultCode MOVEMENT_UNAVAILABLE =
            ResultCode.of("movement", "movement_unavailable");
    private static final ResultCode NOT_PLACED =
            ResultCode.of("movement", "not_placed");
    private static final ResultCode ALREADY_MOVING =
            ResultCode.of("movement", "already_moving");
    private static final ResultCode NOT_ADJACENT =
            ResultCode.of("movement", "not_adjacent");
    private static final ResultCode TRANSITION_UNAVAILABLE =
            ResultCode.of("movement", "transition_unavailable");
    private static final ResultCode TRAVERSAL_RESTRICTED =
            ResultCode.of("movement", "traversal_restricted");
    private static final ResultCode DESTINATION_OCCUPIED =
            ResultCode.of("movement", "destination_occupied");
    private static final ResultCode DESTINATION_RESERVED =
            ResultCode.of("movement", "destination_reserved");

    private final ObjectLookup objects;
    private final TransformLookup transforms;
    private final NavigationLookup navigation;
    private final MovementDefinitions definitions;
    private final TransitionCostLookup transitionCosts;
    private final MoverTraversalConstraint traversalConstraint;
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

        this(
                objects,
                transforms,
                navigation,
                definitions,
                transitionCosts,
                MoverTraversalConstraint.ALLOW_ALL,
                occupancy,
                state,
                scheduler);
    }

    public MovementSystem(
            ObjectLookup objects,
            TransformLookup transforms,
            NavigationLookup navigation,
            MovementDefinitions definitions,
            TransitionCostLookup transitionCosts,
            MoverTraversalConstraint traversalConstraint,
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
        if (traversalConstraint == null) {
            throw new IllegalArgumentException(
                    "traversalConstraint must not be null");
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
        this.traversalConstraint = traversalConstraint;
        this.occupancy = occupancy;
        this.state = state;
        this.scheduler = scheduler;
    }

    public MovementClaimAttempt acquireClaim(
            ObjectId objectId) {

        WorldObject object = requireObject(objectId);

        if (!definitions.has(object.definitionId())) {
            return MovementClaimAttempt.rejected(
                    MOVEMENT_UNAVAILABLE);
        }
        if (!transforms.has(objectId)) {
            return MovementClaimAttempt.rejected(
                    NOT_PLACED);
        }
        if (state.isMoving(objectId)
                || state.hasClaim(objectId)) {
            return MovementClaimAttempt.rejected(
                    ALREADY_MOVING);
        }

        MovementClaimId claimId =
                state.tryAcquireClaim(objectId);
        if (claimId == null) {
            throw new IllegalStateException(
                    "movement claim became unavailable during acquisition: "
                            + objectId);
        }

        return MovementClaimAttempt.acquired(
                CLAIM_ACQUIRED,
                claimId);
    }

    public boolean releaseClaim(
            MovementClaimId claimId,
            ObjectId objectId) {
        return state.releaseClaim(
                claimId,
                objectId);
    }

    public MovementStartAttempt startStep(
            ObjectId objectId,
            int toX,
            int toY,
            int toZ) {

        return startStep(
                null,
                objectId,
                toX,
                toY,
                toZ);
    }

    public MovementStartAttempt startClaimedStep(
            MovementClaimId claimId,
            ObjectId objectId,
            int toX,
            int toY,
            int toZ) {

        if (claimId == null) {
            throw new IllegalArgumentException(
                    "claimId must not be null");
        }
        if (!state.ownsClaim(claimId, objectId)) {
            throw new IllegalStateException(
                    "movement claim is not owned by object: claim="
                            + claimId + ", object=" + objectId);
        }

        return startStep(
                claimId,
                objectId,
                toX,
                toY,
                toZ);
    }

    private MovementStartAttempt startStep(
            MovementClaimId claimId,
            ObjectId objectId,
            int toX,
            int toY,
            int toZ) {

        WorldObject object = requireObject(objectId);

        if (!definitions.has(object.definitionId())) {
            return MovementStartAttempt.rejected(
                    MOVEMENT_UNAVAILABLE);
        }

        if (!transforms.has(objectId)) {
            return MovementStartAttempt.rejected(
                    NOT_PLACED);
        }

        if (claimId == null && state.hasClaim(objectId)) {
            return MovementStartAttempt.rejected(
                    ALREADY_MOVING);
        }

        if (state.isMoving(objectId)) {
            return MovementStartAttempt.rejected(
                    ALREADY_MOVING);
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
            return MovementStartAttempt.rejected(
                    NOT_ADJACENT);
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
            return MovementStartAttempt.rejected(
                    TRANSITION_UNAVAILABLE);
        }

        if (!traversalConstraint.allows(
                objectId,
                fromX,
                fromY,
                fromZ,
                toX,
                toY,
                toZ)) {
            return MovementStartAttempt.rejected(
                    TRAVERSAL_RESTRICTED);
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

        OccupancyReservationAttempt reservation = occupancy.tryReserve(
                objectId,
                toX,
                toY,
                toZ);

        if (reservation.result() == OccupancyReservationResult.OCCUPIED) {
            return MovementStartAttempt.rejected(
                    DESTINATION_OCCUPIED);
        }
        if (reservation.result() == OccupancyReservationResult.RESERVED) {
            return MovementStartAttempt.rejected(
                    DESTINATION_RESERVED);
        }

        OccupancyReservationId reservationId = reservation.reservationId();
        MovementAction action = null;

        try {
            action = state.createAction(
                    objectId,
                    fromX,
                    fromY,
                    fromZ,
                    toX,
                    toY,
                    toZ);

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
            if (action != null) {
                state.removeAction(action.id());
            }
            throw exception;
        }

        state.setCarry(
                objectId,
                timing.nextCarry());

        return MovementStartAttempt.started(
                STARTED,
                action.id());
    }

    private WorldObject requireObject(
            ObjectId objectId) {

        if (objectId == null) {
            throw new IllegalArgumentException(
                    "objectId must not be null");
        }
        WorldObject object = objects.get(objectId);
        if (object == null) {
            throw new IllegalArgumentException(
                    "unknown object: " + objectId);
        }
        return object;
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
