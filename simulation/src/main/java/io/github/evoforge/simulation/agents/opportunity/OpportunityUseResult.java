package io.github.evoforge.simulation.agents.opportunity;

import io.github.evoforge.simulation.kernel.operation.OperationResult;
import io.github.evoforge.simulation.kernel.operation.ResultCode;

/** Structured outcome of applying a selected opportunity after reaching its source. */
public record OpportunityUseResult(boolean accepted, ResultCode code) implements OperationResult {

    public OpportunityUseResult {
        if (code == null) {
            throw new IllegalArgumentException("code must not be null");
        }
    }
}
