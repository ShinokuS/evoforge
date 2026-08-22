package io.github.evoforge.simulation.agents.opportunity;

import io.github.evoforge.simulation.kernel.operation.OperationResult;
import io.github.evoforge.simulation.kernel.operation.ResultCode;

/** Result of requesting one provider-owned opportunity use. */
public record OpportunityUseStartAttempt(
        boolean accepted,
        OpportunityUseActionId actionId,
        long startedTick,
        long expectedCompletionTick,
        ResultCode code) implements OperationResult {

    public OpportunityUseStartAttempt {
        if (code == null) throw new IllegalArgumentException("code must not be null");
        if (accepted) {
            if (actionId == null || startedTick < 0 || expectedCompletionTick < startedTick) {
                throw new IllegalArgumentException("accepted opportunity use requires valid identity and timing");
            }
        } else if (actionId != null || startedTick != -1L || expectedCompletionTick != -1L) {
            throw new IllegalArgumentException("rejected opportunity use must not expose identity or timing");
        }
    }

    public static OpportunityUseStartAttempt rejected(ResultCode code) {
        return new OpportunityUseStartAttempt(false, null, -1L, -1L, code);
    }
}
