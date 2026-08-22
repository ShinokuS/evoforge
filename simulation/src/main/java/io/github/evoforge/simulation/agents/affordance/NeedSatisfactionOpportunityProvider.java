package io.github.evoforge.simulation.agents.affordance;

import io.github.evoforge.simulation.kernel.operation.ResultCode;
import io.github.evoforge.simulation.kernel.scheduling.ProcessScheduler;
import io.github.evoforge.simulation.kernel.time.SimulationTime;
import io.github.evoforge.simulation.agents.AgentDefinition;
import io.github.evoforge.simulation.agents.AgentDefinitions;
import io.github.evoforge.simulation.agents.decision.UtilityMath;
import io.github.evoforge.simulation.agents.knowledge.need.NeedSolutionKnowledgeDefinitions;
import io.github.evoforge.simulation.agents.need.NeedId;
import io.github.evoforge.simulation.agents.need.NeedSystem;
import io.github.evoforge.simulation.agents.need.motivation.NeedMotivationDefinitions;
import io.github.evoforge.simulation.agents.opportunity.AgentOpportunity;
import io.github.evoforge.simulation.agents.opportunity.AgentOpportunityProvider;
import io.github.evoforge.simulation.agents.opportunity.InteractionSite;
import io.github.evoforge.simulation.agents.opportunity.OpportunityEvaluation;
import io.github.evoforge.simulation.agents.opportunity.OpportunitySearchDemand;
import io.github.evoforge.simulation.agents.opportunity.OpportunityTarget;
import io.github.evoforge.simulation.agents.opportunity.OpportunityUseActionId;
import io.github.evoforge.simulation.agents.opportunity.OpportunityUseCompletion;
import io.github.evoforge.simulation.agents.opportunity.OpportunityUseLifecycle;
import io.github.evoforge.simulation.agents.opportunity.OpportunityUseResult;
import io.github.evoforge.simulation.agents.opportunity.OpportunityUseStartAttempt;
import io.github.evoforge.simulation.agents.perception.PerceivedObject;
import io.github.evoforge.simulation.agents.perception.PerceptionSnapshot;
import io.github.evoforge.simulation.world.object.stock.ConsumableStockSystem;
import io.github.evoforge.simulation.world.object.ObjectId;
import io.github.evoforge.simulation.world.object.ObjectLookup;
import io.github.evoforge.simulation.world.object.WorldObject;
import io.github.evoforge.simulation.world.spatial.TransformLookup;
import java.util.ArrayList;
import java.util.List;

/** Object-backed need opportunities plus knowledge-gated search and provider-owned use timing. */
public final class NeedSatisfactionOpportunityProvider implements AgentOpportunityProvider {
    private static final ResultCode STARTED = ResultCode.of("needs", "satisfaction_started");
    private static final ResultCode USED = ResultCode.of("needs", "satisfied");
    private static final ResultCode UNAVAILABLE = ResultCode.of("needs", "opportunity_unavailable");

    private final ObjectLookup objects;
    private final TransformLookup transforms;
    private final AgentDefinitions agents;
    private final NeedSatisfactionDefinitions definitions;
    private final NeedSolutionKnowledgeDefinitions knowledge;
    private final NeedMotivationDefinitions motivations;
    private final NeedSystem needs;
    private final ConsumableStockSystem stocks;
    private final SimulationTime time;
    private final OpportunityUseLifecycle<ActiveUse> useLifecycle =
            new OpportunityUseLifecycle<>("need-satisfaction", "opportunity use id space exhausted");

    public NeedSatisfactionOpportunityProvider(
            ObjectLookup objects,
            TransformLookup transforms,
            AgentDefinitions agents,
            NeedSatisfactionDefinitions definitions,
            NeedSolutionKnowledgeDefinitions knowledge,
            NeedMotivationDefinitions motivations,
            NeedSystem needs,
            ConsumableStockSystem stocks,
            SimulationTime time) {
        if (objects == null || transforms == null || agents == null || definitions == null
                || knowledge == null || motivations == null || needs == null || stocks == null || time == null) {
            throw new IllegalArgumentException("need-satisfaction provider dependencies must not be null");
        }
        this.objects = objects;
        this.transforms = transforms;
        this.agents = agents;
        this.definitions = definitions;
        this.knowledge = knowledge;
        this.motivations = motivations;
        this.needs = needs;
        this.stocks = stocks;
        this.time = time;
    }

    public void bindScheduler(ProcessScheduler scheduler) {
        useLifecycle.bindScheduler(scheduler);
    }

    /** Completes one previously scheduled use lifecycle. */
    public void resume(long processId) {
        complete(useLifecycle.resume(processId));
    }

    @Override
    public String id() { return "needs:satisfaction"; }

