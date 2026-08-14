package io.github.evoforge.simulation.world.agent.search;

import io.github.evoforge.simulation.world.agent.perception.vision.VisionLookup;
import io.github.evoforge.simulation.world.agent.perception.vision.VisionSnapshot;
import io.github.evoforge.simulation.world.mechanics.movement.MoveToActionId;
import io.github.evoforge.simulation.world.mechanics.movement.MoveToCompletion;
import io.github.evoforge.simulation.world.mechanics.movement.MoveToLookup;
import io.github.evoforge.simulation.world.mechanics.movement.MoveToStartAttempt;
import io.github.evoforge.simulation.world.mechanics.movement.MoveToStarter;
import io.github.evoforge.simulation.world.mechanics.movement.MoveToSystem;
import io.github.evoforge.simulation.world.navigation.NavigationLookup;
import io.github.evoforge.simulation.world.object.ObjectId;
import io.github.evoforge.simulation.world.pathfinding.PathTransitionConstraint;
import io.github.evoforge.simulation.world.spatial.TransformLookup;

/**
 * Execution adapter from a coordinate-free relative search target into production MoveTo.
 * Absolute XYZ is used only here as physical execution state and is never returned to cognition/search.
 * Exploratory routing is constrained to cells present in the fresh Vision snapshot facing the selected point.
 */
public final class RelativeSearchLocomotion {
    private static final int[] Z_PREFERENCE = { 0, 1, -1 };

    private final TransformLookup transforms;
    private final NavigationLookup navigation;
    private final VisionLookup vision;
    private final MoveToStarter moveTo;
    private final MoveToLookup moveToLookup;

    public RelativeSearchLocomotion(
            TransformLookup transforms,
            NavigationLookup navigation,
            VisionLookup vision,
            MoveToSystem moveTo,
            MoveToLookup moveToLookup) {
        this(
                transforms,
                navigation,
                vision,
                MoveToStarter.direct(moveTo),
                moveToLookup);
    }

    public RelativeSearchLocomotion(
            TransformLookup transforms,
            NavigationLookup navigation,
            VisionLookup vision,
            MoveToStarter moveTo,
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
        if (navigation.transitions(originX, originY, originZ) == 0) {
            return StartAttempt.rejected();
        }

        Integer targetX = addWithinInt(originX, request.offsetX());
        Integer targetY = addWithinInt(originY, request.offsetY());
        if (targetX == null || targetY == null) return StartAttempt.rejected();

        Integer targetZ = visibleTargetZ(snapshot, targetX, targetY, originZ);
        if (targetZ == null) return StartAttempt.rejected();

        PathTransitionConstraint visibleOnly = (fromX, fromY, fromZ, toX, toY, toZ) ->
                snapshot.isCellVisible(fromX, fromY, fromZ)
                        && snapshot.isCellVisible(toX, toY, toZ);
        MoveToStartAttempt attempt = moveTo.start(
                objectId,
                targetX,
                targetY,
                targetZ,
                visibleOnly);
        if (!attempt.accepted()) return StartAttempt.rejected();
        if (!moveToLookup.isActive(objectId)) {
            MoveToCompletion completion = moveToLookup.lastCompletion(objectId);
            if (completion == null || !attempt.actionId().equals(completion.actionId())) {
                throw new IllegalStateException("synchronous search relocation completion was lost: " + objectId);
            }
            if (!completion.reachedGoal()) return StartAttempt.rejected();
        }
        return StartAttempt.started(attempt.actionId(), request.distance());
    }

    private static Integer visibleTargetZ(
            VisionSnapshot snapshot,
            int targetX,
            int targetY,
            int originZ) {
        for (int dz : Z_PREFERENCE) {
            long candidate = (long) originZ + dz;
            if (candidate < Integer.MIN_VALUE || candidate > Integer.MAX_VALUE) continue;
            int targetZ = (int) candidate;
            if (snapshot.isCellVisible(targetX, targetY, targetZ)) return targetZ;
        }
        return null;
    }

    private static Integer addWithinInt(int value, int delta) {
        long result = (long) value + delta;
        return result < Integer.MIN_VALUE || result > Integer.MAX_VALUE ? null : (int) result;
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
