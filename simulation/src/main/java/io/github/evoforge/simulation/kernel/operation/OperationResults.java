package io.github.evoforge.simulation.kernel.operation;

public final class OperationResults {

    private OperationResults() {
    }

    public static <R extends OperationResult> R requireAccepted(
            R result) {

        if (result == null) {
            throw new IllegalArgumentException(
                    "result must not be null");
        }

        if (!result.accepted()) {
            throw new IllegalStateException(
                    "operation was rejected: " + result.code());
        }

        return result;
    }
}
