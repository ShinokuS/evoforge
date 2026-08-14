package io.github.evoforge.simulation.control.movement;

import io.github.evoforge.simulation.control.core.CommandHandler;
import io.github.evoforge.simulation.world.mechanics.movement.MoveToStarter;
import io.github.evoforge.simulation.world.mechanics.movement.MoveToSystem;

public final class MoveToHandler
        implements CommandHandler<MoveToCommand, MoveToResult> {

    private final MoveToStarter moveTo;

    public MoveToHandler(
            MoveToSystem moveTo) {
        this(MoveToStarter.direct(moveTo));
    }

    public MoveToHandler(
            MoveToStarter moveTo) {
        if (moveTo == null) {
            throw new IllegalArgumentException(
                    "moveTo must not be null");
        }
        this.moveTo = moveTo;
    }

    @Override
    public MoveToResult handle(
            MoveToCommand command) {

        if (command == null) {
            throw new IllegalArgumentException(
                    "command must not be null");
        }

        return MoveToResult.from(
                moveTo.start(
                        command.objectId(),
                        command.x(),
                        command.y(),
                        command.z()));
    }
}
