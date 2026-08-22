package io.github.evoforge.simulation.agents.affordance.liquid;

import io.github.evoforge.simulation.kernel.operation.ResultCode;
import io.github.evoforge.simulation.kernel.scheduling.ProcessScheduler;
import io.github.evoforge.simulation.kernel.time.SimulationTime;
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
import io.github.evoforge.simulation.agents.perception.PerceivedCell;
import io.github.evoforge.simulation.agents.perception.PerceptionSnapshot;
import io.github.evoforge.simulation.world.liquid.LiquidSystem;
import io.github.evoforge.simulation.world.liquid.LiquidTypeId;
import io.github.evoforge.simulation.world.space.measurement.CellVolume;
import io.github.evoforge.simulation.world.interaction.CellOffset;
import io.github.evoforge.simulation.world.interaction.InteractionAccessResolver;
import io.github.evoforge.simulation.world.interaction.InteractionReachPattern;
import io.github.evoforge.simulation.world.space.measurement.PhysicalCellVolume;
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
    private final OpportunityUseLifecycle<ActiveUse> useLifecycle =
            new OpportunityUseLifecycle<>("liquid drinking", "liquid drink use id space exhausted");

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
        useLifecycle.bindScheduler(scheduler);
    }

    public void resume(long processId) {
        complete(useLifecycle.resume(processId));
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
            if (resident == null
                    || liquids.lookup().amount(targetCell.x(), targetCell.y(), targetCell.z()) <= 0) {
                continue;
            }
            for (int definitionIndex = 0;
                    definitionIndex < definitions.count(agent.definitionId());
                    definitionIndex++) {
                LiquidDrinkDefinition definition = definitions.definitionAt(
                        agent.definitionId(),
                        definitionIndex);
                if (!definition.liquidType().equals(resident)
                        || !motivated(agent, definition.needId())) {
                    continue;
                }
                LiquidCellTarget target = new LiquidCellTarget(
                        resident,
                        targetCell.x(),
                        targetCell.y(),
                        targetCell.z(),
                        definitionIndex);
                for (int patternIndex = 0;
                        patternIndex < definition.reach().count();
                        patternIndex++) {
                    InteractionReachPattern pattern = definition.reach().patternAt(patternIndex);
                    CellOffset offset = pattern.targetOffset();
                    int siteX = targetCell.x() - offset.x();
                    int siteY = targetCell.y() - offset.y();
                    int siteZ = targetCell.z() - offset.z();
                    Integer distance = perceivedDistance.get(new Cell(siteX, siteY, siteZ));
                    if (distance == null) continue;
                    if (!access.allows(
                            siteX,
                            siteY,
                            siteZ,
                            targetCell.x(),
                            targetCell.y(),
                            targetCell.z(),
                            definition.reach())) {
                        continue;
                    }
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
            if (!emitted.add(needId)
                    || !motivated(agent, needId)
                    || !knowledge.knows(agent.definitionId(), needId)) {
                continue;
            }
            result.add(new OpportunitySearchDemand(
                    needId.value(),
                    UtilityMath.ratio(
                            needs.level(agentId, needId),
                            needs.maxLevel(agentId, needId))));
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
        LiquidDrinkDefinition definition = definitionFor(agent, liquidTarget);
        if (definition == null || !motivated(agent, definition.needId())) return null;
        if (!access.allows(
                site.x(),
                site.y(),
                site.z(),
                liquidTarget.x,
                liquidTarget.y,
                liquidTarget.z,
                definition.reach())) {
            return null;
        }

        int available = liquids.lookup().amountOf(
                liquidTarget.type,
                liquidTarget.x,
                liquidTarget.y,
                liquidTarget.z);
        if (available <= 0) return null;
        long level = needs.level(agentId, definition.needId());
        int requested = physicalVolume.cellVolumeForMilliliters(
                definition.requestedMillilitersPerUse());
        int expectedDraw = Math.min(requested, available);
        long expectedRelief = Math.min(
                level,
                proportionalRelief(
                        definition.needReliefPerFullUse(),
                        expectedDraw,
                        requested));
        if (expectedRelief <= 0L) return null;
        return new OpportunityEvaluation(
                expectedRelief,
                UtilityMath.ratio(level, needs.maxLevel(agentId, definition.needId())),
                UtilityMath.ratio(expectedRelief, level),
                UtilityMath.travelFromDistance(site.distance()),
                definition.needId().value());
    }

    @Override
    public OpportunityUseStartAttempt startUse(
            ObjectId agentId,
            OpportunityTarget target,
            InteractionSite site) {
        if (!(target instanceof LiquidCellTarget liquidTarget)
                || site == null
                || useLifecycle.isActive(agentId)
                || !atSite(agentId, site)) {
            return OpportunityUseStartAttempt.rejected(UNAVAILABLE);
        }
        WorldObject agent = objects.get(agentId);
        LiquidDrinkDefinition definition = agent == null ? null : definitionFor(agent, liquidTarget);
        if (definition == null || evaluate(agentId, target, site) == null) {
            return OpportunityUseStartAttempt.rejected(UNAVAILABLE);
        }

        long startedTick = time.tick();
        long expectedCompletionTick = Math.addExact(
                startedTick,
                definition.useDurationTicks());
        OpportunityUseActionId actionId = useLifecycle.nextActionId();
        ActiveUse active = new ActiveUse(
                actionId,
                agentId,
                target,
                site,
                definition,
                startedTick,
                expectedCompletionTick);
        if (definition.useDurationTicks() == 0L) {
            complete(active);
        } else {
            useLifecycle.schedule(
                    agentId,
                    actionId,
                    definition.useDurationTicks(),
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
        OpportunityUseResult result = apply(active);
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

    private OpportunityUseResult apply(ActiveUse active) {
        if (!(active.target instanceof LiquidCellTarget target)
                || !atSite(active.agentId, active.site)
                || !access.allows(
                        active.site.x(),
                        active.site.y(),
                        active.site.z(),
                        target.x,
                        target.y,
                        target.z,
                        active.definition.reach())) {
            return new OpportunityUseResult(false, UNAVAILABLE);
        }
        WorldObject agent = objects.get(active.agentId);
        if (agent == null || !needs.has(active.agentId, active.definition.needId())) {
            return new OpportunityUseResult(false, UNAVAILABLE);
        }
        long level = needs.level(active.agentId, active.definition.needId());
        if (level <= 0L) return new OpportunityUseResult(false, UNAVAILABLE);

        int requested = physicalVolume.cellVolumeForMilliliters(
                active.definition.requestedMillilitersPerUse());
        int removed = liquids.removeAtMost(
                target.type,
                target.x,
                target.y,
                target.z,
                requested);
        if (removed <= CellVolume.EMPTY) return new OpportunityUseResult(false, UNAVAILABLE);
        liquidChanged.run();

        long relief = Math.min(
                level,
                proportionalRelief(
                        active.definition.needReliefPerFullUse(),
                        removed,
                        requested));
        long applied = needs.satisfy(active.agentId, active.definition.needId(), relief);
        if (applied <= 0L) {
            throw new IllegalStateException(
                    "removed liquid without applying validated thirst relief: " + active.agentId);
        }
        return new OpportunityUseResult(true, DRANK);
    }

    private LiquidDrinkDefinition definitionFor(WorldObject agent, LiquidCellTarget target) {
        if (agent == null
                || target.definitionIndex < 0
                || target.definitionIndex >= definitions.count(agent.definitionId())) {
            return null;
        }
        LiquidDrinkDefinition definition = definitions.definitionAt(
                agent.definitionId(),
                target.definitionIndex);
        return definition.liquidType().equals(target.type) ? definition : null;
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

    private static long proportionalRelief(long fullRelief, int actual, int requested) {
        if (fullRelief <= 0L || actual <= 0 || requested <= 0) {
            throw new IllegalArgumentException("proportional relief values must be > 0");
        }
        BigInteger scaled = BigInteger.valueOf(fullRelief)
                .multiply(BigInteger.valueOf(actual))
                .divide(BigInteger.valueOf(requested));
        return Math.max(1L, scaled.longValueExact());
    }

    private record Cell(int x, int y, int z) { }

    private record CandidateKey(LiquidCellTarget target, int siteX, int siteY, int siteZ) { }

    private record LiquidCellTarget(
            LiquidTypeId type,
            int x,
            int y,
            int z,
            int definitionIndex) implements OpportunityTarget {
        private LiquidCellTarget {
            if (type == null) throw new IllegalArgumentException("liquid type must not be null");
            if (definitionIndex < 0) {
                throw new IllegalArgumentException("definitionIndex must be >= 0");
            }
        }

        @Override
        public String debugKey() {
            return "liquid:" + type.value() + "@" + x + "," + y + "," + z + "#" + definitionIndex;
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
