package io.github.evoforge.simulation.result;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

final class OperationResultsTest {

    @Test
    void requireAcceptedReturnsOriginalAcceptedResult() {
        TestResult result = TestResult.ACCEPTED;

        assertSame(
                result,
                OperationResults.requireAccepted(result));
    }

    @Test
    void requireAcceptedRejectsUnexpectedDomainRejection() {
        assertThrows(
                IllegalStateException.class,
                () -> OperationResults.requireAccepted(
                        TestResult.REJECTED));
    }

    @Test
    void requireAcceptedRejectsNull() {
        assertThrows(
                IllegalArgumentException.class,
                () -> OperationResults.requireAccepted(null));
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
