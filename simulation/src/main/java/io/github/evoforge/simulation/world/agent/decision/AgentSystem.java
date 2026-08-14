package io.github.evoforge.simulation.world.agent.decision;

import io.github.evoforge.simulation.time.ProcessScheduler;
import io.github.evoforge.simulation.time.SimulationTime;
import io.github.evoforge.simulation.world.agent.AgentDefinition;
import io.github.evoforge.simulation.world.agent.AgentDefinitions;
import io.github.evoforge.simulation.world.agent.opportunity.AgentOpportunityProvider;
import io.github.evoforge.simulation.world.agent.opportunity.OpportunityEvaluation;
import io.github.evoforge.simulation.world.agent.opportunity.OpportunityUseResult;
import io.github.evoforge.simulation.world.mechanics.movement.MoveToActionId;
import io.github.evoforge.simulation.world.mechanics.movement.MoveToCompletion;
import io.github.evoforge.simulation.world.mechanics.movement.MoveToLookup;
import io.github.evoforge.simulation.world.mechanics.movement.MoveToStartAttempt;
import io.github.evoforge.simulation.world.mechanics.movement.MoveToSystem;
import io.github.evoforge.simulation.world.object.ObjectId;
import io.github.evoforge.simulation.world.object.ObjectLookup;
import io.github.evoforge.simulation.world.object.WorldObject;
import io.github.evoforge.simulation.world.spatial.CellObjectLookup;
import io.github.evoforge.simulation.world.spatial.TransformLookup;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * First generic autonomous decision owner. It perceives bounded nearby objects, asks registered
 * mechanic providers what they currently offer, chooses one candidate deterministically, and
 * delegates locomotion to the existing MoveTo domain.
 */
public final class AgentSystem implements AgentDecisionLookup {

    private static final long ACTIVE_POLL_TICKS = 1L;
    private static final long IDLE_RECHECK_TICKS = 10L;

    private static final Comparator<Candidate> CANDIDATE_ORDER =
            Comparator.<Candidate>comparingLong(candidate -> candidate.trace.score()).reversed()
                    .thenComparingInt(candidate -> candidate.trace.distance())
                    .thenComparingLong(candidate -> candidate.trace.sourceId().asLong())
                    .thenComparingInt(candidate -> candidate.providerIndex);

    private final ObjectLookup objects;
    private final TransformLookup transforms;
    private final CellObjectLookup cells;
    private final AgentDefinitions definitions;
    private final List<AgentOpportunityProvider> providers;
    private final MoveToSystem moveTo;
    private final MoveToLookup moveToLookup;
    private final SimulationTime time;

    private final Map<Long, ActiveAgent> byProcessId = new HashMap<>();
    private final Map<ObjectId, ActiveAgent> byObjectId = new HashMap<>();
    private final Map<ObjectId, AgentDecisionTrace> lastDecisionByObject = new HashMap<>();

    private ProcessScheduler scheduler;
    private long nextProcessId;

    public AgentSystem(
            ObjectLookup objects,
            TransformLookup transforms,
            CellObjectLookup cells,
            AgentDefinitions definitions,
            List<AgentOpportunityProvider> providers,
            MoveToSystem moveTo,
            MoveToLookup moveToLookup,
            SimulationTime time) {
        if (objects == null || transforms == null || cells == null || definitions == null
                || providers == null || moveTo == null || moveToLookup == null || time == null) {
            throw new IllegalArgumentException("agent system dependencies must not be null");
        }
        this.objects = objects;
        this.transforms = transforms;
        this.cells = cells;
        this.definitions = definitions;
        this.providers = validateProviders(providers);
        this.moveTo = moveTo;
        this.moveToLookup = moveToLookup;
        this.time = time;
    }

    public void bindScheduler(ProcessScheduler scheduler) {
        if (scheduler == null) {
            throw new IllegalArgumentException("scheduler must not be null");
        }
        if (this.scheduler != null) {
            throw new IllegalStateException("agent scheduler is already bound");
        }
        this.scheduler = scheduler;
    }

