package io.github.evoforge.simulation.world.agent.search;

import io.github.evoforge.simulation.world.agent.perception.vision.VisionLookup;
import io.github.evoforge.simulation.world.object.ObjectId;
import io.github.evoforge.simulation.world.spatial.orientation.FacingDirection;
import io.github.evoforge.simulation.world.spatial.orientation.OrientationLookup;
import io.github.evoforge.simulation.world.spatial.orientation.OrientationMutations;
import java.util.HashMap;
import java.util.Map;

/**
 * Owns epistemic search state. Search first sweeps local Vision and, when unguided,
 * may request one egocentric exploration step without receiving or storing world coordinates.
 */
public final class AgentSearchSystem implements AgentSearchLookup {
    private static final int HEADINGS_IN_LOCAL_SWEEP = 4;

    private final OrientationLookup orientations;
    private final OrientationMutations orientationMutations;
    private final VisionLookup vision;
    private final UnguidedExplorationPolicy explorationPolicy;
    private final Map<ObjectId, SearchState> active = new HashMap<>();
    private final Map<ObjectId, AgentSearchTrace> last = new HashMap<>();

    public AgentSearchSystem(
            OrientationLookup orientations,
            OrientationMutations orientationMutations,
            VisionLookup vision,
            UnguidedExplorationPolicy explorationPolicy) {
        if (orientations == null || orientationMutations == null || vision == null || explorationPolicy == null) {
            throw new IllegalArgumentException("search dependencies must not be null");
        }
        this.orientations = orientations;
        this.orientationMutations = orientationMutations;
        this.vision = vision;
        this.explorationPolicy = explorationPolicy;
    }

    public boolean supports(ObjectId agentId) {
        return agentId != null && orientations.has(agentId) && vision.snapshot(agentId) != null;
    }

    public SearchAdvanceResult advance(ObjectId agentId, String providerId, String motivation) {
        if (agentId == null || providerId == null || providerId.isBlank()
                || motivation == null || motivation.isBlank()) {
            throw new IllegalArgumentException("search request must not be null/blank");
        }
        if (!supports(agentId)) {
            throw new IllegalStateException("visual search requires orientation and vision: " + agentId);
        }

        SearchState state = active.get(agentId);
        if (state == null || !state.providerId.equals(providerId) || !state.motivation.equals(motivation)) {
            state = new SearchState(
                    providerId,
                    motivation,
                    1,
                    orientations.facing(agentId));
            active.put(agentId, state);
        }
        if (state.relocationPending) {
            throw new IllegalStateException("search relocation must finish before another search advance: " + agentId);
        }

        if (state.headingsObserved >= HEADINGS_IN_LOCAL_SWEEP) {
            FacingDirection nextHeading = explorationPolicy.nextHeading(
                    agentId,
                    state.explorationHeading,
                    state.explorationStepOrdinal++);
            state.explorationHeading = nextHeading;
            state.relocationPending = true;
            state.status = AgentSearchStatus.EXPLORING;
            AgentSearchTrace trace = trace(agentId, state);
            last.put(agentId, trace);
            return new SearchAdvanceResult(true, trace, new SearchRelocationRequest(nextHeading));
        }

        FacingDirection current = orientations.facing(agentId);
        FacingDirection next = FacingDirection.of(current.y(), -current.x());
        orientationMutations.faceIfPresent(agentId, next.x(), next.y());
        state.headingsObserved++;
        state.status = AgentSearchStatus.SWEEPING;
        AgentSearchTrace trace = trace(agentId, state);
        last.put(agentId, trace);
        return new SearchAdvanceResult(true, trace, null);
    }

    /** Completes the search-owned epistemic step after AgentSystem finishes its locomotion intent. */
    public void relocationFinished(ObjectId agentId, boolean reachedAdjacentPosition) {
        SearchState state = agentId == null ? null : active.get(agentId);
        if (state == null || !state.relocationPending) {
            throw new IllegalStateException("search relocation completion has no pending request: " + agentId);
        }
        state.relocationPending = false;
        state.headingsObserved = 1;
        if (reachedAdjacentPosition) {
            state.explorationHeading = orientations.facing(agentId);
            state.status = AgentSearchStatus.SWEEPING;
        } else {
            FacingDirection fallback = right(state.explorationHeading);
            orientationMutations.faceIfPresent(agentId, fallback.x(), fallback.y());
            state.explorationHeading = fallback;
            state.status = AgentSearchStatus.RELOCATION_BLOCKED;
        }
        last.put(agentId, trace(agentId, state));
    }

    public void cancel(ObjectId agentId) {
        active.remove(agentId);
    }

    @Override
    public AgentSearchTrace currentSearch(ObjectId agentId) {
        SearchState state = agentId == null ? null : active.get(agentId);
        return state == null ? null : trace(agentId, state);
    }

    @Override
    public AgentSearchTrace lastSearch(ObjectId agentId) {
        return agentId == null ? null : last.get(agentId);
    }

    private AgentSearchTrace trace(ObjectId agentId, SearchState state) {
        return new AgentSearchTrace(
                agentId,
                state.providerId,
                state.motivation,
                state.status,
                state.headingsObserved,
                orientations.facing(agentId));
    }

    private static FacingDirection right(FacingDirection heading) {
        return FacingDirection.of(heading.y(), -heading.x());
    }

    private static final class SearchState {
        private final String providerId;
        private final String motivation;
        private int headingsObserved;
        private FacingDirection explorationHeading;
        private long explorationStepOrdinal;
        private boolean relocationPending;
        private AgentSearchStatus status = AgentSearchStatus.SWEEPING;

        private SearchState(
                String providerId,
                String motivation,
                int headingsObserved,
                FacingDirection explorationHeading) {
            this.providerId = providerId;
            this.motivation = motivation;
            this.headingsObserved = headingsObserved;
            this.explorationHeading = explorationHeading;
        }
    }
}
