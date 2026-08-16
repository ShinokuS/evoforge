package io.github.evoforge.simulation.world.agent.affordance.liquid;

import io.github.evoforge.simulation.result.ResultCode;
import io.github.evoforge.simulation.time.ProcessScheduler;
import io.github.evoforge.simulation.time.SimulationTime;
import io.github.evoforge.simulation.world.agent.decision.UtilityMath;
import io.github.evoforge.simulation.world.agent.knowledge.need.NeedSolutionKnowledgeDefinitions;
import io.github.evoforge.simulation.world.agent.need.NeedId;
import io.github.evoforge.simulation.world.agent.need.NeedSystem;
import io.github.evoforge.simulation.world.agent.need.motivation.NeedMotivationDefinitions;
import io.github.evoforge.simulation.world.agent.opportunity.AgentOpportunity;
import io.github.evoforge.simulation.world.agent.opportunity.AgentOpportunityProvider;
import io.github.evoforge.simulation.world.agent.opportunity.InteractionSite;
import io.github.evoforge.simulation.world.agent.opportunity.OpportunityEvaluation;
import io.github.evoforge.simulation.world.agent.opportunity.OpportunitySearchDemand;
import io.github.evoforge.simulation.world.agent.opportunity.OpportunityTarget;
import io.github.evoforge.simulation.world.agent.opportunity.OpportunityUseActionId;
import io.github.evoforge.simulation.world.agent.opportunity.OpportunityUseCompletion;
import io.github.evoforge.simulation.world.agent.opportunity.OpportunityUseResult;
import io.github.evoforge.simulation.world.agent.opportunity.OpportunityUseStartAttempt;
import io.github.evoforge.simulation.world.agent.perception.PerceivedCell;
import io.github.evoforge.simulation.world.agent.perception.PerceptionSnapshot;
import io.github.evoforge.simulation.world.landscape.liquid.LiquidSystem;
import io.github.evoforge.simulation.world.landscape.liquid.LiquidTypeId;
import io.github.evoforge.simulation.world.mechanics.geometry.CellVolume;
import io.github.evoforge.simulation.world.mechanics.interaction.CellOffset;
import io.github.evoforge.simulation.world.mechanics.interaction.InteractionAccessResolver;
import io.github.evoforge.simulation.world.mechanics.interaction.InteractionReachPattern;
import io.github.evoforge.simulation.world.mechanics.measurement.PhysicalCellVolume;
import io.github.evoforge.simulation.world.object.ObjectId;
import io.github.evoforge.simulation.world.object.ObjectLookup;
import io.github.evoforge.simulation.world.object.WorldObject;
import io.github.evoforge.simulation.world.spatial.TransformLookup;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Need-satisfaction opportunities backed directly by finite perceived free-liquid cells. */
public final class LiquidDrinkOpportunityProvider implements AgentOpportunityProvider {
    private static final ResultCode STARTED = ResultCode.of("drinking", "started");
    private static final ResultCode DRANK = ResultCode.of("drinking", "consumed_liquid");
    private static final ResultCode UNAVAILABLE = ResultCode.of("drinking", "unavailable");

    private final ObjectLookup objects;
    private final TransformLookup transforms;
    private final LiquidDrinkDefinitions definitions;
    private final NeedSolutionKnowledgeDefinitions knowledge;
    private final NeedMotivationDefinitions motivations;
    private final NeedSystem needs;
    private final LiquidSystem liquids;
    private final PhysicalCellVolume physicalVolume;
    private final InteractionAccessResolver access;
    private final Runnable liquidChanged;
    private final SimulationTime time;
    private final Map<ObjectId, ActiveUse> activeByAgent = new HashMap<>();
    private final Map<Long, ActiveUse> activeByProcess = new HashMap<>();
    private final Map<ObjectId, OpportunityUseCompletion> lastCompletionByAgent = new HashMap<>();
    private ProcessScheduler scheduler;
    private long nextUseId;

    public LiquidDrinkOpportunityProvider(
            ObjectLookup objects,
            TransformLookup transforms,
            LiquidDrinkDefinitions definitions,
            NeedSolutionKnowledgeDefinitions knowledge,
            NeedMotivationDefinitions motivations,
            NeedSystem needs,
            LiquidSystem liquids,
            PhysicalCellVolume physicalVolume,
            InteractionAccessResolver access,
            Runnable liquidChanged,
            SimulationTime time) {
        if (objects == null || transforms == null || definitions == null || knowledge == null
                || motivations == null || needs == null || liquids == null || physicalVolume == null
                || access == null || liquidChanged == null || time == null) {
            throw new IllegalArgumentException("liquid drinking dependencies must not be null");
        }
        this.objects = objects;
        this.transforms = transforms;
        this.definitions = definitions;
        this.knowledge = knowledge;
        this.motivations = motivations;
        this.needs = needs;
        this.liquids = liquids;
        this.physicalVolume = physicalVolume;
        this.access = access;
        this.liquidChanged = liquidChanged;
        this.time = time;
    }