    public void activate(ObjectId objectId) {
        requireScheduler();
        WorldObject object = objects.get(objectId);
        if (object == null) {
            throw new IllegalArgumentException("agent object must be alive: " + objectId);
        }
        if (!definitions.has(object.definitionId())) {
            throw new IllegalArgumentException("object definition is not autonomous: " + object.definitionId());
        }
        if (!transforms.has(objectId)) {
            throw new IllegalStateException("autonomous object must have a transform before activation: " + objectId);
        }
        if (byObjectId.containsKey(objectId)) {
            throw new IllegalStateException("agent is already active: " + objectId);
        }
        if (nextProcessId == Long.MAX_VALUE) {
            throw new IllegalStateException("agent process id space exhausted");
        }

        ActiveAgent active = new ActiveAgent(nextProcessId++, objectId);
        byProcessId.put(active.processId, active);
        byObjectId.put(objectId, active);
        scheduler.scheduleAfter(ACTIVE_POLL_TICKS, active.processId);
    }

    /** Scheduled continuation entry point. */
    public void resume(long processId) {
        requireScheduler();
        ActiveAgent active = byProcessId.get(processId);
        if (active == null) {
            throw new IllegalStateException("unknown autonomous process: " + processId);
        }
        if (!objects.isAlive(active.objectId) || !transforms.has(active.objectId)) {
            deactivate(active);
            return;
        }

        if (active.intent != null) {
            continueIntent(active);
        } else {
            decide(active);
        }
    }

    @Override
    public AgentDecisionTrace lastDecision(ObjectId agentId) {
        return agentId == null ? null : lastDecisionByObject.get(agentId);
    }

    @Override
    public ObjectId currentTarget(ObjectId agentId) {
        ActiveAgent active = agentId == null ? null : byObjectId.get(agentId);
        return active == null || active.intent == null ? null : active.intent.sourceId;
    }

    private void decide(ActiveAgent active) {
        WorldObject agentObject = objects.get(active.objectId);
        if (agentObject == null) {
            deactivate(active);
            return;
        }
        AgentDefinition definition = definitions.get(agentObject.definitionId());
        List<Candidate> candidates = perceiveCandidates(active.objectId, definition.perceptionRadius());
        candidates.sort(CANDIDATE_ORDER);

        List<AgentCandidateTrace> traces = new ArrayList<>(candidates.size());
        for (Candidate candidate : candidates) {
            traces.add(candidate.trace);
        }
        Candidate selected = candidates.isEmpty() ? null : candidates.get(0);
        lastDecisionByObject.put(active.objectId, new AgentDecisionTrace(
                time.tick(), active.objectId, traces, selected == null ? null : selected.trace));

        if (selected == null) {
            scheduler.scheduleAfter(IDLE_RECHECK_TICKS, active.processId);
            return;
        }

        MoveToStartAttempt attempt = moveTo.start(
                active.objectId, selected.trace.x(), selected.trace.y(), selected.trace.z());
        if (!attempt.accepted()) {
            scheduler.scheduleAfter(ACTIVE_POLL_TICKS, active.processId);
            return;
        }
        active.intent = new ActiveIntent(selected.providerIndex, selected.trace.sourceId(), attempt.actionId());
        scheduler.scheduleAfter(ACTIVE_POLL_TICKS, active.processId);
    }

