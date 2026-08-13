package io.github.evoforge.simulation.world.mechanics.movement;

import io.github.evoforge.simulation.result.ResultCode;
import io.github.evoforge.simulation.world.mechanics.geometry.TransitionMask;
import io.github.evoforge.simulation.world.mechanics.occupancy.OccupancyReservationId;
import io.github.evoforge.simulation.world.mechanics.occupancy.OccupancySystem;
import io.github.evoforge.simulation.world.navigation.NavigationLookup;
import io.github.evoforge.simulation.world.object.ObjectLookup;
import io.github.evoforge.simulation.world.spatial.SpatialSystem;
import io.github.evoforge.simulation.world.spatial.TransformLookup;

public final class MovementActionProcessor {

    private static final ResultCode COMMITTED =
            ResultCode.of("movement", "committed");
    private static final ResultCode OBJECT_REMOVED =
            ResultCode.of("movement", "object_removed");
    private static final ResultCode NOT_PLACED =
            ResultCode.of("movement", "not_placed");
    private static final ResultCode SOURCE_CHANGED =
            ResultCode.of("movement", "source_changed");
    private static final ResultCode TRANSITION_UNAVAILABLE =
            ResultCode.of("movement", "transition_unavailable");
    private static final ResultCode DESTINATION_UNAVAILABLE =
            ResultCode.of("movement", "destination_unavailable");

    private final MovementStateStore state;
    private final ObjectLookup objects;
    private final TransformLookup transforms;
    private final NavigationLookup navigation;
    private final OccupancySystem occupancy;
    private final SpatialSystem spatial;
    private final MovementStepCompletionSink completions;

    public MovementActionProcessor(
            MovementStateStore state,
            ObjectLookup objects,
            TransformLookup transforms,
            NavigationLookup navigation,
            OccupancySystem occupancy,
            SpatialSystem spatial,
            MovementStepCompletionSink completions) {

        if (state == null) {
            throw new IllegalArgumentException(
                    "state must not be null");
        }
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
        if (occupancy == null) {
            throw new IllegalArgumentException(
                    "occupancy must not be null");
        }
        if (spatial == null) {
            throw new IllegalArgumentException(
                    "spatial must not be null");
        }
        if (completions == null) {
            throw new IllegalArgumentException(
                    "completions must not be null");
        }

        this.state = state;
        this.objects = objects;
        this.transforms = transforms;
        this.navigation = navigation;
        this.occupancy = occupancy;
        this.spatial = spatial;
        this.completions = completions;
    }

    public void complete(
            long processId) {

        MovementActionId actionId =
                MovementActionId.of(processId);

        MovementAction action = state.get(actionId);

        if (action == null) {
            throw new IllegalStateException(
                    "movement action not found: " + actionId);
        }

        OccupancyReservationId reservationId =
                state.reservationId(actionId);

        if (!objects.isAlive(action.objectId())) {
            releaseReservation(action, reservationId);
            state.discardObject(action.objectId());
            emit(action, false, OBJECT_REMOVED);
            return;
        }

        if (occupancy.requiresExclusiveCell(action.objectId())
                && reservationId == null) {
            throw new IllegalStateException(
                    "exclusive movement action has no occupancy reservation: "
                            + actionId);
        }

        if (!transforms.has(action.objectId())) {
            finish(action, reservationId, false, NOT_PLACED);
            return;
        }

        if (transforms.x(action.objectId()) != action.fromX()
                || transforms.y(action.objectId()) != action.fromY()
                || transforms.z(action.objectId()) != action.fromZ()) {
            finish(action, reservationId, false, SOURCE_CHANGED);
            return;
        }

        int dx = action.toX() - action.fromX();
        int dy = action.toY() - action.fromY();
        int dz = action.toZ() - action.fromZ();

        if (!TransitionMask.contains(
                navigation.transitions(
                        action.fromX(),
                        action.fromY(),
                        action.fromZ()),
                dx,
                dy,
                dz)) {
            finish(
                    action,
                    reservationId,
                    false,
                    TRANSITION_UNAVAILABLE);
            return;
        }

        if (reservationId != null) {
            if (!occupancy.ownsReservation(
                    reservationId,
                    action.objectId(),
                    action.toX(),
                    action.toY(),
                    action.toZ())) {
                throw new IllegalStateException(
                        "movement action lost its occupancy reservation: "
                                + actionId);
            }

            if (!occupancy.canCommit(
                    reservationId,
                    action.objectId(),
                    action.toX(),
                    action.toY(),
                    action.toZ())) {
                finish(
                        action,
                        reservationId,
                        false,
                        DESTINATION_UNAVAILABLE);
                return;
            }
        }

        spatial.move(
                action.objectId(),
                action.toX(),
                action.toY(),
                action.toZ());

        finish(
                action,
                reservationId,
                true,
                COMMITTED);
    }

    private void finish(
            MovementAction action,
            OccupancyReservationId reservationId,
            boolean committed,
            ResultCode code) {

        releaseReservation(action, reservationId);
        MovementAction removed =
                state.removeAction(action.id());
        if (removed != action) {
            throw new IllegalStateException(
                    "movement action state changed during completion: "
                            + action.id());
        }
        emit(action, committed, code);
    }

    private void emit(
            MovementAction action,
            boolean committed,
            ResultCode code) {

        completions.completed(
                new MovementStepCompletion(
                        action.id(),
                        action.objectId(),
                        committed,
                        code));
    }

    private void releaseReservation(
            MovementAction action,
            OccupancyReservationId reservationId) {

        if (reservationId == null) {
            return;
        }

        if (!occupancy.release(
                reservationId,
                action.objectId(),
                action.toX(),
                action.toY(),
                action.toZ())) {
            throw new IllegalStateException(
                    "failed to release movement occupancy reservation: "
                            + reservationId);
        }
    }
}
