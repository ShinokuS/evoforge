package io.github.evoforge.simulation.world.agent.opportunity;

import io.github.evoforge.simulation.result.OperationResult;
import io.github.evoforge.simulation.result.ResultCode;

/** Structured outcome of applying a selected opportunity after reaching its source. */
public record OpportunityUseResult(boolean accepted, ResultCode code) implements OperationResult {

    public OpportunityUseResult {
        if (code == null) {
            throw new IllegalArgumentException("code must not be null");
        }
    }
}
