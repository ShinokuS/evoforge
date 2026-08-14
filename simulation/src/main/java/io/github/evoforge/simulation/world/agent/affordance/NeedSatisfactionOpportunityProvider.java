package io.github.evoforge.simulation.world.agent.affordance;

import io.github.evoforge.simulation.result.ResultCode;
import io.github.evoforge.simulation.world.agent.AgentDefinition;
import io.github.evoforge.simulation.world.agent.AgentDefinitions;
import io.github.evoforge.simulation.world.agent.opportunity.AgentOpportunityProvider;
import io.github.evoforge.simulation.world.agent.opportunity.OpportunityEvaluation;
import io.github.evoforge.simulation.world.agent.opportunity.OpportunityUseResult;
import io.github.evoforge.simulation.world.agent.need.NeedSystem;
import io.github.evoforge.simulation.world.object.ObjectId;
import io.github.evoforge.simulation.world.object.ObjectLookup;
import io.github.evoforge.simulation.world.object.WorldObject;
import io.github.evoforge.simulation.world.spatial.TransformLookup;

/** First mechanic-owned opportunity provider: persistent sources that satisfy needs. */
public final class NeedSatisfactionOpportunityProvider implements AgentOpportunityProvider {

    private static final long DISTANCE_SCALE = 1024L;
    private static final ResultCode USED = ResultCode.of("needs", "satisfied");
    private static final ResultCode UNAVAILABLE = ResultCode.of("needs", "opportunity_unavailable");

    private final ObjectLookup objects;
    private final TransformLookup transforms;
    private final AgentDefinitions agents;
    private final NeedSatisfactionDefinitions definitions;
    private final NeedSystem needs;

    public NeedSatisfactionOpportunityProvider(
            ObjectLookup objects,
            TransformLookup transforms,
            AgentDefinitions agents,
            NeedSatisfactionDefinitions definitions,
            NeedSystem needs) {
        if (objects == null || transforms == null || agents == null || definitions == null || needs == null) {
            throw new IllegalArgumentException("need-satisfaction provider dependencies must not be null");
        }
        this.objects = objects;
        this.transforms = transforms;
        this.agents = agents;
        this.definitions = definitions;
        this.needs = needs;
    }

    @Override
    public String id() {
        return "needs:satisfaction";
    }

    @Override
    public OpportunityEvaluation evaluate(ObjectId agentId, ObjectId sourceId, int distance) {
        if (distance < 0) {
            throw new IllegalArgumentException("distance must be >= 0");
        }
        WorldObject agentObject = objects.get(agentId);
        WorldObject sourceObject = objects.get(sourceId);
        if (agentObject == null || sourceObject == null || !agents.has(agentObject.definitionId())
                || !definitions.has(sourceObject.definitionId())) {
            return null;
        }

        AgentDefinition agent = agents.get(agentObject.definitionId());
        OpportunityEvaluation best = null;
        for (int index = 0; index < definitions.count(sourceObject.definitionId()); index++) {
            NeedSatisfaction satisfaction = definitions.satisfactionAt(sourceObject.definitionId(), index);
            if (satisfaction.requiredCapability() != null
                    && !agent.hasCapability(satisfaction.requiredCapability())) {
                continue;
            }
            if (!needs.has(agentId, satisfaction.needId())) {
                continue;
            }
            long deficit = needs.level(agentId, satisfaction.needId());
            if (deficit <= 0) {
                continue;
            }
            long benefit = Math.min(deficit, satisfaction.amount());
            long scaled;
            try {
                scaled = Math.multiplyExact(benefit, DISTANCE_SCALE);
            } catch (ArithmeticException exception) {
                throw new IllegalStateException("opportunity score overflow", exception);
            }
            long score = Math.max(1L, scaled / ((long) distance + 1L));
            OpportunityEvaluation evaluation = new OpportunityEvaluation(
                    score, benefit, satisfaction.needId().value());
            if (best == null || evaluation.score() > best.score()
                    || (evaluation.score() == best.score()
                        && evaluation.motivation().compareTo(best.motivation()) < 0)) {
                best = evaluation;
            }
        }
        return best;
    }

    @Override
    public OpportunityUseResult use(ObjectId agentId, ObjectId sourceId) {
        WorldObject agentObject = objects.get(agentId);
        WorldObject sourceObject = objects.get(sourceId);
        if (agentObject == null || sourceObject == null || !transforms.has(agentId) || !transforms.has(sourceId)
                || !agents.has(agentObject.definitionId()) || !definitions.has(sourceObject.definitionId())) {
            return new OpportunityUseResult(false, UNAVAILABLE);
        }
        if (transforms.x(agentId) != transforms.x(sourceId)
                || transforms.y(agentId) != transforms.y(sourceId)
                || transforms.z(agentId) != transforms.z(sourceId)) {
            return new OpportunityUseResult(false, UNAVAILABLE);
        }

        AgentDefinition agent = agents.get(agentObject.definitionId());
        NeedSatisfaction best = null;
        long bestBenefit = 0;
        for (int index = 0; index < definitions.count(sourceObject.definitionId()); index++) {
            NeedSatisfaction satisfaction = definitions.satisfactionAt(sourceObject.definitionId(), index);
            if (satisfaction.requiredCapability() != null
                    && !agent.hasCapability(satisfaction.requiredCapability())) {
                continue;
            }
            if (!needs.has(agentId, satisfaction.needId())) {
                continue;
            }
            long benefit = Math.min(needs.level(agentId, satisfaction.needId()), satisfaction.amount());
            if (benefit > bestBenefit) {
                best = satisfaction;
                bestBenefit = benefit;
            }
        }
        if (best == null || bestBenefit <= 0) {
            return new OpportunityUseResult(false, UNAVAILABLE);
        }
        long applied = needs.satisfy(agentId, best.needId(), best.amount());
        return new OpportunityUseResult(applied > 0, applied > 0 ? USED : UNAVAILABLE);
    }
}
