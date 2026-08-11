package io.github.evoforge.simulation.control.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import io.github.evoforge.simulation.result.ResultCode;

final class CommandDispatcherTest {

    @Test
    void dispatchesRegisteredCommandByExactRuntimeType() {
        CommandDispatcher dispatcher =
                new CommandDispatcher();
        dispatcher.register(
                TestCommand.class,
                command -> TestResult.ACCEPTED);

        assertEquals(
                TestResult.ACCEPTED,
                dispatcher.dispatch(new TestCommand()));
    }

    @Test
    void duplicateRegistrationIsBootstrapError() {
        CommandDispatcher dispatcher =
                new CommandDispatcher();
        dispatcher.register(
                TestCommand.class,
                command -> TestResult.ACCEPTED);

        assertThrows(
                IllegalStateException.class,
                () -> dispatcher.register(
                        TestCommand.class,
                        command -> TestResult.ACCEPTED));
    }

    @Test
    void missingExactHandlerIsBootstrapError() {
        CommandDispatcher dispatcher =
                new CommandDispatcher();
        dispatcher.register(
                ParentCommand.class,
                command -> TestResult.ACCEPTED);

        assertThrows(
                IllegalStateException.class,
                () -> dispatcher.dispatch(
                        new ChildCommand()));
    }

    @Test
    void nullHandlerResultIsProgrammingError() {
        CommandDispatcher dispatcher =
                new CommandDispatcher();
        dispatcher.register(
                TestCommand.class,
                command -> null);

        assertThrows(
                IllegalStateException.class,
                () -> dispatcher.dispatch(new TestCommand()));
    }

    @Test
    void nullInputsAreRejected() {
        CommandDispatcher dispatcher =
                new CommandDispatcher();

        assertThrows(
                IllegalArgumentException.class,
                () -> dispatcher.register(
                        null,
                        command -> TestResult.ACCEPTED));
        assertThrows(
                IllegalArgumentException.class,
                () -> dispatcher.register(
                        TestCommand.class,
                        null));
        assertThrows(
                IllegalArgumentException.class,
                () -> dispatcher.dispatch(null));
    }

    private static final class TestCommand
            implements Command<TestResult> {
    }

    private static class ParentCommand
            implements Command<TestResult> {
    }

    private static final class ChildCommand
            extends ParentCommand {
    }

    private enum TestResult implements CommandResult {
        ACCEPTED;

        private static final ResultCode CODE =
                ResultCode.of("test", "accepted");

        @Override
        public boolean accepted() {
            return true;
        }

        @Override
        public ResultCode code() {
            return CODE;
        }
    }
}
