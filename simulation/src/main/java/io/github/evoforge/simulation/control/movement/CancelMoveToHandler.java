package io.github.evoforge.simulation.control.movement;

import io.github.evoforge.simulation.control.core.CommandHandler;
import io.github.evoforge.simulation.world.mechanics.movement.MoveToSystem;

public final class CancelMoveToHandler
        implements CommandHandler<CancelMoveToCommand, CancelMoveToResult> {

    private final MoveToSystem moveTo;

    public CancelMoveToHandler(MoveToSystem moveTo) {
        if (moveTo == null) {
            throw new IllegalArgumentException("moveTo must not be null");
        }
        this.moveTo = moveTo;
    }

    @Override
    public CancelMoveToResult handle(CancelMoveToCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("command must not be null");
        }
        return CancelMoveToResult.from(moveTo.cancel(command.objectId()));
    }
}