    @Override
    public List<AgentOpportunity> opportunities(ObjectId agentId, PerceptionSnapshot perception) {
        if (perception == null || !agentId.equals(perception.observerId())) {
            throw new IllegalArgumentException("perception must belong to the evaluated agent");
        }
        List<AgentOpportunity> result = new ArrayList<>();
        for (PerceivedObject perceived : perception.objects()) {
            ObjectTarget target = new ObjectTarget(perceived.objectId());
            InteractionSite site = new InteractionSite(
                    perceived.x(), perceived.y(), perceived.z(), perceived.distance());
            OpportunityEvaluation evaluation = evaluate(agentId, target, site);
            if (evaluation != null) result.add(new AgentOpportunity(target, site, evaluation));
        }
        return List.copyOf(result);
    }

    @Override
    public List<OpportunitySearchDemand> searchDemands(ObjectId agentId) {
        WorldObject agentObject = objects.get(agentId);
        if (agentObject == null || !agents.has(agentObject.definitionId())) return List.of();
        List<OpportunitySearchDemand> result = new ArrayList<>();
        for (int index = 0; index < needs.needCount(agentId); index++) {
            NeedId needId = needs.needAt(agentId, index);
            long level = needs.level(agentId, needId);
            if (!motivated(agentObject, needId, level)
                    || !knowledge.knows(agentObject.definitionId(), needId)) continue;
            long max = needs.maxLevel(agentId, needId);
            long urgency = UtilityMath.ratio(level, Math.max(1L, max));
            result.add(new OpportunitySearchDemand(needId.value(), urgency));
        }
        return List.copyOf(result);
    }

    @Override
    public OpportunityEvaluation evaluate(
            ObjectId agentId,
            OpportunityTarget target,
            InteractionSite site) {
        if (!(target instanceof ObjectTarget objectTarget) || site == null) return null;
        Selection selection = select(agentId, objectTarget.sourceId, false);
        if (selection == null) return null;
        return new OpportunityEvaluation(
                selection.benefit,
                UtilityMath.ratio(selection.level, selection.maxLevel),
                UtilityMath.ratio(selection.benefit, selection.level),
                UtilityMath.travelFromDistance(site.distance()),
                selection.satisfaction.needId().value());
    }

    @Override
    public OpportunityUseStartAttempt startUse(
            ObjectId agentId,
            OpportunityTarget target,
            InteractionSite site) {
        if (!(target instanceof ObjectTarget objectTarget) || site == null) {
            return OpportunityUseStartAttempt.rejected(UNAVAILABLE);
        }
        if (useLifecycle.isActive(agentId) || !atSite(agentId, site)) {
            return OpportunityUseStartAttempt.rejected(UNAVAILABLE);
        }
        Selection selection = select(agentId, objectTarget.sourceId, true);
        if (selection == null) return OpportunityUseStartAttempt.rejected(UNAVAILABLE);

        long startedTick = time.tick();
        long expectedCompletionTick;
        try {
            expectedCompletionTick = Math.addExact(startedTick, selection.satisfaction.useDurationTicks());
        } catch (ArithmeticException exception) {
            throw new IllegalStateException("opportunity use completion tick overflow", exception);
        }
        OpportunityUseActionId actionId = useLifecycle.nextActionId();
        ActiveUse active = new ActiveUse(
                actionId,
                agentId,
                target,
                site,
                objectTarget.sourceId,
                selection.satisfaction,
                startedTick,
                expectedCompletionTick);

        if (selection.satisfaction.useDurationTicks() == 0L) {
            complete(active);
        } else {
            useLifecycle.schedule(
                    agentId,
                    actionId,
                    selection.satisfaction.useDurationTicks(),
                    active);
        }
        return new OpportunityUseStartAttempt(
                true,
                actionId,
                startedTick,
                expectedCompletionTick,
                STARTED);
    }

    @Override
    public boolean isUseActive(ObjectId agentId) {
        return useLifecycle.isActive(agentId);
    }

    @Override
    public OpportunityUseCompletion lastUseCompletion(ObjectId agentId) {
        return useLifecycle.lastCompletion(agentId);
    }

    private void complete(ActiveUse active) {
        OpportunityUseResult result = apply(active.agentId, active.sourceId, active.satisfaction, active.site);
        useLifecycle.recordCompletion(
                active.agentId,
                new OpportunityUseCompletion(
                        active.actionId,
                        active.agentId,
                        active.target,
                        active.site,
                        active.startedTick,
                        time.tick(),
                        result));
    }

    private OpportunityUseResult apply(
            ObjectId agentId,
            ObjectId sourceId,
            NeedSatisfaction satisfaction,
            InteractionSite site) {
        if (!atSite(agentId, site) || !eligible(agentId, sourceId, satisfaction, true)) {
            return new OpportunityUseResult(false, UNAVAILABLE);
        }
        if (satisfaction.consumesStock() && !stocks.consume(sourceId, satisfaction.consumedQuantity())) {
            return new OpportunityUseResult(false, UNAVAILABLE);
        }
        long applied = needs.satisfy(agentId, satisfaction.needId(), satisfaction.amount());
        if (applied <= 0) {
            throw new IllegalStateException("validated need satisfaction applied no benefit: " + agentId);
        }
        return new OpportunityUseResult(true, USED);
    }

