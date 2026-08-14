package io.github.evoforge.simulation.world.agent.search;

import io.github.evoforge.simulation.world.agent.perception.vision.VisionLookup;
import io.github.evoforge.simulation.world.object.ObjectId;
import io.github.evoforge.simulation.world.spatial.orientation.FacingDirection;
import io.github.evoforge.simulation.world.spatial.orientation.OrientationLookup;
import io.github.evoforge.simulation.world.spatial.orientation.OrientationMutations;
import java.util.HashMap;
import java.util.Map;

/** Owns current epistemic search state. The first strategy is a deterministic 360-degree visual sweep. */
public final class AgentSearchSystem implements AgentSearchLookup {
    private static final int HEADINGS_IN_LOCAL_SWEEP = 4;
    private final OrientationLookup orientations;
    private final OrientationMutations orientationMutations;
    private final VisionLookup vision;
    private final Map<ObjectId, SearchState> active = new HashMap<>();
    private final Map<ObjectId, AgentSearchTrace> last = new HashMap<>();

    public AgentSearchSystem(
            OrientationLookup orientations,
            OrientationMutations orientationMutations,
            VisionLookup vision) {
        if (orientations == null || orientationMutations == null || vision == null) {
            throw new IllegalArgumentException("search dependencies must not be null");
        }
        this.orientations = orientations;
        this.orientationMutations = orientationMutations;
        this.vision = vision;
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
            throw new IllegalStateException("local visual search requires orientation and vision: " + agentId);
        }

        SearchState state = active.get(agentId);
        if (state == null || !state.providerId.equals(providerId) || !state.motivation.equals(motivation)) {
            state = new SearchState(providerId, motivation, 1);
            active.put(agentId, state);
        }

        if (state.headingsObserved >= HEADINGS_IN_LOCAL_SWEEP) {
            AgentSearchTrace trace = new AgentSearchTrace(
                    agentId,
                    providerId,
                    motivation,
                    AgentSearchStatus.LOCAL_SWEEP_EXHAUSTED,
                    state.headingsObserved,
                    orientations.facing(agentId));
            active.remove(agentId);
            last.put(agentId, trace);
            return new SearchAdvanceResult(false, trace);
        }

        FacingDirection current = orientations.facing(agentId);
        FacingDirection next = FacingDirection.of(current.y(), -current.x());
        orientationMutations.faceIfPresent(agentId, next.x(), next.y());
        state.headingsObserved++;
        AgentSearchTrace trace = new AgentSearchTrace(
                agentId,
                providerId,
                motivation,
                AgentSearchStatus.SWEEPING,
                state.headingsObserved,
                orientations.facing(agentId));
        last.put(agentId, trace);
        return new SearchAdvanceResult(true, trace);
    }

    public void cancel(ObjectId agentId) {
        active.remove(agentId);
    }

    @Override
    public AgentSearchTrace currentSearch(ObjectId agentId) {
        SearchState state = agentId == null ? null : active.get(agentId);
        if (state == null) return null;
        return new AgentSearchTrace(
                agentId,
                state.providerId,
                state.motivation,
                AgentSearchStatus.SWEEPING,
                state.headingsObserved,
                orientations.facing(agentId));
    }

    @Override
    public AgentSearchTrace lastSearch(ObjectId agentId) {
        return agentId == null ? null : last.get(agentId);
    }

    private static final class SearchState {
        private final String providerId;
        private final String motivation;
        private int headingsObserved;

        private SearchState(String providerId, String motivation, int headingsObserved) {
            this.providerId = providerId;
            this.motivation = motivation;
            this.headingsObserved = headingsObserved;
        }
    }
}
