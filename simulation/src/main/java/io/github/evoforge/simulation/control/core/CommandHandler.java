package io.github.evoforge.simulation.control.core;

@FunctionalInterface
public interface CommandHandler<
        C extends Command<R>,
        R extends CommandResult> {

    R handle(C command);
}
