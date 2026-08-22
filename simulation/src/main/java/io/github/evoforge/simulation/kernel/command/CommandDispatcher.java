package io.github.evoforge.simulation.kernel.command;

import java.util.HashMap;
import java.util.Map;

public final class CommandDispatcher {

    private final Map<Class<?>, Invocation> handlers =
            new HashMap<>();

    public <C extends Command<R>, R extends CommandResult> void register(
            Class<C> commandType,
            CommandHandler<C, R> handler) {

        if (commandType == null) {
            throw new IllegalArgumentException(
                    "commandType must not be null");
        }

        if (handler == null) {
            throw new IllegalArgumentException(
                    "handler must not be null");
        }

        if (handlers.containsKey(commandType)) {
            throw new IllegalStateException(
                    "handler already registered for "
                            + commandType.getName());
        }

        handlers.put(
                commandType,
                command -> handler.handle(
                        commandType.cast(command)));
    }

    public <R extends CommandResult> R dispatch(
            Command<R> command) {

        if (command == null) {
            throw new IllegalArgumentException(
                    "command must not be null");
        }

        Invocation invocation =
                handlers.get(command.getClass());

        if (invocation == null) {
            throw new IllegalStateException(
                    "no handler registered for "
                            + command.getClass().getName());
        }

        CommandResult result =
                invocation.invoke(command);

        if (result == null) {
            throw new IllegalStateException(
                    "handler returned null for "
                            + command.getClass().getName());
        }

        @SuppressWarnings("unchecked")
        R typedResult = (R) result;
        return typedResult;
    }

    @FunctionalInterface
    private interface Invocation {

        CommandResult invoke(
                Command<? extends CommandResult> command);
    }
}
