package io.github.evoforge.simulation.world.agent.decision;

import io.github.evoforge.simulation.time.ProcessScheduler;
import io.github.evoforge.simulation.time.SimulationTime;
import io.github.evoforge.simulation.world.agent.AgentDefinitions;
import io.github.evoforge.simulation.world.agent.opportunity.AgentOpportunityProvider;
import io.github.evoforge.simulation.world.agent.opportunity.OpportunityEvaluation;
import io.github.evoforge.simulation.world.agent.opportunity.OpportunitySearchDemand;
import io.github.evoforge.simulation.world.agent.opportunity.OpportunityUseActionId;
import io.github.evoforge.simulation.world.agent.opportunity.OpportunityUseCompletion;
import io.github.evoforge.simulation.world.agent.opportunity.OpportunityUseStartAttempt;
import io.github.evoforge.simulation.world.agent.perception.PerceivedObject;
import io.github.evoforge.simulation.world.agent.perception.PerceptionLookup;
import io.github.evoforge.simulation.world.agent.perception.PerceptionSnapshot;
import io.github.evoforge.simulation.world.agent.search.AgentSearchSystem;
import io.github.evoforge.simulation.world.agent.search.RelativeSearchLocomotion;
import io.github.evoforge.simulation.world.agent.search.SearchAdvanceResult;
import io.github.evoforge.simulation.world.mechanics.movement.MoveToActionId;
import io.github.evoforge.simulation.world.mechanics.movement.MoveToCompletion;
import io.github.evoforge.simulation.world.mechanics.movement.MoveToLookup;
import io.github.evoforge.simulation.world.mechanics.movement.MoveToStartAttempt;
import io.github.evoforge.simulation.world.mechanics.movement.MoveToSystem;
import io.github.evoforge.simulation.world.object.ObjectId;
import io.github.evoforge.simulation.world.object.ObjectLookup;
import io.github.evoforge.simulation.world.object.WorldObject;
import io.github.evoforge.simulation.world.spatial.TransformLookup;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Generic autonomous decision owner over current perceived mechanic opportunities. */
public final class AgentSystem implements AgentDecisionLookup {
    private static final long ACTIVE_POLL_TICKS = 1L;
    private static final long IDLE_RECHECK_TICKS = 10L;
    private static final Comparator<Candidate> CANDIDATE_ORDER =
            Comparator.<Candidate>comparingLong(candidate -> candidate.trace.score()).reversed()
                    .thenComparingInt(candidate -> candidate.trace.distance())
                    .thenComparingLong(candidate -> candidate.trace.sourceId().asLong())
                    .thenComparingInt(candidate -> candidate.providerIndex);
    private static final Comparator<SearchCandidate> SEARCH_ORDER =
            Comparator.<SearchCandidate>comparingLong(candidate -> candidate.demand.urgency()).reversed()
                    .thenComparing(candidate -> candidate.demand.motivation())
                    .thenComparingInt(candidate -> candidate.providerIndex);

    private final ObjectLookup objects;
    private final TransformLookup transforms;
    private final AgentDefinitions definitions;
    private final List<AgentOpportunityProvider> providers;
    private final MoveToSystem moveTo;
    private final MoveToLookup moveToLookup;
    private final PerceptionLookup perception;
    private final AgentSearchSystem search;
    private final RelativeSearchLocomotion searchLocomotion;
    private final SimulationTime time;
    private final Map<Long, ActiveAgent> byProcessId = new HashMap<>();
    private final Map<ObjectId, ActiveAgent> byObjectId = new HashMap<>();
    private final Map<ObjectId, AgentDecisionTrace> lastDecisionByObject = new HashMap<>();
    private ProcessScheduler scheduler;
    private long nextProcessId;

    public AgentSystem(
            ObjectLookup objects,
            TransformLookup transforms,
            AgentDefinitions definitions,
            List<AgentOpportunityProvider> providers,
            MoveToSystem moveTo,
            MoveToLookup moveToLookup,
            PerceptionLookup perception,
            AgentSearchSystem search,
            RelativeSearchLocomotion searchLocomotion,
            SimulationTime time) {
        if (objects == null || transforms == null || definitions == null || providers == null
                || moveTo == null || moveToLookup == null || perception == null || search == null
                || searchLocomotion == null || time == null) {
            throw new IllegalArgumentException("agent system dependencies must not be null");
        }
        this.objects = objects;
        this.transforms = transforms;
        this.definitions = definitions;
        this.providers = validateProviders(providers);
        this.moveTo = moveTo;
        this.moveToLookup = moveToLookup;
        this.perception = perception;
        this.search = search;
        this.searchLocomotion = searchLocomotion;
        this.time = time;
    }

