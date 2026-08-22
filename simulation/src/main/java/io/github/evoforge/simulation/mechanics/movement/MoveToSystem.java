package io.github.evoforge.simulation.mechanics.movement;

import io.github.evoforge.simulation.kernel.operation.ResultCode;
import io.github.evoforge.simulation.world.object.ObjectId;
import io.github.evoforge.simulation.world.navigation.pathfinding.PathQuery;
import io.github.evoforge.simulation.world.navigation.pathfinding.PathRoute;
import io.github.evoforge.simulation.world.navigation.pathfinding.PathSearch;
import io.github.evoforge.simulation.world.navigation.pathfinding.PathSearchStatus;
import io.github.evoforge.simulation.world.navigation.pathfinding.PathTransitionConstraint;
import io.github.evoforge.simulation.world.navigation.pathfinding.Pathfinder;
import io.github.evoforge.simulation.world.spatial.TransformLookup;
import java.util.HashMap;
import java.util.Map;

/**
 * Long-range Movement orchestration over the existing concrete timed edge.
 * Pathfinding is disposable advice; every real edge still goes through MovementSystem.
 */
public final class MoveToSystem implements MovementStepCompletionSink, MoveToView {
    private static final int PATH_EXPANSION_CHUNK = 4096;

    private static final ResultCode STARTED = ResultCode.of("movement", "move_to_started");
    private static final ResultCode GOAL_REACHED = ResultCode.of("movement", "goal_reached");
    private static final ResultCode NO_PATH = ResultCode.of("movement", "no_path");
    private static final ResultCode PATH_STALE = ResultCode.of("movement", "path_stale");
    private static final ResultCode CANCEL_REQUESTED = ResultCode.of("movement", "move_to_cancel_requested");
    private static final ResultCode CANCELLED = ResultCode.of("movement", "move_to_cancelled");
    private static final ResultCode NO_ACTIVE_MOVE_TO = ResultCode.of("movement", "no_active_move_to");

    private final TransformLookup transforms;
    private final Pathfinder pathfinder;
    private final MovementSystem movement;
    private final MoveToQueryConstraintProvider queryConstraints;
    private final MoveToCompletionSink completionSink;

    private final Map<ObjectId, ActiveMoveTo> activeByObject = new HashMap<>();
    private final Map<MovementActionId, ActiveMoveTo> activeByChild = new HashMap<>();
    private final Map<ObjectId, MoveToCompletion> lastCompletionByObject = new HashMap<>();
    private long nextActionId;

    public MoveToSystem(
            TransformLookup transforms,
            Pathfinder pathfinder,
            MovementSystem movement) {
        this(
                transforms,
                pathfinder,
                movement,
                MoveToQueryConstraintProvider.IDENTITY,
                new MoveToCompletionRelay());
    }

    public MoveToSystem(
            TransformLookup transforms,
            Pathfinder pathfinder,
            MovementSystem movement,
            MoveToQueryConstraintProvider queryConstraints) {
        this(
                transforms,
                pathfinder,
                movement,
                queryConstraints,
                new MoveToCompletionRelay());
    }

    public MoveToSystem(
            TransformLookup transforms,
            Pathfinder pathfinder,
            MovementSystem movement,
            MoveToQueryConstraintProvider queryConstraints,
            MoveToCompletionSink completionSink) {
        if (transforms == null) throw new IllegalArgumentException("transforms must not be null");
        if (pathfinder == null) throw new IllegalArgumentException("pathfinder must not be null");
        if (movement == null) throw new IllegalArgumentException("movement must not be null");
        if (queryConstraints == null) throw new IllegalArgumentException("queryConstraints must not be null");
        if (completionSink == null) throw new IllegalArgumentException("completionSink must not be null");
        this.transforms = transforms;
        this.pathfinder = pathfinder;
        this.movement = movement;
        this.queryConstraints = queryConstraints;
        this.completionSink = completionSink;
    }

    /** Binds the autonomous completion consumer when this MoveTo uses its default bootstrap relay. */
    public void bindCompletionSink(MoveToCompletionSink target) {
        if (!(completionSink instanceof MoveToCompletionRelay relay)) {
            throw new IllegalStateException("MoveTo completion sink was supplied explicitly");
        }
        relay.bind(target);
    }

    public MoveToStartAttempt start(ObjectId objectId, int goalX, int goalY, int goalZ) {
        return start(objectId, goalX, goalY, goalZ, PathTransitionConstraint.ALLOW_ALL);
    }