    public void bindScheduler(ProcessScheduler scheduler) {
        if (scheduler == null) throw new IllegalArgumentException("scheduler must not be null");
        if (this.scheduler != null) throw new IllegalStateException("liquid drinking scheduler is already bound");
        this.scheduler = scheduler;
    }

    public void resume(long processId) {
        ActiveUse active = activeByProcess.remove(processId);
        if (active == null) throw new IllegalStateException("unknown liquid drinking process: " + processId);
        activeByAgent.remove(active.agentId, active);
        complete(active);
    }

    @Override
    public String id() { return "needs:liquid_drink"; }

    @Override
    public List<AgentOpportunity> opportunities(ObjectId agentId, PerceptionSnapshot perception) {
        if (perception == null || !agentId.equals(perception.observerId())) {
            throw new IllegalArgumentException("perception must belong to the evaluated agent");
        }
        WorldObject agent = objects.get(agentId);
        if (agent == null || !definitions.has(agent.definitionId())) return List.of();

        Map<Cell, Integer> perceivedDistance = new HashMap<>();
        for (PerceivedCell cell : perception.cells()) {
            perceivedDistance.put(new Cell(cell.x(), cell.y(), cell.z()), cell.distance());
        }

        List<AgentOpportunity> result = new ArrayList<>();
        Set<CandidateKey> emitted = new HashSet<>();
        for (PerceivedCell targetCell : perception.cells()) {
            LiquidTypeId resident = liquids.lookup().typeAt(targetCell.x(), targetCell.y(), targetCell.z());
            if (resident == null || liquids.lookup().amount(targetCell.x(), targetCell.y(), targetCell.z()) <= 0) continue;
            for (int definitionIndex = 0; definitionIndex < definitions.count(agent.definitionId()); definitionIndex++) {
                LiquidDrinkDefinition definition = definitions.definitionAt(agent.definitionId(), definitionIndex);
                if (!definition.liquidType().equals(resident) || !motivated(agent, definition.needId())) continue;
                LiquidCellTarget target = new LiquidCellTarget(
                        resident, targetCell.x(), targetCell.y(), targetCell.z());
                for (int patternIndex = 0; patternIndex < definition.reach().count(); patternIndex++) {
                    InteractionReachPattern pattern = definition.reach().patternAt(patternIndex);
                    CellOffset offset = pattern.targetOffset();
                    int siteX = targetCell.x() - offset.x();
                    int siteY = targetCell.y() - offset.y();
                    int siteZ = targetCell.z() - offset.z();
                    Integer distance = perceivedDistance.get(new Cell(siteX, siteY, siteZ));
                    if (distance == null) continue;
                    if (!access.allows(
                            siteX, siteY, siteZ,
                            targetCell.x(), targetCell.y(), targetCell.z(),
                            definition.reach())) continue;
                    CandidateKey key = new CandidateKey(target, siteX, siteY, siteZ);
                    if (!emitted.add(key)) continue;
                    InteractionSite site = new InteractionSite(siteX, siteY, siteZ, distance);
                    OpportunityEvaluation evaluation = evaluate(agentId, target, site);
                    if (evaluation != null) result.add(new AgentOpportunity(target, site, evaluation));
                }
            }
        }
        return List.copyOf(result);
    }

    @Override
    public List<OpportunitySearchDemand> searchDemands(ObjectId agentId) {
        WorldObject agent = objects.get(agentId);
        if (agent == null || !definitions.has(agent.definitionId())) return List.of();
        List<OpportunitySearchDemand> result = new ArrayList<>();
        Set<NeedId> emitted = new HashSet<>();
        for (int index = 0; index < definitions.count(agent.definitionId()); index++) {
            LiquidDrinkDefinition definition = definitions.definitionAt(agent.definitionId(), index);
            NeedId needId = definition.needId();
            if (!emitted.add(needId) || !motivated(agent, needId)
                    || !knowledge.knows(agent.definitionId(), needId)) continue;
            result.add(new OpportunitySearchDemand(
                    needId.value(),
                    UtilityMath.ratio(needs.level(agentId, needId), needs.maxLevel(agentId, needId))));
        }
        return List.copyOf(result);
    }