    public void bindScheduler(ProcessScheduler scheduler) {
        if (scheduler == null) throw new IllegalArgumentException("scheduler must not be null");
        if (this.scheduler != null) throw new IllegalStateException("agent scheduler is already bound");
        this.scheduler = scheduler;
    }

    public void activate(ObjectId objectId) {
        requireScheduler();
        WorldObject object = objects.get(objectId);
        if (object == null) throw new IllegalArgumentException("agent object must be alive: " + objectId);
        if (!definitions.has(object.definitionId())) {
            throw new IllegalArgumentException("object definition is not autonomous: " + object.definitionId());
        }
        if (!transforms.has(objectId)) {
            throw new IllegalStateException("autonomous object must have a transform before activation: " + objectId);
        }
        if (byObjectId.containsKey(objectId)) throw new IllegalStateException("agent is already active: " + objectId);
        if (nextProcessId == Long.MAX_VALUE) throw new IllegalStateException("agent process id space exhausted");
        ActiveAgent active = new ActiveAgent(nextProcessId++, objectId);
        byProcessId.put(active.processId, active);
        byObjectId.put(objectId, active);
        scheduler.scheduleAfter(ACTIVE_POLL_TICKS, active.processId);
    }

    public void resume(long processId) {
        requireScheduler();
        ActiveAgent active = byProcessId.get(processId);
        if (active == null) throw new IllegalStateException("unknown autonomous process: " + processId);
        if (!objects.isAlive(active.objectId) || !transforms.has(active.objectId)) {
            deactivate(active);
            return;
        }
        if (active.opportunityIntent != null) {
            continueOpportunityIntent(active);
        } else if (active.searchRelocation != null) {
            continueSearchRelocation(active);
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
        return active == null || active.opportunityIntent == null ? null : active.opportunityIntent.sourceId;
    }

    @Override
    public AgentIntentTrace currentIntent(ObjectId agentId) {
        ActiveAgent active = agentId == null ? null : byObjectId.get(agentId);
        if (active == null) return null;
        if (active.opportunityIntent != null) {
            ActiveOpportunityIntent intent = active.opportunityIntent;
            if (intent.useActionId != null) {
                return new AgentIntentTrace(
                        AgentIntentPhase.USING_OPPORTUNITY,
                        providers.get(intent.providerIndex).id(),
                        intent.sourceId,
                        intent.useStartedTick,
                        intent.useExpectedCompletionTick);
            }
            return new AgentIntentTrace(
                    AgentIntentPhase.MOVING_TO_OPPORTUNITY,
                    providers.get(intent.providerIndex).id(),
                    intent.sourceId,
                    intent.startedTick,
                    -1L);
        }
        if (active.searchRelocation != null) {
            return new AgentIntentTrace(
                    AgentIntentPhase.SEARCH_RELOCATION,
                    null,
                    null,
                    active.searchRelocation.startedTick,
                    -1L);
        }
        return null;
    }

    private void decide(ActiveAgent active) {
        if (objects.get(active.objectId) == null) {
            deactivate(active);
            return;
        }
        List<Candidate> candidates = perceiveCandidates(active.objectId);
        candidates.sort(CANDIDATE_ORDER);
        List<AgentCandidateTrace> traces = new ArrayList<>(candidates.size());
        for (Candidate candidate : candidates) traces.add(candidate.trace);
        Candidate selected = candidates.isEmpty() ? null : candidates.get(0);
        lastDecisionByObject.put(
                active.objectId,
                new AgentDecisionTrace(time.tick(), active.objectId, traces, selected == null ? null : selected.trace));

        if (selected == null) {
            continueSearchOrIdle(active);
            return;
        }

        search.cancel(active.objectId);
        MoveToStartAttempt attempt = moveTo.start(
                active.objectId,
                selected.trace.x(),
                selected.trace.y(),
                selected.trace.z());
        if (!attempt.accepted()) {
            scheduler.scheduleAfter(ACTIVE_POLL_TICKS, active.processId);
            return;
        }
        active.opportunityIntent = new ActiveOpportunityIntent(
                selected.providerIndex,
                selected.trace.sourceId(),
                attempt.actionId(),
                time.tick());
        scheduler.scheduleAfter(ACTIVE_POLL_TICKS, active.processId);
    }

    private void continueSearchOrIdle(ActiveAgent active) {
        List<SearchCandidate> demands = new ArrayList<>();
        for (int providerIndex = 0; providerIndex < providers.size(); providerIndex++) {
            AgentOpportunityProvider provider = providers.get(providerIndex);
            List<OpportunitySearchDemand> providerDemands = provider.searchDemands(active.objectId);
            if (providerDemands == null) {
                throw new IllegalStateException("opportunity provider returned null search demands: " + provider.id());
            }
            for (OpportunitySearchDemand demand : providerDemands) {
                if (demand == null) {
                    throw new IllegalStateException("opportunity provider returned null search demand: " + provider.id());
                }
                demands.add(new SearchCandidate(providerIndex, demand));
            }
        }
        demands.sort(SEARCH_ORDER);
        if (demands.isEmpty() || !search.supports(active.objectId)) {
            search.cancel(active.objectId);
            scheduler.scheduleAfter(IDLE_RECHECK_TICKS, active.processId);
            return;
        }

        SearchCandidate selected = demands.get(0);
        SearchAdvanceResult result = search.advance(
                active.objectId,
                providers.get(selected.providerIndex).id(),
                selected.demand.motivation());
        if (result.relocation() != null) {
            RelativeSearchLocomotion.StartAttempt attempt = searchLocomotion.startLeg(
                    active.objectId,
                    result.relocation());
            if (!attempt.accepted()) {
                search.relocationFinished(active.objectId, false);
                scheduler.scheduleAfter(ACTIVE_POLL_TICKS, active.processId);
                return;
            }
            active.searchRelocation = new ActiveSearchRelocation(attempt.actionId(), time.tick());
            scheduler.scheduleAfter(ACTIVE_POLL_TICKS, active.processId);
            return;
        }
        scheduler.scheduleAfter(result.continueSoon() ? ACTIVE_POLL_TICKS : IDLE_RECHECK_TICKS, active.processId);
    }

    private List<Candidate> perceiveCandidates(ObjectId agentId) {
        PerceptionSnapshot snapshot = perception.perceive(agentId);
        if (snapshot == null) throw new IllegalStateException("perception returned null snapshot: " + agentId);
        List<Candidate> result = new ArrayList<>();
        for (PerceivedObject perceived : snapshot.objects()) {
            for (int providerIndex = 0; providerIndex < providers.size(); providerIndex++) {
                AgentOpportunityProvider provider = providers.get(providerIndex);
                OpportunityEvaluation evaluation = provider.evaluate(agentId, perceived.objectId(), perceived.distance());
                if (evaluation == null) continue;
                result.add(new Candidate(
                        providerIndex,
                        new AgentCandidateTrace(
                                provider.id(),
                                perceived.objectId(),
                                perceived.x(),
                                perceived.y(),
                                perceived.z(),
                                perceived.distance(),
                                evaluation.expectedBenefit(),
                                evaluation.score(),
                                evaluation.motivation())));
            }
        }
        return result;
    }

    private void continueOpportunityIntent(ActiveAgent active) {
        ActiveOpportunityIntent intent = active.opportunityIntent;
        AgentOpportunityProvider provider = providers.get(intent.providerIndex);

        if (intent.useActionId != null) {
            if (provider.isUseActive(active.objectId)) {
                scheduler.scheduleAfter(ACTIVE_POLL_TICKS, active.processId);
                return;
            }
            OpportunityUseCompletion completion = provider.lastUseCompletion(active.objectId);
            if (completion == null || !intent.useActionId.equals(completion.actionId())
                    || !intent.sourceId.equals(completion.sourceId())) {
                throw new IllegalStateException("autonomous opportunity-use completion was lost: " + active.objectId);
            }
            if (completion.result().accepted()
                    && provider.evaluate(active.objectId, intent.sourceId, 0) != null) {
                OpportunityUseStartAttempt nextUse = provider.startUse(active.objectId, intent.sourceId);
                if (nextUse == null) {
                    throw new IllegalStateException("opportunity provider returned null continuation start attempt");
                }
                if (nextUse.accepted()) {
                    setUse(intent, nextUse);
                    scheduler.scheduleAfter(ACTIVE_POLL_TICKS, active.processId);
                    return;
                }
            }
            active.opportunityIntent = null;
            scheduler.scheduleAfter(ACTIVE_POLL_TICKS, active.processId);
            return;
        }

        if (moveToLookup.isActive(active.objectId)) {
            scheduler.scheduleAfter(ACTIVE_POLL_TICKS, active.processId);
            return;
        }
        MoveToCompletion completion = moveToLookup.lastCompletion(active.objectId);
        if (completion == null || !intent.moveToActionId.equals(completion.actionId())) {
            throw new IllegalStateException("autonomous MoveTo completion was lost: " + active.objectId);
        }
        if (!completion.reachedGoal()) {
            active.opportunityIntent = null;
            scheduler.scheduleAfter(ACTIVE_POLL_TICKS, active.processId);
            return;
        }

        OpportunityUseStartAttempt use = provider.startUse(active.objectId, intent.sourceId);
        if (use == null) throw new IllegalStateException("opportunity provider returned null use start attempt");
        if (!use.accepted()) {
            active.opportunityIntent = null;
            scheduler.scheduleAfter(ACTIVE_POLL_TICKS, active.processId);
            return;
        }
        setUse(intent, use);
        scheduler.scheduleAfter(ACTIVE_POLL_TICKS, active.processId);
    }

    private static void setUse(ActiveOpportunityIntent intent, OpportunityUseStartAttempt use) {
        intent.useActionId = use.actionId();
        intent.useStartedTick = use.startedTick();
        intent.useExpectedCompletionTick = use.expectedCompletionTick();
    }

    private void continueSearchRelocation(ActiveAgent active) {
        if (moveToLookup.isActive(active.objectId)) {
            scheduler.scheduleAfter(ACTIVE_POLL_TICKS, active.processId);
            return;
        }
        ActiveSearchRelocation relocation = active.searchRelocation;
        MoveToCompletion completion = moveToLookup.lastCompletion(active.objectId);
        active.searchRelocation = null;
        if (completion == null || !relocation.actionId.equals(completion.actionId())) {
            throw new IllegalStateException("search relocation completion was lost: " + active.objectId);
        }
        search.relocationFinished(active.objectId, completion.reachedGoal());
        scheduler.scheduleAfter(ACTIVE_POLL_TICKS, active.processId);
    }

    private void deactivate(ActiveAgent active) {
        search.cancel(active.objectId);
        byProcessId.remove(active.processId, active);
        byObjectId.remove(active.objectId, active);
        lastDecisionByObject.remove(active.objectId);
    }

    private void requireScheduler() {
        if (scheduler == null) throw new IllegalStateException("agent scheduler is not bound");
    }

    private static List<AgentOpportunityProvider> validateProviders(List<AgentOpportunityProvider> providers) {
        List<AgentOpportunityProvider> copy = List.copyOf(providers);
        if (copy.isEmpty()) throw new IllegalArgumentException("at least one opportunity provider is required");
        Set<String> ids = new HashSet<>();
        for (AgentOpportunityProvider provider : copy) {
            if (provider == null) throw new IllegalArgumentException("opportunity provider must not be null");
            if (provider.id() == null || provider.id().isBlank()) {
                throw new IllegalArgumentException("opportunity provider id must not be blank");
            }
            if (!ids.add(provider.id())) {
                throw new IllegalArgumentException("duplicate opportunity provider id: " + provider.id());
            }
        }
        return copy;
    }

    private record Candidate(int providerIndex, AgentCandidateTrace trace) { }
    private record SearchCandidate(int providerIndex, OpportunitySearchDemand demand) { }

    private static final class ActiveAgent {
        private final long processId;
        private final ObjectId objectId;
        private ActiveOpportunityIntent opportunityIntent;
        private ActiveSearchRelocation searchRelocation;

        private ActiveAgent(long processId, ObjectId objectId) {
            this.processId = processId;
            this.objectId = objectId;
        }
    }

    private static final class ActiveOpportunityIntent {
        private final int providerIndex;
        private final ObjectId sourceId;
        private final MoveToActionId moveToActionId;
        private final long startedTick;
        private OpportunityUseActionId useActionId;
        private long useStartedTick;
        private long useExpectedCompletionTick;

        private ActiveOpportunityIntent(
                int providerIndex,
                ObjectId sourceId,
                MoveToActionId moveToActionId,
                long startedTick) {
            this.providerIndex = providerIndex;
            this.sourceId = sourceId;
            this.moveToActionId = moveToActionId;
            this.startedTick = startedTick;
        }
    }

    private static final class ActiveSearchRelocation {
        private final MoveToActionId actionId;
        private final long startedTick;

        private ActiveSearchRelocation(MoveToActionId actionId, long startedTick) {
            this.actionId = actionId;
            this.startedTick = startedTick;
        }
    }
}