    /** Starts MoveTo with a query-local advisory route constraint; physical edges still revalidate normally. */
    public MoveToStartAttempt start(
            ObjectId objectId,
            int goalX,
            int goalY,
            int goalZ,
            PathTransitionConstraint constraint) {
        if (constraint == null) throw new IllegalArgumentException("constraint must not be null");

        MovementClaimAttempt claim = movement.acquireClaim(objectId);
        if (!claim.accepted()) return MoveToStartAttempt.rejected(claim.code());

        MoveToActionId actionId = nextActionId();
        ActiveMoveTo active = new ActiveMoveTo(
                actionId,
                objectId,
                claim.claimId(),
                goalX,
                goalY,
                goalZ,
                constraint);

        if (activeByObject.putIfAbsent(objectId, active) != null) {
            releaseAfterFailure(active, null);
            throw new IllegalStateException(
                    "MoveTo state exists without a movement claim conflict: " + objectId);
        }
        lastCompletionByObject.remove(objectId);

        try {
            planAndStart(active);
        } catch (RuntimeException failure) {
            releaseAfterFailure(active, failure);
            throw failure;
        }

        return MoveToStartAttempt.started(STARTED, actionId);
    }

    /**
     * Starts disposable planning with the same mover-specific constraint composition
     * as authoritative MoveTo, but without acquiring a movement claim.
     */
    @Override
    public PathSearch beginPreview(ObjectId objectId, int goalX, int goalY, int goalZ) {
        if (objectId == null) throw new IllegalArgumentException("objectId must not be null");
        if (!transforms.has(objectId)) throw new IllegalArgumentException("object has no transform: " + objectId);
        return beginPathSearch(
                objectId,
                goalX,
                goalY,
                goalZ,
                PathTransitionConstraint.ALLOW_ALL);
    }

    /**
     * Requests cancellation of an active long-range order.
     *
     * <p>An already scheduled atomic Movement edge is allowed to complete so its
     * occupancy reservation and scheduler state remain consistent. No further edge
     * is started after that completion. If no edge is currently active, cancellation
     * completes immediately.</p>
     */
    public MoveToCancelAttempt cancel(ObjectId objectId) {
        if (objectId == null) throw new IllegalArgumentException("objectId must not be null");
        ActiveMoveTo active = activeByObject.get(objectId);
        if (active == null) return MoveToCancelAttempt.rejected(NO_ACTIVE_MOVE_TO);

        active.cancelRequested = true;
        if (active.childActionId == null) finish(active, false, CANCELLED);
        return MoveToCancelAttempt.accepted(CANCEL_REQUESTED);
    }

    @Override
    public void completed(MovementStepCompletion completion) {
        if (completion == null) throw new IllegalArgumentException("completion must not be null");
        ActiveMoveTo active = activeByChild.remove(completion.actionId());
        if (active == null) return;
        if (!active.objectId.equals(completion.objectId())
                || !completion.actionId().equals(active.childActionId)) {
            throw new IllegalStateException("movement completion does not match active MoveTo child");
        }
        active.childActionId = null;

        if (active.cancelRequested) {
            finish(active, false, CANCELLED);
            return;
        }
        if (!completion.committed()) {
            finish(active, false, completion.code());
            return;
        }

        active.nextStepIndex++;
        try {
            startNextStep(active);
        } catch (RuntimeException failure) {
            releaseAfterFailure(active, failure);
            throw failure;
        }
    }

    @Override
    public boolean isActive(ObjectId objectId) {
        return objectId != null && activeByObject.containsKey(objectId);
    }

    @Override
    public MoveToActionId activeActionId(ObjectId objectId) {
        ActiveMoveTo active = objectId == null ? null : activeByObject.get(objectId);
        return active == null ? null : active.actionId;
    }

    @Override
    public PathRoute activeRoute(ObjectId objectId) {
        ActiveMoveTo active = objectId == null ? null : activeByObject.get(objectId);
        return active == null ? null : active.route;
    }

    @Override
    public MoveToCompletion lastCompletion(ObjectId objectId) {
        return objectId == null ? null : lastCompletionByObject.get(objectId);
    }

    private void planAndStart(ActiveMoveTo active) {
        if (!transforms.has(active.objectId)) {
            throw new IllegalStateException(
                    "claimed MoveTo object lost its transform before planning: " + active.objectId);
        }

        PathSearch search = beginPathSearch(
                active.objectId,
                active.goalX,
                active.goalY,
                active.goalZ,
                active.constraint);
        PathSearchStatus status = search.status();
        while (status == PathSearchStatus.RUNNING) status = search.advance(PATH_EXPANSION_CHUNK);

        if (status == PathSearchStatus.NO_PATH) {
            finish(active, false, NO_PATH);
            return;
        }
        if (status == PathSearchStatus.STALE) {
            finish(active, false, PATH_STALE);
            return;
        }
        if (status == PathSearchStatus.CANCELLED) {
            throw new IllegalStateException("MoveTo path search was cancelled unexpectedly");
        }
        if (status != PathSearchStatus.FOUND) {
            throw new IllegalStateException("unexpected terminal path status: " + status);
        }

        PathRoute route = search.route();
        if (route == null) throw new IllegalStateException("found path search has no route");
        active.route = route;
        startNextStep(active);
    }

