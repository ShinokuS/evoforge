package io.github.evoforge.simulation.world.agent.search;

import io.github.evoforge.simulation.world.agent.perception.vision.VisionLookup;
import io.github.evoforge.simulation.world.agent.perception.vision.VisionSnapshot;
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
 * Execution adapter from an egocentric search leg into production MoveTo.
 * Absolute XYZ is used only here as physical execution state and is never returned to cognition/search.
 * A leg may target only a contiguous locally traversable ray of cells present in the current Vision snapshot.
 */
public final class RelativeSearchLocomotion {
    private static final int[] Z_PREFERENCE = { 0, 1, -1 };

    private final TransformLookup transforms;
    private final NavigationLookup navigation;
    private final VisionLookup vision;
    private final MoveToSystem moveTo;
    private final MoveToLookup moveToLookup;

    public RelativeSearchLocomotion(
            TransformLookup transforms,
            NavigationLookup navigation,
            VisionLookup vision,
            MoveToSystem moveTo,
            MoveToLookup moveToLookup) {
        if (transforms == null || navigation == null || vision == null || moveTo == null || moveToLookup == null) {
            throw new IllegalArgumentException("search locomotion dependencies must not be null");
        }
        this.transforms = transforms;
        this.navigation = navigation;
        this.vision = vision;
        this.moveTo = moveTo;
        this.moveToLookup = moveToLookup;
    }

    public StartAttempt startLeg(ObjectId objectId, SearchRelocationRequest request) {
        if (objectId == null || request == null) {
            throw new IllegalArgumentException("search relocation identity/request must not be null");
        }
        if (!transforms.has(objectId)) {
            throw new IllegalStateException("search relocation object has no transform: " + objectId);
        }
        VisionSnapshot snapshot = vision.snapshot(objectId);
        if (snapshot == null) {
            throw new IllegalStateException("search relocation object has no Vision snapshot: " + objectId);
        }

        int originX = transforms.x(objectId);
        int originY = transforms.y(objectId);
        int originZ = transforms.z(objectId);
        if (snapshot.originX() != originX || snapshot.originY() != originY || snapshot.originZ() != originZ) {
            throw new IllegalStateException("search relocation Vision origin is stale: " + objectId);
        }

        FacingDirection heading = request.heading();
        int x = originX;
        int y = originY;
        int z = originZ;
        int reached = 0;
        for (int step = 0; step < request.distance(); step++) {
            int transitions = navigation.transitions(x, y, z);
            Integer dz = localDz(transitions, heading);
            if (dz == null) break;
            int nextX = x + heading.x();
            int nextY = y + heading.y();
            int nextZ = z + dz;
            if (!snapshot.isCellVisible(nextX, nextY, nextZ)) break;
            x = nextX;
            y = nextY;
            z = nextZ;
            reached++;
        }
        if (reached == 0) return StartAttempt.rejected();

        MoveToStartAttempt attempt = moveTo.start(objectId, x, y, z);
        if (!attempt.accepted()) return StartAttempt.rejected();
        if (!moveToLookup.isActive(objectId)) {
            MoveToCompletion completion = moveToLookup.lastCompletion(objectId);
            if (completion == null || !attempt.actionId().equals(completion.actionId())) {
                throw new IllegalStateException("synchronous search relocation completion was lost: " + objectId);
            }
            if (!completion.reachedGoal()) return StartAttempt.rejected();
        }
        return StartAttempt.started(attempt.actionId(), reached);
    }

    private static Integer localDz(int transitions, FacingDirection heading) {
        for (int dz : Z_PREFERENCE) {
            if (TransitionMask.contains(transitions, heading.x(), heading.y(), dz)) return dz;
        }
        return null;
    }

    public record StartAttempt(boolean accepted, MoveToActionId actionId, int distance) {
        public StartAttempt {
            if (accepted != (actionId != null) || distance < 0 || accepted && distance == 0) {
                throw new IllegalArgumentException("search relocation start attempt is inconsistent");
            }
        }

        static StartAttempt started(MoveToActionId actionId, int distance) {
            return new StartAttempt(true, actionId, distance);
        }

        static StartAttempt rejected() {
            return new StartAttempt(false, null, 0);
        }
    }
}
