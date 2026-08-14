package io.github.evoforge.simulation.world.agent.search;

import io.github.evoforge.simulation.world.mechanics.geometry.TransitionMask;
import io.github.evoforge.simulation.world.mechanics.movement.MoveToActionId;
import io.github.evoforge.simulation.world.mechanics.movement.MoveToCompletion;
import io.github.evoforge.simulation.world.mechanics.movement.MoveToLookup;
import io.github.evoforge.simulation.world.mechanics.movement.MoveToStartAttempt;
import io.github.evoforge.simulation.world.mechanics.movement.MoveToSystem;
import io.github.evoforge.simulation.world.navigation.NavigationLookup;
import io.github.evoforge.simulation.world.object.ObjectId;
import io.github.evoforge.simulation.world.spatial.TransformLookup;
import io.github.evoforge.simulation.world.spatial.orientation.FacingDirection;

/**
 * Execution adapter from an egocentric search step into production MoveTo.
 * Absolute XYZ is used only here as physical execution state and is never returned to cognition/search.
 */
public final class RelativeSearchLocomotion {
    private static final int[] Z_PREFERENCE = { 0, 1, -1 };

    private final TransformLookup transforms;
    private final NavigationLookup navigation;
    private final MoveToSystem moveTo;
    private final MoveToLookup moveToLookup;

    public RelativeSearchLocomotion(
            TransformLookup transforms,
            NavigationLookup navigation,
            MoveToSystem moveTo,
            MoveToLookup moveToLookup) {
        if (transforms == null || navigation == null || moveTo == null || moveToLookup == null) {
            throw new IllegalArgumentException("search locomotion dependencies must not be null");
        }
        this.transforms = transforms;
        this.navigation = navigation;
        this.moveTo = moveTo;
        this.moveToLookup = moveToLookup;
    }

    public StartAttempt startStep(ObjectId objectId, FacingDirection heading) {
        if (objectId == null || heading == null) {
            throw new IllegalArgumentException("search relocation identity/heading must not be null");
        }
        if (!transforms.has(objectId)) {
            throw new IllegalStateException("search relocation object has no transform: " + objectId);
        }

        int x = transforms.x(objectId);
        int y = transforms.y(objectId);
        int z = transforms.z(objectId);
        int transitions = navigation.transitions(x, y, z);
        Integer dz = localDz(transitions, heading);
        if (dz == null) {
            return StartAttempt.rejected();
        }

        MoveToStartAttempt attempt = moveTo.start(
                objectId,
                x + heading.x(),
                y + heading.y(),
                z + dz);
        if (!attempt.accepted()) {
            return StartAttempt.rejected();
        }
        if (!moveToLookup.isActive(objectId)) {
            MoveToCompletion completion = moveToLookup.lastCompletion(objectId);
            if (completion == null || !attempt.actionId().equals(completion.actionId())) {
                throw new IllegalStateException("synchronous search relocation completion was lost: " + objectId);
            }
            if (!completion.reachedGoal()) {
                return StartAttempt.rejected();
            }
        }
        return StartAttempt.started(attempt.actionId());
    }

    private static Integer localDz(int transitions, FacingDirection heading) {
        for (int dz : Z_PREFERENCE) {
            if (TransitionMask.contains(transitions, heading.x(), heading.y(), dz)) {
                return dz;
            }
        }
        return null;
    }

    public record StartAttempt(boolean accepted, MoveToActionId actionId) {
        public StartAttempt {
            if (accepted != (actionId != null)) {
                throw new IllegalArgumentException("accepted search relocation must have exactly one action id");
            }
        }

        static StartAttempt started(MoveToActionId actionId) {
            return new StartAttempt(true, actionId);
        }

        static StartAttempt rejected() {
            return new StartAttempt(false, null);
        }
    }
}
