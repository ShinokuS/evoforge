package io.github.evoforge.simulation.control.sync;

import io.github.evoforge.simulation.control.core.Command;
import io.github.evoforge.simulation.control.core.CommandDispatcher;
import io.github.evoforge.simulation.control.core.CommandResult;

public final class SynchronousCommandGateway {

    private final CommandDispatcher dispatcher;

    public SynchronousCommandGateway(
            CommandDispatcher dispatcher) {

        if (dispatcher == null) {
            throw new IllegalArgumentException(
                    "dispatcher must not be null");
        }

        this.dispatcher = dispatcher;
    }

    public <R extends CommandResult> R submit(
            Command<R> command) {
        return dispatcher.dispatch(command);
    }
}
