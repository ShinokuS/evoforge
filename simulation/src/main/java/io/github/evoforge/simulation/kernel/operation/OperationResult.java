package io.github.evoforge.simulation.kernel.operation;

public interface OperationResult {

    boolean accepted();

    ResultCode code();
}
