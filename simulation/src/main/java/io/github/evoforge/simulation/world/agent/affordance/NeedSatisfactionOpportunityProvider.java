package io.github.evoforge.simulation.world.agent.affordance;

import io.github.evoforge.simulation.result.ResultCode;
import io.github.evoforge.simulation.time.ProcessScheduler;
import io.github.evoforge.simulation.time.SimulationTime;
import io.github.evoforge.simulation.world.agent.AgentDefinition;
import io.github.evoforge.simulation.world.agent.AgentDefinitions;
import io.github.evoforge.simulation.world.agent.knowledge.need.NeedSolutionKnowledgeDefinitions;
import io.github.evoforge.simulation.world.agent.need.NeedId;
import io.github.evoforge.simulation.world.agent.need.NeedSystem;
import io.github.evoforge.simulation.world.agent.opportunity.AgentOpportunityProvider;
import io.github.evoforge.simulation.world.agent.opportunity.OpportunityEvaluation;
import io.github.evoforge.simulation.world.agent.opportunity.OpportunitySearchDemand;
import io.github.evoforge.simulation.world.agent.opportunity.OpportunityUseActionId;
import io.github.evoforge.simulation.world.agent.opportunity.OpportunityUseCompletion;
import io.github.evoforge.simulation.world.agent.opportunity.OpportunityUseResult;
import io.github.evoforge.simulation.world.agent.opportunity.OpportunityUseStartAttempt;
import io.github.evoforge.simulation.world.mechanics.consumption.ConsumableStockSystem;
import io.github.evoforge.simulation.world.object.ObjectId;
import io.github.evoforge.simulation.world.object.ObjectLookup;
import io.github.evoforge.simulation.world.object.WorldObject;
import io.github.evoforge.simulation.world.spatial.TransformLookup;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Mechanic-owned need opportunities plus knowledge-gated search and provider-owned use timing. */
public final class NeedSatisfactionOpportunityProvider implements AgentOpportunityProvider {
    private static final long DISTANCE_SCALE = 1024L;
    private static final ResultCode STARTED = ResultCode.of("needs", "satisfaction_started");
    private static final ResultCode USED = ResultCode.of("needs", "satisfied");
    private static final ResultCode UNAVAILABLE = ResultCode.of("needs", "opportunity_unavailable");

    private final ObjectLookup objects;
    private final TransformLookup transforms;
    private final AgentDefinitions agents;
    private final NeedSatisfactionDefinitions definitions;
    private final NeedSolutionKnowledgeDefinitions knowledge;
    private final NeedSystem needs;
    private final ConsumableStockSystem stocks;
    private final SimulationTime time;
    private final Map<ObjectId, ActiveUse> activeByAgent = new HashMap<>();
    private final Map<Long, ActiveUse> activeByProcess = new HashMap<>();
    private final Map<ObjectId, OpportunityUseCompletion> lastCompletionByAgent = new HashMap<>();
    private ProcessScheduler scheduler;
    private long nextUseId;

    public NeedSatisfactionOpportunityProvider(
            ObjectLookup objects,
            TransformLookup transforms,
            AgentDefinitions agents,
            NeedSatisfactionDefinitions definitions,
            NeedSolutionKnowledgeDefinitions knowledge,
            NeedSystem needs,
            ConsumableStockSystem stocks,
            SimulationTime time) {
        if (objects == null || transforms == null || agents == null || definitions == null
                || knowledge == null || needs == null || stocks == null || time == null) {
            throw new IllegalArgumentException("need-satisfaction provider dependencies must not be null");
        }
        this.objects = objects;
        this.transforms = transforms;
        this.agents = agents;
        this.definitions = definitions;
        this.knowledge = knowledge;
        this.needs = needs;
        this.stocks = stocks;
        this.time = time;
    }

    public void bindScheduler(ProcessScheduler scheduler) {
        if (scheduler == null) throw new IllegalArgumentException("scheduler must not be null");
        if (this.scheduler != null) throw new IllegalStateException("need-satisfaction scheduler is already bound");
        this.scheduler = scheduler;
    }

    /** Completes one previously scheduled use lifecycle. */
    public void resume(long processId) {
        ActiveUse active = activeByProcess.remove(processId);
        if (active == null) throw new IllegalStateException("unknown need-satisfaction use process: " + processId);
        activeByAgent.remove(active.agentId, active);
        complete(active);
    }

    @Override
    public String id() { return "needs:satisfaction"; }

    @Override
    public List<OpportunitySearchDemand> searchDemands(ObjectId agentId) {
        WorldObject agentObject = objects.get(agentId);
        if (agentObject == null || !agents.has(agentObject.definitionId())) return List.of();
        List<OpportunitySearchDemand> result = new ArrayList<>();
        for (int index = 0; index < needs.needCount(agentId); index++) {
            NeedId needId = needs.needAt(agentId, index);
            long level = needs.level(agentId, needId);
            if (level <= 0 || !knowledge.knows(agentObject.definitionId(), needId)) continue;
            long max = needs.maxLevel(agentId, needId);
            long urgency;
            try {
                urgency = Math.max(1L, Math.multiplyExact(level, 1024L) / Math.max(1L, max));
            } catch (ArithmeticException exception) {
                throw new IllegalStateException("search urgency overflow", exception);
            }
            result.add(new OpportunitySearchDemand(needId.value(), urgency));
        }
        return List.copyOf(result);
    }