    @Override
    public OpportunityEvaluation evaluate(
            ObjectId agentId,
            OpportunityTarget target,
            InteractionSite site) {
        if (!(target instanceof LiquidCellTarget liquidTarget) || site == null) return null;
        WorldObject agent = objects.get(agentId);
        if (agent == null || !definitions.has(agent.definitionId())) return null;
        if (!atSiteOrReachableRelation(site, liquidTarget)) return null;
        int available = liquids.lookup().amountOf(
                liquidTarget.type, liquidTarget.x, liquidTarget.y, liquidTarget.z);
        if (available <= 0) return null;

        Selection selection = select(agentId, agent, liquidTarget.type, available);
        if (selection == null) return null;
        return new OpportunityEvaluation(
                selection.expectedRelief,
                UtilityMath.ratio(selection.level, selection.maxLevel),
                UtilityMath.ratio(selection.expectedRelief, selection.level),
                UtilityMath.travelFromDistance(site.distance()),
                selection.definition.needId().value());
    }

    @Override
    public OpportunityUseStartAttempt startUse(
            ObjectId agentId,
            OpportunityTarget target,
            InteractionSite site) {
        if (!(target instanceof LiquidCellTarget liquidTarget) || site == null
                || activeByAgent.containsKey(agentId) || !atSite(agentId, site)) {
            return OpportunityUseStartAttempt.rejected(UNAVAILABLE);
        }
        OpportunityEvaluation evaluation = evaluate(agentId, target, site);
        if (evaluation == null) return OpportunityUseStartAttempt.rejected(UNAVAILABLE);
        WorldObject agent = objects.get(agentId);
        Selection selection = select(
                agentId,
                agent,
                liquidTarget.type,
                liquids.lookup().amountOf(liquidTarget.type, liquidTarget.x, liquidTarget.y, liquidTarget.z));
        if (selection == null) return OpportunityUseStartAttempt.rejected(UNAVAILABLE);
        if (nextUseId == Long.MAX_VALUE) throw new IllegalStateException("liquid drink use id space exhausted");

        long startedTick = time.tick();
        long expectedCompletionTick = Math.addExact(startedTick, selection.definition.useDurationTicks());
        OpportunityUseActionId actionId = new OpportunityUseActionId(nextUseId++);
        ActiveUse active = new ActiveUse(
                actionId,
                agentId,
                target,
                site,
                selection.definition,
                startedTick,
                expectedCompletionTick);
        if (selection.definition.useDurationTicks() == 0L) {
            complete(active);
        } else {
            requireScheduler();
            activeByAgent.put(agentId, active);
            activeByProcess.put(actionId.value(), active);
            scheduler.scheduleAfter(selection.definition.useDurationTicks(), actionId.value());
        }
        return new OpportunityUseStartAttempt(
                true, actionId, startedTick, expectedCompletionTick, STARTED);
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
        OpportunityUseResult result = apply(active);
        lastCompletionByAgent.put(
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

    private OpportunityUseResult apply(ActiveUse active) {
        if (!(active.target instanceof LiquidCellTarget target)
                || !atSite(active.agentId, active.site)
                || !atSiteOrReachableRelation(active.site, target)) {
            return new OpportunityUseResult(false, UNAVAILABLE);
        }
        WorldObject agent = objects.get(active.agentId);
        if (agent == null || !needs.has(active.agentId, active.definition.needId())) {
            return new OpportunityUseResult(false, UNAVAILABLE);
        }
        long level = needs.level(active.agentId, active.definition.needId());
        if (level <= 0L) return new OpportunityUseResult(false, UNAVAILABLE);

        int requested = physicalVolume.cellVolumeForMilliliters(active.definition.requestedMillilitersPerUse());
        int removed = liquids.removeAtMost(target.type, target.x, target.y, target.z, requested);
        if (removed <= CellVolume.EMPTY) return new OpportunityUseResult(false, UNAVAILABLE);
        liquidChanged.run();

        long relief = proportionalRelief(
                active.definition.needReliefPerFullUse(),
                removed,
                requested);
        relief = Math.min(relief, level);
        long applied = needs.satisfy(active.agentId, active.definition.needId(), relief);
        if (applied <= 0L) {
            throw new IllegalStateException("removed liquid without applying validated thirst relief: " + active.agentId);
        }
        return new OpportunityUseResult(true, DRANK);
    }

    private Selection select(
            ObjectId agentId,
            WorldObject agent,
            LiquidTypeId type,
            int availableCellVolume) {
        if (agent == null || availableCellVolume <= 0) return null;
        LiquidDrinkDefinition best = null;
        long bestRelief = 0L;
        long bestLevel = 0L;
        long bestMax = 0L;
        for (int index = 0; index < definitions.count(agent.definitionId()); index++) {
            LiquidDrinkDefinition definition = definitions.definitionAt(agent.definitionId(), index);
            if (!definition.liquidType().equals(type)
                    || !needs.has(agentId, definition.needId())
                    || !motivated(agent, definition.needId())) continue;
            long level = needs.level(agentId, definition.needId());
            int requested = physicalVolume.cellVolumeForMilliliters(definition.requestedMillilitersPerUse());
            int expectedDraw = Math.min(requested, availableCellVolume);
            long possibleRelief = proportionalRelief(
                    definition.needReliefPerFullUse(), expectedDraw, requested);
            long expectedRelief = Math.min(level, possibleRelief);
            if (expectedRelief > bestRelief) {
                best = definition;
                bestRelief = expectedRelief;
                bestLevel = level;
                bestMax = needs.maxLevel(agentId, definition.needId());
            }
        }
        return best == null || bestRelief <= 0L
                ? null
                : new Selection(best, bestRelief, bestLevel, bestMax);
    }

    private boolean motivated(WorldObject agent, NeedId needId) {
        return needs.has(agent.id(), needId)
                && needs.level(agent.id(), needId)
                >= motivations.activationLevel(agent.definitionId(), needId);
    }

    private boolean atSite(ObjectId agentId, InteractionSite site) {
        return transforms.has(agentId)
                && transforms.x(agentId) == site.x()
                && transforms.y(agentId) == site.y()
                && transforms.z(agentId) == site.z();
    }

    private boolean atSiteOrReachableRelation(InteractionSite site, LiquidCellTarget target) {
        return access.allows(
                site.x(), site.y(), site.z(),
                target.x, target.y, target.z,
                definitionReachFor(target));
    }

    private io.github.evoforge.simulation.world.mechanics.interaction.InteractionReachProfile definitionReachFor(
            LiquidCellTarget target) {
        // Reach profiles are agent-definition data. The concrete target alone is intentionally insufficient;
        // this method is used only after the caller has resolved the current agent definition.
        // A target-independent fallback is not allowed.
        throw new UnsupportedOperationException("agent-specific reach must be resolved by caller");
    }

    private static long proportionalRelief(long fullRelief, int actual, int requested) {
        if (fullRelief <= 0L || actual <= 0 || requested <= 0) {
            throw new IllegalArgumentException("proportional relief values must be > 0");
        }
        BigInteger scaled = BigInteger.valueOf(fullRelief)
                .multiply(BigInteger.valueOf(actual))
                .divide(BigInteger.valueOf(requested));
        return Math.max(1L, scaled.longValueExact());
    }

    private void requireScheduler() {
        if (scheduler == null) throw new IllegalStateException("liquid drinking scheduler is not bound");
    }

    private record Cell(int x, int y, int z) { }

    private record CandidateKey(LiquidCellTarget target, int siteX, int siteY, int siteZ) { }

    private record Selection(
            LiquidDrinkDefinition definition,
            long expectedRelief,
            long level,
            long maxLevel) { }

    private record LiquidCellTarget(
            LiquidTypeId type,
            int x,
            int y,
            int z) implements OpportunityTarget {
        private LiquidCellTarget {
            if (type == null) throw new IllegalArgumentException("liquid type must not be null");
        }

        @Override
        public String debugKey() {
            return "liquid:" + type.value() + "@" + x + "," + y + "," + z;
        }
    }

    private static final class ActiveUse {
        private final OpportunityUseActionId actionId;
        private final ObjectId agentId;
        private final OpportunityTarget target;
        private final InteractionSite site;
        private final LiquidDrinkDefinition definition;
        private final long startedTick;
        @SuppressWarnings("unused")
        private final long expectedCompletionTick;

        private ActiveUse(
                OpportunityUseActionId actionId,
                ObjectId agentId,
                OpportunityTarget target,
                InteractionSite site,
                LiquidDrinkDefinition definition,
                long startedTick,
                long expectedCompletionTick) {
            this.actionId = actionId;
            this.agentId = agentId;
            this.target = target;
            this.site = site;
            this.definition = definition;
            this.startedTick = startedTick;
            this.expectedCompletionTick = expectedCompletionTick;
        }
    }
}
