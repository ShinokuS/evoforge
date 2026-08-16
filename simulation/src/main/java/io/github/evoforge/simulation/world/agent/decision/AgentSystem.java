package io.github.evoforge.simulation.world.agent.decision;

import io.github.evoforge.simulation.time.ProcessScheduler;
import io.github.evoforge.simulation.time.SimulationTime;
import io.github.evoforge.simulation.world.agent.AgentDefinitions;
import io.github.evoforge.simulation.world.agent.opportunity.AgentOpportunity;
import io.github.evoforge.simulation.world.agent.opportunity.AgentOpportunityProvider;
import io.github.evoforge.simulation.world.agent.opportunity.InteractionSite;
import io.github.evoforge.simulation.world.agent.opportunity.OpportunityEvaluation;
import io.github.evoforge.simulation.world.agent.opportunity.OpportunitySearchDemand;
import io.github.evoforge.simulation.world.agent.opportunity.OpportunityTarget;
import io.github.evoforge.simulation.world.agent.opportunity.OpportunityUseActionId;
import io.github.evoforge.simulation.world.agent.opportunity.OpportunityUseCompletion;
import io.github.evoforge.simulation.world.agent.opportunity.OpportunityUseStartAttempt;
import io.github.evoforge.simulation.world.agent.perception.PerceptionLookup;
import io.github.evoforge.simulation.world.agent.perception.PerceptionSnapshot;
import io.github.evoforge.simulation.world.agent.search.AgentSearchSystem;
import io.github.evoforge.simulation.world.agent.search.RelativeSearchLocomotion;
import io.github.evoforge.simulation.world.agent.search.SearchAdvanceResult;
import io.github.evoforge.simulation.world.mechanics.movement.MoveToActionId;
import io.github.evoforge.simulation.world.mechanics.movement.MoveToCompletion;
import io.github.evoforge.simulation.world.mechanics.movement.MoveToCompletionSink;
import io.github.evoforge.simulation.world.mechanics.movement.MoveToLookup;
import io.github.evoforge.simulation.world.mechanics.movement.MoveToStartAttempt;
import io.github.evoforge.simulation.world.mechanics.movement.MoveToSystem;
import io.github.evoforge.simulation.world.mechanics.traversal.MoverDestinationAccessResolver;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Generic autonomous decision owner over current perceived mechanic opportunities. */
public final class AgentSystem implements AgentDecisionLookup, MoveToCompletionSink {
    private static final Logger LOGGER = LoggerFactory.getLogger("io.github.evoforge.agent.decision");
    private static final long CONTINUE_SOON_TICKS = 1L;
    private static final long IDLE_RECHECK_TICKS = 10L;
    private static final Comparator<Candidate> CANDIDATE_ORDER =
            Comparator.<Candidate>comparingLong(candidate -> candidate.trace.utility()).reversed()
                    .thenComparingInt(candidate -> candidate.trace.distance())
                    .thenComparing(candidate -> candidate.trace.targetKey())
                    .thenComparingInt(candidate -> candidate.trace.x())
                    .thenComparingInt(candidate -> candidate.trace.y())
                    .thenComparingInt(candidate -> candidate.trace.z())
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
    private final MoverDestinationAccessResolver destinationAccess;
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
            MoverDestinationAccessResolver destinationAccess,
            PerceptionLookup perception,
            AgentSearchSystem search,
            RelativeSearchLocomotion searchLocomotion,
            SimulationTime time) {
        if (objects == null || transforms == null || definitions == null || providers == null
                || moveTo == null || moveToLookup == null || destinationAccess == null
                || perception == null || search == null || searchLocomotion == null || time == null) {
            throw new IllegalArgumentException("agent system dependencies must not be null");
        }
        this.objects = objects;
        this.transforms = transforms;
        this.definitions = definitions;
        this.providers = validateProviders(providers);
        this.moveTo = moveTo;
        this.moveToLookup = moveToLookup;
        this.destinationAccess = destinationAccess;
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
        scheduler.scheduleAfter(CONTINUE_SOON_TICKS, active.processId);
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

    /** Wakes only the autonomous process that is actually waiting on this MoveTo. */
    @Override
    public void completed(MoveToCompletion completion) {
        if (completion == null) throw new IllegalArgumentException("completion must not be null");
        ActiveAgent active = byObjectId.get(completion.objectId());
        if (active == null) return;

        boolean waitingForOpportunity = active.opportunityIntent != null
                && active.opportunityIntent.useActionId == null
                && active.opportunityIntent.moveToActionId.equals(completion.actionId());
        boolean waitingForSearch = active.searchRelocation != null
                && active.searchRelocation.actionId.equals(completion.actionId());
        if (!waitingForOpportunity && !waitingForSearch) return;

        requireScheduler();
        scheduler.scheduleAfter(0L, active.processId);
    }

    @Override
    public AgentDecisionTrace lastDecision(ObjectId agentId) {
        return agentId == null ? null : lastDecisionByObject.get(agentId);
    }

    @Override
    public String currentTargetKey(ObjectId agentId) {
        ActiveAgent active = agentId == null ? null : byObjectId.get(agentId);
        return active == null || active.opportunityIntent == null
                ? null
                : active.opportunityIntent.target.debugKey();
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
                        intent.target.debugKey(),
                        intent.site,
                        intent.useStartedTick,
                        intent.useExpectedCompletionTick);
            }
            return new AgentIntentTrace(
                    AgentIntentPhase.MOVING_TO_OPPORTUNITY,
                    providers.get(intent.providerIndex).id(),
                    intent.target.debugKey(),
                    intent.site,
                    intent.startedTick,
                    -1L);
        }
        if (active.searchRelocation != null) {
            return new AgentIntentTrace(
                    AgentIntentPhase.SEARCH_RELOCATION,
                    null,
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
        refreshRejectedContext(active);
        List<Candidate> candidates = perceiveCandidates(active.objectId);
        candidates.sort(CANDIDATE_ORDER);
        List<AgentCandidateTrace> traces = new ArrayList<>(candidates.size());
        for (Candidate candidate : candidates) traces.add(candidate.trace);

        Candidate selected = null;
        for (Candidate candidate : candidates) {
            if (active.rejectedMovementSites.contains(MovementSite.from(candidate))) continue;
            if (active.rejectedOpportunities.contains(RejectedOpportunity.from(candidate))) continue;
            if (!isLocallyEnterable(active.objectId, candidate.opportunity.site())) continue;
            selected = candidate;
            break;
        }
        lastDecisionByObject.put(
                active.objectId,
                new AgentDecisionTrace(time.tick(), active.objectId, traces, selected == null ? null : selected.trace));

        if (selected == null) {
            continueSearchOrIdle(active);
            return;
        }

        InteractionSite site = selected.opportunity.site();
        MoveToStartAttempt attempt = moveTo.start(active.objectId, site.x(), site.y(), site.z());
        if (!attempt.accepted()) {
            scheduler.scheduleAfter(CONTINUE_SOON_TICKS, active.processId);
            return;
        }

        if (!moveToLookup.isActive(active.objectId)) {
            MoveToCompletion completion = moveToLookup.lastCompletion(active.objectId);
            if (completion == null || !attempt.actionId().equals(completion.actionId())) {
                throw new IllegalStateException("accepted autonomous MoveTo lost its terminal outcome: "
                        + active.objectId);
            }
            if (!completion.reachedGoal()) {
                rememberMovementFailure(
                        active,
                        selected.providerIndex,
                        selected.opportunity.target(),
                        site,
                        "move_to",
                        completion.code().value());
                scheduler.scheduleAfter(CONTINUE_SOON_TICKS, active.processId);
                return;
            }
        }

        search.cancel(active.objectId);
        active.opportunityIntent = new ActiveOpportunityIntent(
                selected.providerIndex,
                selected.opportunity.target(),
                site,
                attempt.actionId(),
                time.tick());

        if (!moveToLookup.isActive(active.objectId)) {
            scheduler.scheduleAfter(CONTINUE_SOON_TICKS, active.processId);
        }
    }

    private boolean isLocallyEnterable(ObjectId objectId, InteractionSite site) {
        if (transforms.x(objectId) == site.x()
                && transforms.y(objectId) == site.y()
                && transforms.z(objectId) == site.z()) {
            return true;
        }
        return destinationAccess.canEnter(objectId, site.x(), site.y(), site.z());
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
            clearRejectedOpportunities(active);
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
                clearRejectedOpportunities(active);
                scheduler.scheduleAfter(CONTINUE_SOON_TICKS, active.processId);
                return;
            }
            active.searchRelocation = new ActiveSearchRelocation(attempt.actionId(), time.tick());
            if (!moveToLookup.isActive(active.objectId)) {
                scheduler.scheduleAfter(CONTINUE_SOON_TICKS, active.processId);
            }
            return;
        }
        scheduler.scheduleAfter(result.continueSoon() ? CONTINUE_SOON_TICKS : IDLE_RECHECK_TICKS, active.processId);
    }

    private List<Candidate> perceiveCandidates(ObjectId agentId) {
        PerceptionSnapshot snapshot = perception.perceive(agentId);
        if (snapshot == null) throw new IllegalStateException("perception returned null snapshot: " + agentId);
        List<Candidate> result = new ArrayList<>();
        for (int providerIndex = 0; providerIndex < providers.size(); providerIndex++) {
            AgentOpportunityProvider provider = providers.get(providerIndex);
            List<AgentOpportunity> opportunities = provider.opportunities(agentId, snapshot);
            if (opportunities == null) {
                throw new IllegalStateException("opportunity provider returned null opportunities: " + provider.id());
            }
            for (AgentOpportunity opportunity : opportunities) {
                if (opportunity == null) {
                    throw new IllegalStateException("opportunity provider returned null opportunity: " + provider.id());
                }
                OpportunityEvaluation evaluation = opportunity.evaluation();
                long utility = UtilityMath.score(evaluation);
                InteractionSite site = opportunity.site();
                AgentCandidateTrace trace = new AgentCandidateTrace(
                        provider.id(),
                        opportunity.target().debugKey(),
                        site.x(),
                        site.y(),
                        site.z(),
                        site.distance(),
                        evaluation.expectedBenefit(),
                        evaluation.pressure(),
                        evaluation.relief(),
                        evaluation.travel(),
                        utility,
                        evaluation.motivation());
                result.add(new Candidate(providerIndex, opportunity, trace));
            }
        }
        return result;
    }

    private void continueOpportunityIntent(ActiveAgent active) {
        ActiveOpportunityIntent intent = active.opportunityIntent;
        AgentOpportunityProvider provider = providers.get(intent.providerIndex);

        if (intent.useActionId != null) {
            if (provider.isUseActive(active.objectId)) {
                return;
            }
            OpportunityUseCompletion completion = provider.lastUseCompletion(active.objectId);
            if (completion == null
                    || !intent.useActionId.equals(completion.actionId())
                    || !intent.target.equals(completion.target())
                    || !intent.site.equals(completion.site())) {
                throw new IllegalStateException("autonomous opportunity-use completion was lost: " + active.objectId);
            }
            if (completion.result().accepted()
                    && provider.evaluate(active.objectId, intent.target, intent.site) != null) {
                OpportunityUseStartAttempt nextUse = provider.startUse(
                        active.objectId,
                        intent.target,
                        intent.site);
                if (nextUse == null) {
                    throw new IllegalStateException("opportunity provider returned null continuation start attempt");
                }
                if (nextUse.accepted()) {
                    setUse(intent, nextUse);
                    scheduleUseCompletion(active, provider, nextUse);
                    return;
                }
            }
            if (!completion.result().accepted()) {
                rememberRejected(
                        active,
                        RejectedOpportunity.from(intent),
                        "use",
                        completion.result().code().value());
            } else {
                clearRejectedOpportunities(active);
            }
            active.opportunityIntent = null;
            scheduler.scheduleAfter(CONTINUE_SOON_TICKS, active.processId);
            return;
        }

        if (moveToLookup.isActive(active.objectId)) {
            return;
        }
        MoveToCompletion completion = moveToLookup.lastCompletion(active.objectId);
        if (completion == null || !intent.moveToActionId.equals(completion.actionId())) {
            throw new IllegalStateException("autonomous MoveTo completion was lost: " + active.objectId);
        }
        if (!completion.reachedGoal()) {
            rememberMovementFailure(
                    active,
                    intent.providerIndex,
                    intent.target,
                    intent.site,
                    "move_to",
                    completion.code().value());
            active.opportunityIntent = null;
            scheduler.scheduleAfter(CONTINUE_SOON_TICKS, active.processId);
            return;
        }

        OpportunityUseStartAttempt use = provider.startUse(
                active.objectId,
                intent.target,
                intent.site);
        if (use == null) throw new IllegalStateException("opportunity provider returned null use start attempt");
        if (!use.accepted()) {
            rememberRejected(
                    active,
                    RejectedOpportunity.from(intent),
                    "use_start",
                    use.code().value());
            active.opportunityIntent = null;
            scheduler.scheduleAfter(CONTINUE_SOON_TICKS, active.processId);
            return;
        }
        setUse(intent, use);
        scheduleUseCompletion(active, provider, use);
    }

    private void scheduleUseCompletion(
            ActiveAgent active,
            AgentOpportunityProvider provider,
            OpportunityUseStartAttempt use) {
        if (!provider.isUseActive(active.objectId)) {
            scheduler.scheduleAfter(CONTINUE_SOON_TICKS, active.processId);
            return;
        }
        if (use.expectedCompletionTick() < time.tick()) {
            throw new IllegalStateException("active opportunity use completion is already in the past: "
                    + active.objectId);
        }
        scheduler.scheduleAt(use.expectedCompletionTick(), active.processId);
    }

    private static void setUse(ActiveOpportunityIntent intent, OpportunityUseStartAttempt use) {
        intent.useActionId = use.actionId();
        intent.useStartedTick = use.startedTick();
        intent.useExpectedCompletionTick = use.expectedCompletionTick();
    }

    private void continueSearchRelocation(ActiveAgent active) {
        if (moveToLookup.isActive(active.objectId)) {
            return;
        }
        ActiveSearchRelocation relocation = active.searchRelocation;
        MoveToCompletion completion = moveToLookup.lastCompletion(active.objectId);
        active.searchRelocation = null;
        if (completion == null || !relocation.actionId.equals(completion.actionId())) {
            throw new IllegalStateException("search relocation completion was lost: " + active.objectId);
        }
        search.relocationFinished(active.objectId, completion.reachedGoal());
        clearRejectedOpportunities(active);
        scheduler.scheduleAfter(CONTINUE_SOON_TICKS, active.processId);
    }

    private void rememberMovementFailure(
            ActiveAgent active,
            int providerIndex,
            OpportunityTarget target,
            InteractionSite site,
            String stage,
            String code) {
        refreshRejectedContext(active);
        if (!active.rejectedMovementSites.add(MovementSite.from(site))) return;
        logFailure(active, providerIndex, target, site, stage, code,
                "Autonomous movement site failed and was quarantined for the current local context");
    }

    private void rememberRejected(
            ActiveAgent active,
            RejectedOpportunity rejected,
            String stage,
            String code) {
        refreshRejectedContext(active);
        if (!active.rejectedOpportunities.add(rejected)) return;
        logFailure(
                active,
                rejected.providerIndex,
                rejected.target,
                rejected.site,
                stage,
                code,
                "Autonomous opportunity failed and was quarantined for the current local context");
    }

    private void logFailure(
            ActiveAgent active,
            int providerIndex,
            OpportunityTarget target,
            InteractionSite site,
            String stage,
            String code,
            String message) {
        LOGGER.atDebug()
                .addKeyValue("event", "agent.opportunity_failed")
                .addKeyValue("tick", time.tick())
                .addKeyValue("objectId", active.objectId.asLong())
                .addKeyValue("provider", providers.get(providerIndex).id())
                .addKeyValue("target", target.debugKey())
                .addKeyValue("siteX", site.x())
                .addKeyValue("siteY", site.y())
                .addKeyValue("siteZ", site.z())
                .addKeyValue("stage", stage)
                .addKeyValue("code", code)
                .log(message);
    }

    private void refreshRejectedContext(ActiveAgent active) {
        RejectionContext current = new RejectionContext(
                transforms.x(active.objectId),
                transforms.y(active.objectId),
                transforms.z(active.objectId));
        if (current.equals(active.rejectionContext)) return;
        active.rejectedMovementSites.clear();
        active.rejectedOpportunities.clear();
        active.rejectionContext = current;
    }

    private static void clearRejectedOpportunities(ActiveAgent active) {
        active.rejectedMovementSites.clear();
        active.rejectedOpportunities.clear();
        active.rejectionContext = null;
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

    private record Candidate(
            int providerIndex,
            AgentOpportunity opportunity,
            AgentCandidateTrace trace) { }

    private record SearchCandidate(int providerIndex, OpportunitySearchDemand demand) { }

    private record MovementSite(int x, int y, int z) {
        private static MovementSite from(Candidate candidate) {
            return from(candidate.opportunity.site());
        }

        private static MovementSite from(InteractionSite site) {
            return new MovementSite(site.x(), site.y(), site.z());
        }
    }

    private record RejectedOpportunity(
            int providerIndex,
            OpportunityTarget target,
            InteractionSite site) {
        private static RejectedOpportunity from(ActiveOpportunityIntent intent) {
            return new RejectedOpportunity(intent.providerIndex, intent.target, intent.site);
        }

        private static RejectedOpportunity from(Candidate candidate) {
            return new RejectedOpportunity(
                    candidate.providerIndex,
                    candidate.opportunity.target(),
                    candidate.opportunity.site());
        }
    }

    private record RejectionContext(int x, int y, int z) { }

    private static final class ActiveAgent {
        private final long processId;
        private final ObjectId objectId;
        private final Set<MovementSite> rejectedMovementSites = new HashSet<>();
        private final Set<RejectedOpportunity> rejectedOpportunities = new HashSet<>();
        private ActiveOpportunityIntent opportunityIntent;
        private ActiveSearchRelocation searchRelocation;
        private RejectionContext rejectionContext;

        private ActiveAgent(long processId, ObjectId objectId) {
            this.processId = processId;
            this.objectId = objectId;
        }
    }

    private static final class ActiveOpportunityIntent {
        private final int providerIndex;
        private final OpportunityTarget target;
        private final InteractionSite site;
        private final MoveToActionId moveToActionId;
        private final long startedTick;
        private OpportunityUseActionId useActionId;
        private long useStartedTick;
        private long useExpectedCompletionTick;

        private ActiveOpportunityIntent(
                int providerIndex,
                OpportunityTarget target,
                InteractionSite site,
                MoveToActionId moveToActionId,
                long startedTick) {
            this.providerIndex = providerIndex;
            this.target = target;
            this.site = site;
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