    @Override
    public OpportunityEvaluation evaluate(ObjectId agentId, ObjectId sourceId, int distance) {
        if (distance < 0) throw new IllegalArgumentException("distance must be >= 0");
        Selection selection = select(agentId, sourceId, false);
        if (selection == null) return null;
        long scaled;
        try {
            scaled = Math.multiplyExact(selection.benefit, DISTANCE_SCALE);
        } catch (ArithmeticException exception) {
            throw new IllegalStateException("opportunity score overflow", exception);
        }
        long score = Math.max(1L, scaled / ((long) distance + 1L));
        return new OpportunityEvaluation(score, selection.benefit, selection.satisfaction.needId().value());
    }

    @Override
    public OpportunityUseStartAttempt startUse(ObjectId agentId, ObjectId sourceId) {
        if (activeByAgent.containsKey(agentId)) return OpportunityUseStartAttempt.rejected(UNAVAILABLE);
        Selection selection = select(agentId, sourceId, true);
        if (selection == null) return OpportunityUseStartAttempt.rejected(UNAVAILABLE);
        if (nextUseId == Long.MAX_VALUE) throw new IllegalStateException("opportunity use id space exhausted");

        long startedTick = time.tick();
        long expectedCompletionTick;
        try {
            expectedCompletionTick = Math.addExact(startedTick, selection.satisfaction.useDurationTicks());
        } catch (ArithmeticException exception) {
            throw new IllegalStateException("opportunity use completion tick overflow", exception);
        }
        OpportunityUseActionId actionId = new OpportunityUseActionId(nextUseId++);
        ActiveUse active = new ActiveUse(
                actionId,
                agentId,
                sourceId,
                selection.satisfaction,
                startedTick,
                expectedCompletionTick);

        if (selection.satisfaction.useDurationTicks() == 0L) {
            complete(active);
        } else {
            requireScheduler();
            activeByAgent.put(agentId, active);
            activeByProcess.put(actionId.value(), active);
            scheduler.scheduleAfter(selection.satisfaction.useDurationTicks(), actionId.value());
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
        return agentId != null && activeByAgent.containsKey(agentId);
    }

    @Override
    public OpportunityUseCompletion lastUseCompletion(ObjectId agentId) {
        return agentId == null ? null : lastCompletionByAgent.get(agentId);
    }

    private void complete(ActiveUse active) {
        OpportunityUseResult result = apply(active.agentId, active.sourceId, active.satisfaction);
        lastCompletionByAgent.put(
                active.agentId,
                new OpportunityUseCompletion(
                        active.actionId,
                        active.agentId,
                        active.sourceId,
                        active.startedTick,
                        time.tick(),
                        result));
    }

    private OpportunityUseResult apply(ObjectId agentId, ObjectId sourceId, NeedSatisfaction satisfaction) {
        if (!eligible(agentId, sourceId, satisfaction, true)) {
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
        for (int index = 0; index < definitions.count(sourceObject.definitionId()); index++) {
            NeedSatisfaction satisfaction = definitions.satisfactionAt(sourceObject.definitionId(), index);
            if (satisfaction.requiredCapability() != null
                    && !agent.hasCapability(satisfaction.requiredCapability())) continue;
            if (!needs.has(agentId, satisfaction.needId()) || !stockAvailable(sourceId, satisfaction)) continue;
            long benefit = Math.min(needs.level(agentId, satisfaction.needId()), satisfaction.amount());
            if (benefit > bestBenefit
                    || (benefit == bestBenefit && benefit > 0L && best != null
                    && satisfaction.needId().compareTo(best.needId()) < 0)) {
                best = satisfaction;
                bestBenefit = benefit;
            }
        }
        return best == null || bestBenefit <= 0L ? null : new Selection(best, bestBenefit);
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

    private void requireScheduler() {
        if (scheduler == null) throw new IllegalStateException("need-satisfaction scheduler is not bound");
    }

    private record Selection(NeedSatisfaction satisfaction, long benefit) { }

    private static final class ActiveUse {
        private final OpportunityUseActionId actionId;
        private final ObjectId agentId;
        private final ObjectId sourceId;
        private final NeedSatisfaction satisfaction;
        private final long startedTick;
        @SuppressWarnings("unused")
        private final long expectedCompletionTick;

        private ActiveUse(
                OpportunityUseActionId actionId,
                ObjectId agentId,
                ObjectId sourceId,
                NeedSatisfaction satisfaction,
                long startedTick,
                long expectedCompletionTick) {
            this.actionId = actionId;
            this.agentId = agentId;
            this.sourceId = sourceId;
            this.satisfaction = satisfaction;
            this.startedTick = startedTick;
            this.expectedCompletionTick = expectedCompletionTick;
        }
    }
}