    private List<Candidate> perceiveCandidates(ObjectId agentId, int radius) {
        int originX = transforms.x(agentId);
        int originY = transforms.y(agentId);
        int originZ = transforms.z(agentId);
        List<Candidate> result = new ArrayList<>();
        Set<ObjectId> seen = new HashSet<>();

        for (int z = originZ - radius; z <= originZ + radius; z++) {
            for (int y = originY - radius; y <= originY + radius; y++) {
                for (int x = originX - radius; x <= originX + radius; x++) {
                    int distance = chebyshevDistance(originX, originY, originZ, x, y, z);
                    if (distance > radius) {
                        continue;
                    }
                    int count = cells.objectCount(x, y, z);
                    for (int objectIndex = 0; objectIndex < count; objectIndex++) {
                        ObjectId sourceId = cells.objectAt(x, y, z, objectIndex);
                        if (sourceId == null || sourceId.equals(agentId) || !seen.add(sourceId)) {
                            continue;
                        }
                        for (int providerIndex = 0; providerIndex < providers.size(); providerIndex++) {
                            AgentOpportunityProvider provider = providers.get(providerIndex);
                            OpportunityEvaluation evaluation = provider.evaluate(agentId, sourceId, distance);
                            if (evaluation == null) {
                                continue;
                            }
                            result.add(new Candidate(providerIndex, new AgentCandidateTrace(
                                    provider.id(), sourceId, x, y, z, distance,
                                    evaluation.expectedBenefit(), evaluation.score(), evaluation.motivation())));
                        }
                    }
                }
            }
        }
        return result;
    }

    private void continueIntent(ActiveAgent active) {
        if (moveToLookup.isActive(active.objectId)) {
            scheduler.scheduleAfter(ACTIVE_POLL_TICKS, active.processId);
            return;
        }

        ActiveIntent intent = active.intent;
        MoveToCompletion completion = moveToLookup.lastCompletion(active.objectId);
        active.intent = null;
        if (completion == null || !intent.actionId.equals(completion.actionId())) {
            throw new IllegalStateException("autonomous MoveTo completion was lost: " + active.objectId);
        }
        if (completion.reachedGoal()) {
            OpportunityUseResult result = providers.get(intent.providerIndex).use(active.objectId, intent.sourceId);
            if (result == null) {
                throw new IllegalStateException("opportunity provider returned null use result");
            }
        }
        scheduler.scheduleAfter(ACTIVE_POLL_TICKS, active.processId);
    }

    private void deactivate(ActiveAgent active) {
        byProcessId.remove(active.processId, active);
        byObjectId.remove(active.objectId, active);
        lastDecisionByObject.remove(active.objectId);
    }

    private void requireScheduler() {
        if (scheduler == null) {
            throw new IllegalStateException("agent scheduler is not bound");
        }
    }

    private static List<AgentOpportunityProvider> validateProviders(List<AgentOpportunityProvider> providers) {
        List<AgentOpportunityProvider> copy = List.copyOf(providers);
        if (copy.isEmpty()) {
            throw new IllegalArgumentException("at least one opportunity provider is required");
        }
        Set<String> ids = new HashSet<>();
        for (AgentOpportunityProvider provider : copy) {
            if (provider == null) {
                throw new IllegalArgumentException("opportunity provider must not be null");
            }
            if (provider.id() == null || provider.id().isBlank()) {
                throw new IllegalArgumentException("opportunity provider id must not be blank");
            }
            if (!ids.add(provider.id())) {
                throw new IllegalArgumentException("duplicate opportunity provider id: " + provider.id());
            }
        }
        return copy;
    }

    private static int chebyshevDistance(int ax, int ay, int az, int bx, int by, int bz) {
        return Math.max(Math.max(Math.abs(ax - bx), Math.abs(ay - by)), Math.abs(az - bz));
    }

    private record Candidate(int providerIndex, AgentCandidateTrace trace) {
    }

    private static final class ActiveAgent {
        private final long processId;
        private final ObjectId objectId;
        private ActiveIntent intent;

        private ActiveAgent(long processId, ObjectId objectId) {
            this.processId = processId;
            this.objectId = objectId;
        }
    }

    private static final class ActiveIntent {
        private final int providerIndex;
        private final ObjectId sourceId;
        private final MoveToActionId actionId;

        private ActiveIntent(int providerIndex, ObjectId sourceId, MoveToActionId actionId) {
            this.providerIndex = providerIndex;
            this.sourceId = sourceId;
            this.actionId = actionId;
        }
    }
}
