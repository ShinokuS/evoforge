package io.github.evoforge.simulation.world.agent.opportunity;

import io.github.evoforge.simulation.result.OperationResult;
import io.github.evoforge.simulation.result.ResultCode;

/** Result of requesting one provider-owned opportunity use. */
public record OpportunityUseStartAttempt(
        boolean accepted,
        OpportunityUseActionId actionId,
        ResultCode code) implements OperationResult {

    public OpportunityUseStartAttempt {
        if (code == null) throw new IllegalArgumentException("code must not be null");
        if (accepted != (actionId != null)) {
            throw new IllegalArgumentException("accepted opportunity use must have an actionId and rejection must not");
        }
    }
}
