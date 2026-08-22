package io.github.evoforge.simulation.kernel.command;

@FunctionalInterface
public interface CommandHandler<
        C extends Command<R>,
        R extends CommandResult> {

    R handle(C command);
}