    private PathSearch beginPathSearch(
            ObjectId objectId,
            int goalX,
            int goalY,
            int goalZ,
            PathTransitionConstraint callerConstraint) {
        PathTransitionConstraint constraint = queryConstraints.constraintFor(objectId, callerConstraint);
        if (constraint == null) throw new IllegalStateException("MoveTo query constraint provider returned null");

        PathSearch search = pathfinder.begin(
                PathQuery.between(
                                transforms.x(objectId),
                                transforms.y(objectId),
                                transforms.z(objectId),
                                goalX,
                                goalY,
                                goalZ)
                        .withConstraint(constraint));
        if (search == null) throw new IllegalStateException("pathfinder returned null search");
        return search;
    }

    private void startNextStep(ActiveMoveTo active) {
        if (active.route == null) throw new IllegalStateException("MoveTo has no route");
        if (active.childActionId != null) throw new IllegalStateException("MoveTo already has an active movement child");
        if (active.cancelRequested) {
            finish(active, false, CANCELLED);
            return;
        }
        if (active.nextStepIndex >= active.route.size()) {
            finish(active, true, GOAL_REACHED);
            return;
        }

        MovementStartAttempt attempt = movement.startClaimedStep(
                active.claimId,
                active.objectId,
                active.route.x(active.nextStepIndex),
                active.route.y(active.nextStepIndex),
                active.route.z(active.nextStepIndex));
        if (!attempt.accepted()) {
            finish(active, false, attempt.code());
            return;
        }

        active.childActionId = attempt.actionId();
        ActiveMoveTo previous = activeByChild.putIfAbsent(attempt.actionId(), active);
        if (previous != null) {
            throw new IllegalStateException(
                    "movement action is already owned by another MoveTo: " + attempt.actionId());
        }
    }

    private void finish(ActiveMoveTo active, boolean reachedGoal, ResultCode code) {
        if (active.childActionId != null) {
            throw new IllegalStateException("cannot finish MoveTo with an active movement child");
        }
        if (!activeByObject.remove(active.objectId, active)) {
            throw new IllegalStateException("active MoveTo ownership was lost: " + active.actionId);
        }
        if (!movement.releaseClaim(active.claimId, active.objectId)) {
            throw new IllegalStateException("failed to release MoveTo movement claim: " + active.claimId);
        }

        MoveToCompletion completion = new MoveToCompletion(
                active.actionId,
                active.objectId,
                reachedGoal,
                code);
        lastCompletionByObject.put(active.objectId, completion);
        completionSink.completed(completion);
    }

    private void releaseAfterFailure(ActiveMoveTo active, RuntimeException failure) {
        if (active.childActionId != null) {
            activeByChild.remove(active.childActionId, active);
            active.childActionId = null;
        }
        activeByObject.remove(active.objectId, active);

        try {
            if (!movement.releaseClaim(active.claimId, active.objectId)) {
                IllegalStateException releaseFailure = new IllegalStateException(
                        "failed to release movement claim after MoveTo failure: " + active.claimId);
                if (failure == null) throw releaseFailure;
                failure.addSuppressed(releaseFailure);
            }
        } catch (RuntimeException releaseFailure) {
            if (failure == null) throw releaseFailure;
            if (releaseFailure != failure) failure.addSuppressed(releaseFailure);
        }
    }

    private MoveToActionId nextActionId() {
        if (nextActionId == Long.MAX_VALUE) throw new IllegalStateException("MoveTo action id space exhausted");
        return MoveToActionId.of(nextActionId++);
    }

    private static final class ActiveMoveTo {
        private final MoveToActionId actionId;
        private final ObjectId objectId;
        private final MovementClaimId claimId;
        private final int goalX;
        private final int goalY;
        private final int goalZ;
        private final PathTransitionConstraint constraint;
        private PathRoute route;
        private int nextStepIndex;
        private MovementActionId childActionId;
        private boolean cancelRequested;

        private ActiveMoveTo(
                MoveToActionId actionId,
                ObjectId objectId,
                MovementClaimId claimId,
                int goalX,
                int goalY,
                int goalZ,
                PathTransitionConstraint constraint) {
            this.actionId = actionId;
            this.objectId = objectId;
            this.claimId = claimId;
            this.goalX = goalX;
            this.goalY = goalY;
            this.goalZ = goalZ;
            this.constraint = constraint;
        }
    }
}
