package io.github.evoforge.simulation.world.mechanics.movement;

import io.github.evoforge.simulation.world.mechanics.geometry.TransitionMask;
import io.github.evoforge.simulation.world.navigation.NavigationLookup;
import io.github.evoforge.simulation.world.object.ObjectLookup;
import io.github.evoforge.simulation.world.spatial.SpatialSystem;
import io.github.evoforge.simulation.world.spatial.TransformLookup;

public final class MovementActionProcessor {

    private final MovementStateStore state;
    private final ObjectLookup objects;
    private final TransformLookup transforms;
    private final NavigationLookup navigation;
    private final SpatialSystem spatial;

    public MovementActionProcessor(
            MovementStateStore state,
            ObjectLookup objects,
            TransformLookup transforms,
            NavigationLookup navigation,
            SpatialSystem spatial) {

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
        if (spatial == null) {
            throw new IllegalArgumentException(
                    "spatial must not be null");
        }

        this.state = state;
        this.objects = objects;
        this.transforms = transforms;
        this.navigation = navigation;
        this.spatial = spatial;
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

        if (!objects.isAlive(action.objectId())) {
            state.discardObject(action.objectId());
            return;
        }

        if (!transforms.has(action.objectId())) {
            state.removeAction(actionId);
            return;
        }

        if (transforms.x(action.objectId()) != action.fromX()
                || transforms.y(action.objectId()) != action.fromY()
                || transforms.z(action.objectId()) != action.fromZ()) {
            state.removeAction(actionId);
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
            state.removeAction(actionId);
            return;
        }

        spatial.move(
                action.objectId(),
                action.toX(),
                action.toY(),
                action.toZ());

        state.removeAction(actionId);
    }
}
