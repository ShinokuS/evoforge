package io.github.evoforge.simulation.kernel.operation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.evoforge.simulation.mechanics.movement.command.MoveStepResult;
import org.junit.jupiter.api.Test;

final class OperationResultsTest {

    @Test
    void requireAcceptedReturnsOriginalAcceptedResult() {
        TestResult result = TestResult.ACCEPTED;
        assertSame(result, OperationResults.requireAccepted(result));
    }

    @Test
    void requireAcceptedRejectsUnexpectedDomainRejection() {
        assertThrows(
                IllegalStateException.class,
                () -> OperationResults.requireAccepted(TestResult.REJECTED));
    }

    @Test
    void requireAcceptedRejectsNull() {
        assertThrows(
                IllegalArgumentException.class,
                () -> OperationResults.requireAccepted(null));
    }

    @Test
    void movementCommandAdapterPreservesOpenResultCode() {
        ResultCode code = ResultCode.of("movement", "extension_result");
        MoveStepResult result = MoveStepResult.from(
                new OpenResult(false, code));

        assertFalse(result.accepted());
        assertEquals(code, result.code());
    }

    private record OpenResult(
            boolean accepted,
            ResultCode code)
            implements OperationResult {
    }

    private enum TestResult implements OperationResult {
        ACCEPTED(true, ResultCode.of("test", "accepted")),
        REJECTED(false, ResultCode.of("test", "rejected"));

        private final boolean accepted;
        private final ResultCode code;

        TestResult(
                boolean accepted,
                ResultCode code) {
            this.accepted = accepted;
            this.code = code;
        }

        @Override
        public boolean accepted() {
            return accepted;
        }

        @Override
        public ResultCode code() {
            return code;
        }
    }
}