    private Selection select(ObjectId agentId, ObjectId sourceId, boolean requireCoLocated) {
        WorldObject agentObject = objects.get(agentId);
        WorldObject sourceObject = objects.get(sourceId);
        if (agentObject == null || sourceObject == null || !agents.has(agentObject.definitionId())
                || !definitions.has(sourceObject.definitionId())) return null;
        if (requireCoLocated && !coLocated(agentId, sourceId)) return null;

        AgentDefinition agent = agents.get(agentObject.definitionId());
        NeedSatisfaction best = null;
        long bestBenefit = 0L;
        long bestLevel = 0L;
        long bestMax = 0L;
        for (int index = 0; index < definitions.count(sourceObject.definitionId()); index++) {
            NeedSatisfaction satisfaction = definitions.satisfactionAt(sourceObject.definitionId(), index);
            if (satisfaction.requiredCapability() != null
                    && !agent.hasCapability(satisfaction.requiredCapability())) continue;
            if (!needs.has(agentId, satisfaction.needId()) || !stockAvailable(sourceId, satisfaction)) continue;
            long level = needs.level(agentId, satisfaction.needId());
            if (!motivated(agentObject, satisfaction.needId(), level)) continue;
            long benefit = Math.min(level, satisfaction.amount());
            if (benefit > bestBenefit
                    || (benefit == bestBenefit && benefit > 0L && best != null
                    && satisfaction.needId().compareTo(best.needId()) < 0)) {
                best = satisfaction;
                bestBenefit = benefit;
                bestLevel = level;
                bestMax = needs.maxLevel(agentId, satisfaction.needId());
            }
        }
        return best == null || bestBenefit <= 0L
                ? null
                : new Selection(best, bestBenefit, bestLevel, bestMax);
    }

    private boolean motivated(WorldObject agentObject, NeedId needId, long level) {
        return level >= motivations.activationLevel(agentObject.definitionId(), needId);
    }

    private boolean eligible(
            ObjectId agentId,
            ObjectId sourceId,
            NeedSatisfaction satisfaction,
            boolean requireCoLocated) {
        WorldObject agentObject = objects.get(agentId);
        WorldObject sourceObject = objects.get(sourceId);
        if (agentObject == null || sourceObject == null || !agents.has(agentObject.definitionId())
                || !definitions.has(sourceObject.definitionId())) return false;
        if (requireCoLocated && !coLocated(agentId, sourceId)) return false;
        AgentDefinition agent = agents.get(agentObject.definitionId());
        if (satisfaction.requiredCapability() != null
                && !agent.hasCapability(satisfaction.requiredCapability())) return false;
        if (!needs.has(agentId, satisfaction.needId()) || needs.level(agentId, satisfaction.needId()) <= 0L) {
            return false;
        }
        return stockAvailable(sourceId, satisfaction);
    }

    private boolean atSite(ObjectId agentId, InteractionSite site) {
        return transforms.has(agentId)
                && transforms.x(agentId) == site.x()
                && transforms.y(agentId) == site.y()
                && transforms.z(agentId) == site.z();
    }

    private boolean coLocated(ObjectId agentId, ObjectId sourceId) {
        return transforms.has(agentId) && transforms.has(sourceId)
                && transforms.x(agentId) == transforms.x(sourceId)
                && transforms.y(agentId) == transforms.y(sourceId)
                && transforms.z(agentId) == transforms.z(sourceId);
    }

    private boolean stockAvailable(ObjectId sourceId, NeedSatisfaction satisfaction) {
        if (!satisfaction.consumesStock()) return true;
        if (!stocks.has(sourceId)) {
            throw new IllegalStateException(
                    "need satisfaction consumes stock but source has no consumable stock: " + sourceId);
        }
        return stocks.quantity(sourceId) >= satisfaction.consumedQuantity();
    }

    private record Selection(
            NeedSatisfaction satisfaction,
            long benefit,
            long level,
            long maxLevel) { }

    private record ObjectTarget(ObjectId sourceId) implements OpportunityTarget {
        private ObjectTarget {
            if (sourceId == null) throw new IllegalArgumentException("sourceId must not be null");
        }

        @Override
        public String debugKey() {
            return "object:" + sourceId.asLong();
        }
    }

    private static final class ActiveUse {
        private final OpportunityUseActionId actionId;
        private final ObjectId agentId;
        private final OpportunityTarget target;
        private final InteractionSite site;
        private final ObjectId sourceId;
        private final NeedSatisfaction satisfaction;
        private final long startedTick;
        @SuppressWarnings("unused")
        private final long expectedCompletionTick;

        private ActiveUse(
                OpportunityUseActionId actionId,
                ObjectId agentId,
                OpportunityTarget target,
                InteractionSite site,
                ObjectId sourceId,
                NeedSatisfaction satisfaction,
                long startedTick,
                long expectedCompletionTick) {
            this.actionId = actionId;
            this.agentId = agentId;
            this.target = target;
            this.site = site;
            this.sourceId = sourceId;
            this.satisfaction = satisfaction;
            this.startedTick = startedTick;
            this.expectedCompletionTick = expectedCompletionTick;
        }
    }
}
